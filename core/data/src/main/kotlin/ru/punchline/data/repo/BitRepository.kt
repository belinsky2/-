package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import ru.punchline.data.db.BitDao
import ru.punchline.data.db.BitPerformanceDao
import ru.punchline.data.db.BitVersionDao
import ru.punchline.data.db.BitVersionEntity
import ru.punchline.data.db.SetListDao
import ru.punchline.model.ActOut
import ru.punchline.model.Attitude
import ru.punchline.model.Bit
import ru.punchline.model.BitElements
import ru.punchline.model.BitHint
import ru.punchline.model.BitLifecycle
import ru.punchline.model.BitStatus
import ru.punchline.model.Clock
import ru.punchline.model.Id
import ru.punchline.model.Punch

/** Шутка вместе с тем, что о ней известно из зала. */
data class BitCard(
    val bit: Bit,
    val isScheduled: Boolean,
    val hints: List<BitHint>,
    val deservedStatus: BitStatus,
) {
    /** Движок предлагает повышение, но не выполняет его молча. */
    val promotionOffered: Boolean
        get() = deservedStatus != bit.status &&
            BitLifecycle.canTransition(bit.status, deservedStatus)
}

class BitRepository(
    private val bits: BitDao,
    private val versions: BitVersionDao,
    private val performances: BitPerformanceDao,
    private val setLists: SetListDao,
    private val sink: MutationSink,
    private val clock: Clock,
    private val json: Json = Json,
) {

    fun observeInbox(): Flow<List<Bit>> =
        bits.observeInbox().map { list -> list.map { it.toDomain() } }

    fun observeByStatus(status: BitStatus): Flow<List<Bit>> =
        bits.observeByStatus(status).map { list -> list.map { it.toDomain() } }

    fun search(query: String): Flow<List<Bit>> =
        bits.search(query).map { list -> list.map { it.toDomain() } }

    /**
     * Доска материала. Подсказки и заслуженный статус считаются здесь, а не в UI:
     * это правила методики, и они не должны разъезжаться между экранами.
     */
    fun observeCards(): Flow<List<BitCard>> = combine(
        bits.observeAlive(),
        performances.observeAll(),
        setLists.observeScheduledBitIds(),
    ) { bitRows, performanceRows, scheduled ->
        val byBit = performanceRows.map { it.toDomain() }.groupBy { it.bitId }
        val scheduledIds = scheduled.toSet()
        val now = clock.nowMillis()
        bitRows.map { row ->
            val bit = row.toDomain()
            val history = byBit[bit.id].orEmpty()
            BitCard(
                bit = bit,
                isScheduled = row.id in scheduledIds,
                hints = BitLifecycle.hints(bit, history, now),
                deservedStatus = BitLifecycle.deservedStatus(bit, history),
            )
        }
    }

    /** Быстрый захват: одна строка текста превращается в зерно будущей шутки. */
    suspend fun capture(text: String, topicId: Id? = null): Id {
        val id = Id.generate(clock)
        bits.upsert(
            Bit(
                id = id,
                topicId = topicId,
                title = text.trim().take(TITLE_LIMIT),
                status = BitStatus.SEED,
                elements = BitElements(),
                meta = sink.stamp().toDomain(),
            ).toEntity()
        )
        return id
    }

    suspend fun byId(id: Id): Bit? = bits.byId(id.value)?.toDomain()

    /**
     * Правка шутки. Снимок предыдущей версии делается не чаще раза в
     * [SNAPSHOT_INTERVAL_MS]: иначе набор текста породит сотню записей в истории.
     */
    suspend fun update(id: Id, note: String? = null, transform: (Bit) -> Bit) {
        val current = bits.byId(id.value)?.toDomain() ?: return
        val updated = transform(current)
        if (updated == current) return

        snapshotIfDue(current, note)
        bits.upsert(updated.copy(meta = sink.stamp().toDomain()).toEntity())
    }

    suspend fun setAttitude(id: Id, attitude: Attitude) =
        update(id) { it.copy(attitude = attitude) }

    suspend fun setPremise(id: Id, premise: String) =
        update(id) { it.copy(elements = it.elements.copy(premise = premise)) }

    suspend fun setPunch(id: Id, punch: Punch) =
        update(id) { it.copy(elements = it.elements.copy(punch = punch)) }

    suspend fun setActOut(id: Id, actOut: ActOut) =
        update(id) { it.copy(elements = it.elements.copy(actOut = actOut)) }

    /**
     * Смена статуса вручную. Недопустимый переход не выполняется молча —
     * жизненный цикл описан в домене и должен соблюдаться и здесь.
     */
    suspend fun changeStatus(id: Id, to: BitStatus): Boolean {
        val current = bits.byId(id.value)?.toDomain() ?: return false
        if (!BitLifecycle.canTransition(current.status, to)) return false
        update(id, note = STATUS_NOTE) { it.copy(status = to) }
        return true
    }

    /** Мягкое удаление: запись остаётся надгробием, иначе воскреснет при слиянии. */
    suspend fun delete(id: Id) {
        val current = bits.byId(id.value) ?: return
        bits.upsert(current.copy(sync = sink.tombstone()))
    }

    private suspend fun snapshotIfDue(bit: Bit, note: String?) {
        val last = versions.lastSnapshotAt(bit.id.value)
        val now = clock.nowMillis()
        if (last != null && now - last < SNAPSHOT_INTERVAL_MS && note == null) return
        versions.insert(
            BitVersionEntity(
                id = Id.generate(clock).value,
                bitId = bit.id.value,
                snapshotJson = json.encodeToString(BitSnapshot.from(bit)),
                note = note,
                createdAt = now,
            )
        )
    }

    private companion object {
        const val TITLE_LIMIT = 120
        const val SNAPSHOT_INTERVAL_MS = 5 * 60 * 1000L
        const val STATUS_NOTE = "status"
    }
}
