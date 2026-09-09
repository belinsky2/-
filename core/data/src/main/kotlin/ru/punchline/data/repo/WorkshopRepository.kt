package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.punchline.data.db.BitDao
import ru.punchline.data.db.MindMapDao
import ru.punchline.data.db.MindMapNodeEntity
import ru.punchline.data.db.RantDao
import ru.punchline.data.db.RantEntity
import ru.punchline.model.Bit
import ru.punchline.model.BitElements
import ru.punchline.model.BitStatus
import ru.punchline.model.Clock
import ru.punchline.model.Id

/** Узел дерева ассоциаций с уже посчитанной глубиной — UI не должен её выводить сам. */
data class MindMapNode(
    val id: Id,
    val parentId: Id?,
    val text: String,
    val depth: Int,
    val promotedBitId: Id?,
)

/**
 * Мастерская: путь от темы к черновику шутки. Part Two у Картер —
 * ассоциации, рант вслух, отношение, премиса, панч, act-out.
 */
class WorkshopRepository(
    private val mindMap: MindMapDao,
    private val rants: RantDao,
    private val bits: BitDao,
    private val sink: MutationSink,
    private val clock: Clock,
) {

    fun observeMindMap(topicId: Id): Flow<List<MindMapNode>> =
        mindMap.observeForTopic(topicId.value).map { rows -> rows.toTree() }

    suspend fun addNode(topicId: Id, parentId: Id?, text: String): Id {
        val id = Id.generate(clock)
        val siblings = mindMap.forTopic(topicId.value).count { it.parentId == parentId?.value }
        mindMap.upsert(
            MindMapNodeEntity(
                id = id.value,
                topicId = topicId.value,
                parentId = parentId?.value,
                text = text.trim(),
                order = siblings,
                promotedBitId = null,
                sync = sink.stamp(),
            )
        )
        return id
    }

    /**
     * Превратить узел в шутку. Связь сохраняется в обе стороны: узел помечается
     * использованным, чтобы мастерская не предлагала его снова, а шутка помнит,
     * из какой ассоциации выросла.
     */
    suspend fun promoteNode(nodeId: Id): Id? {
        val node = mindMap.byId(nodeId.value) ?: return null
        node.promotedBitId?.let { return Id(it) }

        val bitId = Id.generate(clock)
        bits.upsert(
            Bit(
                id = bitId,
                topicId = Id(node.topicId),
                title = node.text,
                status = BitStatus.SEED,
                elements = BitElements(),
                meta = sink.stamp().toDomain(),
            ).toEntity()
        )
        mindMap.upsert(node.copy(promotedBitId = bitId.value, sync = sink.stamp()))
        return bitId
    }

    suspend fun deleteNode(nodeId: Id) {
        val node = mindMap.byId(nodeId.value) ?: return
        mindMap.upsert(node.copy(sync = sink.tombstone()))
    }

    /**
     * Сохранить рант. Аудио обязательно, транскрипт — нет: распознавание
     * длинной непрерывной речи ненадёжно, и запись не должна от него зависеть.
     */
    suspend fun saveRant(topicId: Id?, audioHash: String, durationSec: Int, transcript: String?): Id {
        val id = Id.generate(clock)
        rants.upsert(
            RantEntity(
                id = id.value,
                topicId = topicId?.value,
                audioHash = audioHash,
                transcript = transcript,
                durationSec = durationSec,
                sync = sink.stamp(),
            )
        )
        return id
    }

    /** Выделенный кусок ранта становится зерном будущей шутки. */
    suspend fun harvest(topicId: Id?, text: String): Id {
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

    private companion object { const val TITLE_LIMIT = 120 }
}

/**
 * Плоский список из базы в дерево с глубинами. Порядок обхода — как в аутлайне:
 * родитель, затем всё его поддерево.
 */
private fun List<MindMapNodeEntity>.toTree(): List<MindMapNode> {
    val byParent = groupBy { it.parentId }
    val result = mutableListOf<MindMapNode>()

    fun walk(parentId: String?, depth: Int) {
        byParent[parentId].orEmpty().sortedBy { it.order }.forEach { row ->
            result += MindMapNode(
                id = Id(row.id),
                parentId = row.parentId?.let(::Id),
                text = row.text,
                depth = depth,
                promotedBitId = row.promotedBitId?.let(::Id),
            )
            walk(row.id, depth + 1)
        }
    }

    walk(null, 0)
    return result
}
