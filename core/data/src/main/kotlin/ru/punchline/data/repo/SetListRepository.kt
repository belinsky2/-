package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import ru.punchline.data.db.BitDao
import ru.punchline.data.db.SetListDao
import ru.punchline.data.db.SetListEntity
import ru.punchline.data.db.SetListItemEntity
import ru.punchline.model.Clock
import ru.punchline.model.Id
import ru.punchline.model.SetList
import ru.punchline.model.SetListIssue
import ru.punchline.model.SetListRole
import ru.punchline.model.SetListRules

/** Сет-лист вместе с замечаниями движка. */
data class SetListCard(val setList: SetList, val issues: List<SetListIssue>)

class SetListRepository(
    private val setLists: SetListDao,
    private val bits: BitDao,
    private val sink: MutationSink,
    private val clock: Clock,
) {

    fun observeAll(): Flow<List<SetList>> =
        setLists.observeAlive().map { rows -> rows.map { it.toDomain(emptyList()) } }

    /**
     * Сет со всеми проверками: каллбэк раньше исходной шутки, две подряд шутки
     * на одну тему, слабое закрытие, перебор по времени. Ни одна из них не
     * блокирующая — на сцену выходит автор, а не движок.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeCard(id: Id, averageScores: Flow<Map<Id, Double>>): Flow<SetListCard?> =
        setLists.observeItems(id.value).flatMapLatest { items ->
            combine(bits.observeAlive(), averageScores) { bitRows, scores ->
                val header = setLists.byId(id.value) ?: return@combine null
                val setList = header.toDomain(items)
                val byId = bitRows.associate { Id(it.id) to it.toDomain() }
                SetListCard(setList, SetListRules.validate(setList, byId, scores))
            }
        }

    /** Какие шутки стоят в этом сете и в каком порядке. Нужно разбору выступления. */
    fun observeItemBitIds(setListId: Id): Flow<List<Id>> =
        setLists.observeItems(setListId.value).map { items ->
            items.sortedBy { it.order }.map { Id(it.bitId) }
        }

    suspend fun create(title: String, targetDurationSec: Int): Id {
        val id = Id.generate(clock)
        setLists.upsert(
            SetListEntity(id.value, title, targetDurationSec, sink.stamp())
        )
        return id
    }

    suspend fun addBit(setListId: Id, bitId: Id, role: SetListRole = SetListRole.BODY) {
        val existing = setLists.items(setListId.value)
        setLists.upsertItem(
            SetListItemEntity(
                id = Id.generate(clock).value,
                setListId = setListId.value,
                bitId = bitId.value,
                order = existing.size,
                role = role,
                plannedDurationSec = bits.byId(bitId.value)?.durationSec,
                sync = sink.stamp(),
            )
        )
    }

    /**
     * Новый порядок номеров. Позиции переписываются целиком: частичное
     * обновление после перетаскивания оставляет дыры и дубли в нумерации.
     */
    suspend fun reorder(setListId: Id, orderedItemIds: List<Id>) {
        val current = setLists.items(setListId.value).associateBy { it.id }
        val renumbered = orderedItemIds.mapIndexedNotNull { index, itemId ->
            current[itemId.value]?.copy(order = index, sync = sink.stamp())
        }
        setLists.upsertItems(renumbered)
    }

    suspend fun setRole(itemId: Id, setListId: Id, role: SetListRole) {
        val item = setLists.items(setListId.value).firstOrNull { it.id == itemId.value } ?: return
        setLists.upsertItem(item.copy(role = role, sync = sink.stamp()))
    }

    suspend fun removeItem(itemId: Id, setListId: Id) {
        val item = setLists.items(setListId.value).firstOrNull { it.id == itemId.value } ?: return
        setLists.upsertItem(item.copy(sync = sink.tombstone()))
        // Оставшиеся позиции перенумеровываются, иначе в порядке появится дыра.
        val rest = setLists.items(setListId.value).filter { it.id != itemId.value }
        setLists.upsertItems(rest.mapIndexed { index, row -> row.copy(order = index, sync = sink.stamp()) })
    }

    suspend fun delete(id: Id) {
        val current = setLists.byId(id.value) ?: return
        setLists.upsert(current.copy(sync = sink.tombstone()))
    }
}
