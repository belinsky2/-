package ru.punchline.model

import org.junit.Assert.assertTrue
import org.junit.Test

class SetListRulesTest {

    private val clock = TestClock()

    private fun setList(vararg items: SetListItem, targetSec: Int = 300) = SetList(
        id = Id("set-1"),
        title = "Пятиминутка",
        targetDurationSec = targetSec,
        items = items.toList(),
        meta = meta(clock),
    )

    private fun item(
        id: String,
        bitId: String,
        order: Int,
        role: SetListRole = SetListRole.BODY,
        durationSec: Int? = 60,
    ) = SetListItem(Id(id), Id(bitId), order, role, durationSec)

    @Test
    fun `a callback placed before its source is reported`() {
        val bits = mapOf(
            Id("a") to bit(clock, id = "a", topicId = "t1"),
            Id("b") to bit(clock, id = "b", topicId = "t2", callbackTo = "a"),
        )
        val broken = setList(
            item("i1", "b", order = 0, role = SetListRole.CALLBACK),
            item("i2", "a", order = 1),
            targetSec = 120,
        )
        assertTrue(SetListRules.validate(broken, bits).any { it is SetListIssue.CallbackBeforeSource })

        val fixed = setList(
            item("i1", "a", order = 0),
            item("i2", "b", order = 1, role = SetListRole.CALLBACK),
            targetSec = 120,
        )
        assertTrue(SetListRules.validate(fixed, bits).none { it is SetListIssue.CallbackBeforeSource })
    }

    @Test
    fun `a callback whose source is absent from the set is reported`() {
        val bits = mapOf(Id("b") to bit(clock, id = "b", topicId = "t2", callbackTo = "missing"))
        val list = setList(item("i1", "b", order = 0, role = SetListRole.CALLBACK), targetSec = 60)
        assertTrue(SetListRules.validate(list, bits).any { it is SetListIssue.CallbackBeforeSource })
    }

    @Test
    fun `two neighbours on one topic are flagged, spaced ones are not`() {
        val bits = mapOf(
            Id("a") to bit(clock, id = "a", topicId = "t1"),
            Id("b") to bit(clock, id = "b", topicId = "t1"),
            Id("c") to bit(clock, id = "c", topicId = "t2"),
        )
        val clumped = setList(
            item("i1", "a", 0), item("i2", "b", 1), item("i3", "c", 2), targetSec = 180,
        )
        assertTrue(SetListRules.validate(clumped, bits).any { it is SetListIssue.SameTopicInARow })

        val spaced = setList(
            item("i1", "a", 0), item("i2", "c", 1), item("i3", "b", 2), targetSec = 180,
        )
        assertTrue(SetListRules.validate(spaced, bits).none { it is SetListIssue.SameTopicInARow })
    }

    @Test
    fun `closing with a weaker bit than the set's best is flagged`() {
        val bits = mapOf(
            Id("a") to bit(clock, id = "a", topicId = "t1"),
            Id("b") to bit(clock, id = "b", topicId = "t2"),
        )
        val scores = mapOf(Id("a") to 3.5, Id("b") to 1.0)
        val list = setList(
            item("i1", "a", 0),
            item("i2", "b", 1, role = SetListRole.CLOSER),
            targetSec = 120,
        )
        assertTrue(SetListRules.validate(list, bits, scores).any { it is SetListIssue.WeakCloser })
    }

    @Test
    fun `closing with the best bit passes`() {
        val bits = mapOf(
            Id("a") to bit(clock, id = "a", topicId = "t1"),
            Id("b") to bit(clock, id = "b", topicId = "t2"),
        )
        val scores = mapOf(Id("a") to 1.0, Id("b") to 3.5)
        val list = setList(
            item("i1", "a", 0),
            item("i2", "b", 1, role = SetListRole.CLOSER),
            targetSec = 120,
        )
        assertTrue(SetListRules.validate(list, bits, scores).none { it is SetListIssue.WeakCloser })
    }

    @Test
    fun `timing is judged with tolerance, not exactly`() {
        val bits = mapOf(Id("a") to bit(clock, id = "a", topicId = "t1"))

        val onTime = setList(item("i1", "a", 0, durationSec = 295), targetSec = 300)
        assertTrue(
            "отклонение в пределах допуска не должно считаться проблемой",
            SetListRules.validate(onTime, bits).none {
                it is SetListIssue.OverTime || it is SetListIssue.UnderTime
            },
        )

        val tooLong = setList(item("i1", "a", 0, durationSec = 400), targetSec = 300)
        assertTrue(SetListRules.validate(tooLong, bits).any { it is SetListIssue.OverTime })

        val tooShort = setList(item("i1", "a", 0, durationSec = 100), targetSec = 300)
        assertTrue(SetListRules.validate(tooShort, bits).any { it is SetListIssue.UnderTime })
    }

    @Test
    fun `an empty set list produces no timing complaints`() {
        assertTrue(SetListRules.validate(setList(), emptyMap()).isEmpty())
    }

    @Test
    fun `items are validated in their stated order, not their list order`() {
        val bits = mapOf(
            Id("a") to bit(clock, id = "a", topicId = "t1"),
            Id("b") to bit(clock, id = "b", topicId = "t2", callbackTo = "a"),
        )
        // Каллбэк идёт первым в списке, но вторым по порядку — это корректный сет.
        val list = setList(
            item("i2", "b", order = 1, role = SetListRole.CALLBACK),
            item("i1", "a", order = 0),
            targetSec = 120,
        )
        assertTrue(SetListRules.validate(list, bits).none { it is SetListIssue.CallbackBeforeSource })
    }
}
