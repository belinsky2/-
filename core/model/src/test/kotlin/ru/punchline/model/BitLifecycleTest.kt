package ru.punchline.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitLifecycleTest {

    private val clock = TestClock()
    private val punch = Punch("панч", PunchTechnique.TURN)

    @Test
    fun `forward transitions follow the workbook order`() {
        assertTrue(BitLifecycle.canTransition(BitStatus.SEED, BitStatus.PREMISE))
        assertTrue(BitLifecycle.canTransition(BitStatus.PREMISE, BitStatus.DRAFT))
        assertTrue(BitLifecycle.canTransition(BitStatus.DRAFT, BitStatus.TESTED))
        assertTrue(BitLifecycle.canTransition(BitStatus.TESTED, BitStatus.POLISHED))
    }

    @Test
    fun `stages cannot be skipped`() {
        assertFalse(BitLifecycle.canTransition(BitStatus.SEED, BitStatus.DRAFT))
        assertFalse(BitLifecycle.canTransition(BitStatus.PREMISE, BitStatus.POLISHED))
    }

    @Test
    fun `parking and unparking are always allowed`() {
        BitStatus.entries.filter { it != BitStatus.PARKED }.forEach {
            assertTrue("нельзя отложить из $it", BitLifecycle.canTransition(it, BitStatus.PARKED))
        }
        assertTrue(BitLifecycle.canTransition(BitStatus.PARKED, BitStatus.DRAFT))
    }

    @Test
    fun `a bare idea stays a seed`() {
        val b = bit(clock, status = BitStatus.SEED)
        assertEquals(BitStatus.SEED, BitLifecycle.deservedStatus(b, emptyList()))
    }

    @Test
    fun `premise needs both the text and the attitude`() {
        val withoutAttitude = bit(clock, premise = "самое сложное...")
        assertEquals(
            "без выбранного отношения это ещё не премиса",
            BitStatus.SEED,
            BitLifecycle.deservedStatus(withoutAttitude, emptyList()),
        )

        val complete = bit(clock, premise = "самое сложное...", attitude = Attitude.HARD)
        assertEquals(BitStatus.PREMISE, BitLifecycle.deservedStatus(complete, emptyList()))
    }

    @Test
    fun `premise plus punch makes a draft`() {
        val b = bit(clock, premise = "п", attitude = Attitude.WEIRD, punch = punch)
        assertEquals(BitStatus.DRAFT, BitLifecycle.deservedStatus(b, emptyList()))
    }

    @Test
    fun `one performance makes a draft tested`() {
        val b = bit(clock, premise = "п", attitude = Attitude.HARD, punch = punch)
        val perf = listOf(performance(clock, "bit-1", LaughResult.CHUCKLE))
        assertEquals(BitStatus.TESTED, BitLifecycle.deservedStatus(b, perf))
    }

    @Test
    fun `polishing requires two real laughs, not two appearances`() {
        val b = bit(clock, premise = "п", attitude = Attitude.HARD, punch = punch)

        val weak = listOf(
            performance(clock, "bit-1", LaughResult.CHUCKLE, lamport = 1),
            performance(clock, "bit-1", LaughResult.SILENCE, lamport = 2),
        )
        assertEquals(
            "хмык и тишина не повышают шутку",
            BitStatus.TESTED,
            BitLifecycle.deservedStatus(b, weak),
        )

        val strong = listOf(
            performance(clock, "bit-1", LaughResult.LAUGH, lamport = 1),
            performance(clock, "bit-1", LaughResult.BIG_LAUGH, lamport = 2),
        )
        assertEquals(BitStatus.POLISHED, BitLifecycle.deservedStatus(b, strong))
    }

    @Test
    fun `the engine never drags a bit out of the archive by itself`() {
        val parked = bit(clock, status = BitStatus.PARKED, premise = "п", attitude = Attitude.HARD, punch = punch)
        val strong = listOf(
            performance(clock, "bit-1", LaughResult.BIG_LAUGH, lamport = 1),
            performance(clock, "bit-1", LaughResult.BIG_LAUGH, lamport = 2),
        )
        assertEquals(BitStatus.PARKED, BitLifecycle.deservedStatus(parked, strong))
    }

    @Test
    fun `three silences in a row suggest a rewrite`() {
        val b = bit(clock, premise = "п", attitude = Attitude.HARD, punch = punch)
        val perf = (1..3).map { performance(clock, "bit-1", LaughResult.SILENCE, lamport = it.toLong()) }
        val hints = BitLifecycle.hints(b, perf, clock.nowMillis())
        assertTrue(hints.any { it is BitHint.RewriteOrPark })
    }

    @Test
    fun `an older laugh does not cancel a fresh run of silences`() {
        val b = bit(clock, premise = "п", attitude = Attitude.HARD, punch = punch)
        val perf = listOf(
            performance(clock, "bit-1", LaughResult.BIG_LAUGH, lamport = 1),
            performance(clock, "bit-1", LaughResult.SILENCE, lamport = 2),
            performance(clock, "bit-1", LaughResult.SILENCE, lamport = 3),
            performance(clock, "bit-1", LaughResult.SILENCE, lamport = 4),
        )
        val hints = BitLifecycle.hints(b, perf, clock.nowMillis())
        assertTrue("важна свежая серия провалов, а не история целиком",
            hints.any { it is BitHint.RewriteOrPark })
    }

    @Test
    fun `a tested bit without an act-out is flagged`() {
        val b = bit(clock, status = BitStatus.TESTED, premise = "п", attitude = Attitude.HARD, punch = punch)
        assertTrue(BitLifecycle.hints(b, emptyList(), clock.nowMillis()).contains(BitHint.MissingActOut))

        val played = b.copy(elements = b.elements.copy(actOut = ActOut("играю тёщу")))
        assertFalse(BitLifecycle.hints(played, emptyList(), clock.nowMillis()).contains(BitHint.MissingActOut))
    }

    @Test
    fun `a draft gathering dust surfaces after a month`() {
        val b = bit(clock, status = BitStatus.DRAFT, premise = "п", attitude = Attitude.HARD, punch = punch)
        assertTrue(BitLifecycle.hints(b, emptyList(), clock.nowMillis()).none { it is BitHint.StuckInDraft })

        clock.advanceDays(31)
        assertTrue(BitLifecycle.hints(b, emptyList(), clock.nowMillis()).any { it is BitHint.StuckInDraft })
    }

    @Test
    fun `polished material unused for a quarter is offered back`() {
        val b = bit(clock, status = BitStatus.POLISHED, premise = "п", attitude = Attitude.HARD, punch = punch)
        clock.advanceDays(91)
        assertTrue(BitLifecycle.hints(b, emptyList(), clock.nowMillis()).any { it is BitHint.UnusedPolished })
    }
}
