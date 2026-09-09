package ru.punchline.model

/** Замечание к сет-листу. Блокирующих среди них нет — сцена важнее правил. */
sealed interface SetListIssue {
    /** Каллбэк стоит раньше шутки, на которую ссылается: зал не поймёт отсылку. */
    data class CallbackBeforeSource(val itemId: Id, val sourceBitId: Id) : SetListIssue

    /** Две подряд шутки на одну тему — сет звучит однообразно. */
    data class SameTopicInARow(val firstItemId: Id, val secondItemId: Id, val topicId: Id) : SetListIssue

    /** Закрывать надо лучшим, что есть. */
    data class WeakCloser(val itemId: Id, val betterBitId: Id) : SetListIssue

    /** Не уложиться в отведённое время — самый частый способ испортить выступление. */
    data class OverTime(val plannedSec: Int, val targetSec: Int) : SetListIssue

    /** Сет заметно короче заявленного. */
    data class UnderTime(val plannedSec: Int, val targetSec: Int) : SetListIssue
}

object SetListRules {

    /** Насколько сет может отклониться от цели, прежде чем это стоит упоминания. */
    const val TOLERANCE_RATIO: Double = 0.1

    fun validate(
        setList: SetList,
        bitsById: Map<Id, Bit>,
        averageScoreByBit: Map<Id, Double> = emptyMap(),
    ): List<SetListIssue> {
        val issues = mutableListOf<SetListIssue>()
        val ordered = setList.items.sortedBy { it.order }

        checkCallbacks(ordered, bitsById, issues)
        checkTopicRuns(ordered, bitsById, issues)
        checkCloser(ordered, averageScoreByBit, issues)
        checkDuration(setList, ordered, bitsById, issues)

        return issues
    }

    private fun checkCallbacks(
        ordered: List<SetListItem>,
        bitsById: Map<Id, Bit>,
        issues: MutableList<SetListIssue>,
    ) {
        val positionOfBit = ordered.withIndex().associate { (i, item) -> item.bitId to i }
        ordered.forEachIndexed { index, item ->
            val source = bitsById[item.bitId]?.elements?.callbackTo ?: return@forEachIndexed
            val sourcePosition = positionOfBit[source]
            if (sourcePosition == null || sourcePosition >= index) {
                issues += SetListIssue.CallbackBeforeSource(item.id, source)
            }
        }
    }

    private fun checkTopicRuns(
        ordered: List<SetListItem>,
        bitsById: Map<Id, Bit>,
        issues: MutableList<SetListIssue>,
    ) {
        ordered.zipWithNext { a, b ->
            val topicA = bitsById[a.bitId]?.topicId
            val topicB = bitsById[b.bitId]?.topicId
            if (topicA != null && topicA == topicB) {
                issues += SetListIssue.SameTopicInARow(a.id, b.id, topicA)
            }
        }
    }

    private fun checkCloser(
        ordered: List<SetListItem>,
        averageScoreByBit: Map<Id, Double>,
        issues: MutableList<SetListIssue>,
    ) {
        if (averageScoreByBit.isEmpty()) return
        val closer = ordered.lastOrNull { it.role == SetListRole.CLOSER } ?: ordered.lastOrNull() ?: return
        val closerScore = averageScoreByBit[closer.bitId] ?: return
        val best = ordered
            .mapNotNull { item -> averageScoreByBit[item.bitId]?.let { item to it } }
            .maxByOrNull { it.second } ?: return
        if (best.second > closerScore) {
            issues += SetListIssue.WeakCloser(closer.id, best.first.bitId)
        }
    }

    private fun checkDuration(
        setList: SetList,
        ordered: List<SetListItem>,
        bitsById: Map<Id, Bit>,
        issues: MutableList<SetListIssue>,
    ) {
        if (ordered.isEmpty()) return
        val planned = ordered.sumOf { item ->
            item.plannedDurationSec ?: bitsById[item.bitId]?.durationSec ?: 0
        }
        val tolerance = (setList.targetDurationSec * TOLERANCE_RATIO).toInt()
        when {
            planned > setList.targetDurationSec + tolerance ->
                issues += SetListIssue.OverTime(planned, setList.targetDurationSec)
            planned < setList.targetDurationSec - tolerance ->
                issues += SetListIssue.UnderTime(planned, setList.targetDurationSec)
        }
    }
}
