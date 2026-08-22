package ru.punchline.data.repo

import kotlinx.serialization.Serializable
import ru.punchline.model.Attitude
import ru.punchline.model.Bit
import ru.punchline.model.BitStatus
import ru.punchline.model.PunchTechnique

/**
 * Снимок шутки для истории версий. Отдельный тип, а не сериализованный [Bit]:
 * доменная модель будет меняться, а старые снимки должны продолжать читаться.
 */
@Serializable
data class BitSnapshot(
    val title: String,
    val status: BitStatus,
    val attitude: Attitude? = null,
    val premise: String? = null,
    val setup: String? = null,
    val punchText: String? = null,
    val punchTechnique: PunchTechnique? = null,
    val actOutText: String? = null,
    val actOutSpaceWork: Boolean = false,
    val tags: List<String> = emptyList(),
) {
    companion object {
        fun from(bit: Bit): BitSnapshot = BitSnapshot(
            title = bit.title,
            status = bit.status,
            attitude = bit.attitude,
            premise = bit.elements.premise,
            setup = bit.elements.setup,
            punchText = bit.elements.punch?.text,
            punchTechnique = bit.elements.punch?.technique,
            actOutText = bit.elements.actOut?.text,
            actOutSpaceWork = bit.elements.actOut?.hasSpaceWork ?: false,
            tags = bit.elements.tags,
        )
    }
}
