package ru.punchline.data.repo

import ru.punchline.model.Bit
import ru.punchline.model.BitStatus

/**
 * Подписи для выгрузки. Приходят снаружи из строковых ресурсов: правило
 * «никакого видимого текста в коде» действует и здесь, иначе второй язык
 * снова пришлось бы вычёсывать по всему проекту.
 */
data class MarkdownLabels(
    val documentTitle: String,
    val myAct: String,
    val inProgress: String,
    val archive: String,
    val premise: String,
    val punch: String,
    val actOut: String,
    val tags: String,
)

/**
 * Выгрузка в Markdown — чтобы материал можно было прочитать глазами где угодно.
 * Это не бэкап: бэкап разворачивается обратно, Markdown только читается.
 * Структура повторяет секции бумажной тетради.
 */
object MarkdownExport {

    fun render(bitsByTopic: Map<String, List<Bit>>, labels: MarkdownLabels): String = buildString {
        appendLine("# ${labels.documentTitle}")
        appendLine()

        section(this, labels.myAct, labels, bitsByTopic) { it.status == BitStatus.POLISHED }
        section(this, labels.inProgress, labels, bitsByTopic) { it.status in IN_PROGRESS }
        section(this, labels.archive, labels, bitsByTopic) { it.status in ARCHIVED }
    }

    private fun section(
        out: StringBuilder,
        title: String,
        labels: MarkdownLabels,
        bitsByTopic: Map<String, List<Bit>>,
        predicate: (Bit) -> Boolean,
    ) {
        val filtered = bitsByTopic
            .mapValues { (_, bits) -> bits.filter(predicate) }
            .filterValues { it.isNotEmpty() }
        if (filtered.isEmpty()) return

        out.appendLine("## $title")
        out.appendLine()
        filtered.forEach { (topic, bits) ->
            out.appendLine("### $topic")
            out.appendLine()
            bits.forEach { bit ->
                out.appendLine("**${bit.title}**")
                bit.elements.premise?.takeIf(String::isNotBlank)
                    ?.let { out.appendLine("- ${labels.premise}: $it") }
                bit.elements.punch?.text?.takeIf(String::isNotBlank)
                    ?.let { out.appendLine("- ${labels.punch}: $it") }
                bit.elements.actOut?.text?.takeIf(String::isNotBlank)
                    ?.let { out.appendLine("- ${labels.actOut}: $it") }
                bit.elements.tags.takeIf { it.isNotEmpty() }
                    ?.let { out.appendLine("- ${labels.tags}: " + it.joinToString(", ")) }
                out.appendLine()
            }
        }
    }

    private val IN_PROGRESS = setOf(
        BitStatus.SEED, BitStatus.PREMISE, BitStatus.DRAFT, BitStatus.TESTED,
    )
    private val ARCHIVED = setOf(BitStatus.PARKED, BitStatus.RETIRED)
}
