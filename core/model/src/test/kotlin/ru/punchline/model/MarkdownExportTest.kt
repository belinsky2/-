package ru.punchline.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExportTest {

    private val clock = TestClock()

    private val labels = MarkdownLabels(
        documentTitle = "Выгрузка",
        myAct = "Мой акт",
        inProgress = "В работе",
        archive = "Архив",
        premise = "Премиса",
        punch = "Панч",
        actOut = "Act-out",
        tags = "Добивки",
    )

    @Test
    fun `sections appear only when they have something in them`() {
        val onlyDrafts = mapOf(
            "Метро" to listOf(bit(clock, id = "a", status = BitStatus.DRAFT)),
        )
        val text = MarkdownExport.render(onlyDrafts, labels)

        assertTrue(text.contains("## В работе"))
        assertFalse("пустые секции не печатаются", text.contains("## Мой акт"))
        assertFalse(text.contains("## Архив"))
    }

    @Test
    fun `a bit renders every field it actually has`() {
        val full = bit(
            clock,
            id = "Шутка про метро",
            status = BitStatus.POLISHED,
            premise = "самое сложное — это утро",
            punch = Punch("панчлайн", PunchTechnique.TURN),
            actOut = ActOut("играю турникет"),
        )
        val text = MarkdownExport.render(mapOf("Метро" to listOf(full)), labels)

        assertTrue(text.contains("### Метро"))
        assertTrue(text.contains("**Шутка про метро**"))
        assertTrue(text.contains("- Премиса: самое сложное — это утро"))
        assertTrue(text.contains("- Панч: панчлайн"))
        assertTrue(text.contains("- Act-out: играю турникет"))
    }

    @Test
    fun `missing fields do not leave dangling labels`() {
        val bare = bit(clock, id = "Голая идея", status = BitStatus.SEED)
        val text = MarkdownExport.render(mapOf("Разное" to listOf(bare)), labels)

        assertTrue(text.contains("**Голая идея**"))
        assertFalse(text.contains("- Премиса:"))
        assertFalse(text.contains("- Панч:"))
        assertFalse(text.contains("- Добивки:"))
    }

    @Test
    fun `archived material is separated from the working act`() {
        val bits = mapOf(
            "Семья" to listOf(
                bit(clock, id = "живая", status = BitStatus.POLISHED),
                bit(clock, id = "списанная", status = BitStatus.RETIRED),
            )
        )
        val text = MarkdownExport.render(bits, labels)
        val actIndex = text.indexOf("## Мой акт")
        val archiveIndex = text.indexOf("## Архив")

        assertTrue(actIndex >= 0 && archiveIndex > actIndex)
        assertTrue(text.substring(actIndex, archiveIndex).contains("живая"))
        assertTrue(text.substring(archiveIndex).contains("списанная"))
    }

    @Test
    fun `an empty vault still produces a readable document`() {
        val text = MarkdownExport.render(emptyMap(), labels)
        assertTrue(text.startsWith("# Выгрузка"))
    }
}
