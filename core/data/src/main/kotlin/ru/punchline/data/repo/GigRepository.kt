package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.punchline.data.db.BitDao
import ru.punchline.data.db.BitPerformanceDao
import ru.punchline.data.db.BitPerformanceEntity
import ru.punchline.data.db.GigDao
import ru.punchline.data.db.GigEntity
import ru.punchline.model.BitLifecycle
import ru.punchline.model.BitStatus
import ru.punchline.model.Clock
import ru.punchline.model.Gig
import ru.punchline.model.GigStats
import ru.punchline.model.GigType
import ru.punchline.model.Id
import ru.punchline.model.LaughResult
import ru.punchline.model.Metrics

/**
 * Выступления и то, ради чего они существуют в приложении: превратить реакцию
 * зала в данные. Упражнение 35 у Картер — считать laugh score, а не полагаться
 * на ощущение, потому что ощущение врёт в обе стороны.
 */
class GigRepository(
    private val gigs: GigDao,
    private val performances: BitPerformanceDao,
    private val bits: BitDao,
    private val sink: MutationSink,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<Gig>> =
        gigs.observeAlive().map { list -> list.map { it.toDomain() } }

    fun observeRecentCount(days: Int): Flow<Int> =
        gigs.observeCountSince(clock.nowMillis() - days * MILLIS_PER_DAY)

    suspend fun start(type: GigType, venue: String, setListId: Id?): Id {
        val id = Id.generate(clock)
        gigs.upsert(
            GigEntity(
                id = id.value,
                setListId = setListId?.value,
                type = type,
                venue = venue,
                dateMillis = clock.nowMillis(),
                actualDurationSec = null,
                audioHash = null,
                notes = null,
                sync = sink.stamp(),
            )
        )
        return id
    }

    suspend fun finish(id: Id, actualDurationSec: Int, audioHash: String?) {
        val current = gigs.byId(id.value) ?: return
        gigs.upsert(
            current.copy(
                actualDurationSec = actualDurationSec,
                audioHash = audioHash ?: current.audioHash,
                sync = sink.stamp(),
            )
        )
    }

    /**
     * Отметка реакции зала на одну шутку. Разбирая выступление, автор проходит
     * по списку в один тап на шутку — все отметки попадают в одну миллисекунду,
     * и порядок восстанавливается логическими часами из [MutationSink].
     */
    suspend fun mark(gigId: Id, bitId: Id, result: LaughResult, note: String? = null) {
        performances.upsert(
            BitPerformanceEntity(
                id = Id.generate(clock).value,
                gigId = gigId.value,
                bitId = bitId.value,
                result = result,
                note = note,
                sync = sink.stamp(),
            )
        )
    }

    fun observeStats(gigId: Id): Flow<GigStats> = combine(
        performances.observeForGig(gigId.value),
        gigs.observeAlive(),
    ) { rows, allGigs ->
        val duration = allGigs.firstOrNull { it.id == gigId.value }?.actualDurationSec
        Metrics.gigStats(rows.map { it.toDomain() }, duration)
    }

    /** Средний результат каждой шутки — топливо для правил сет-листа. */
    fun observeAverageScores(): Flow<Map<Id, Double>> =
        performances.observeAll().map { rows -> Metrics.averageScoreByBit(rows.map { it.toDomain() }) }

    /**
     * Применить повышения, которые материал заслужил после выступления.
     * Возвращает список изменившихся шуток: разбор выступления должен
     * заканчиваться внятным «вот что поменялось», а не молча перестроенным экраном.
     */
    suspend fun applyDeservedPromotions(): List<Promotion> {
        val applied = mutableListOf<Promotion>()
        for (row in bits.alive()) {
            val bit = row.toDomain()
            val history = performances.forBit(row.id).map { it.toDomain() }
            if (history.isEmpty()) continue

            val deserved = BitLifecycle.deservedStatus(bit, history)
            if (deserved == bit.status) continue
            if (!BitLifecycle.canTransition(bit.status, deserved)) continue

            bits.upsert(bit.copy(status = deserved, meta = sink.stamp().toDomain()).toEntity())
            applied += Promotion(bit.id, bit.title, bit.status, deserved)
        }
        return applied
    }

    private companion object { const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000 }
}

/** Что именно движок изменил после разбора выступления. */
data class Promotion(
    val bitId: Id,
    val title: String,
    val from: BitStatus,
    val to: BitStatus,
)
