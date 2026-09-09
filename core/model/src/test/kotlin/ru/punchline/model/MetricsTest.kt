package ru.punchline.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsTest {

    private val clock = TestClock()
    private val punch = Punch("п", PunchTechnique.MIX)

    @Test
    fun `laughs per minute counts real laughs over actual stage time`() {
        val perf = listOf(
            performance(clock, "a", LaughResult.BIG_LAUGH, lamport = 1),
            performance(clock, "b", LaughResult.LAUGH, lamport = 2),
            performance(clock, "c", LaughResult.CHUCKLE, lamport = 3),
            performance(clock, "d", LaughResult.SILENCE, lamport = 4),
        )
        val stats = Metrics.gigStats(perf, actualDurationSec = 120)
        assertEquals(4, stats.bitCount)
        assertEquals("хмык и тишина не считаются смехом", 1.0, stats.laughsPerMinute, 0.001)
        assertEquals((3 + 2 + 1 + 0) / 4.0, stats.averageScore, 0.001)
    }

    @Test
    fun `an unmeasured gig does not divide by zero`() {
        val perf = listOf(performance(clock, "a", LaughResult.LAUGH))
        assertEquals(0.0, Metrics.gigStats(perf, actualDurationSec = null).laughsPerMinute, 0.001)
        assertEquals(0.0, Metrics.gigStats(perf, actualDurationSec = 0).laughsPerMinute, 0.001)
        assertEquals(0.0, Metrics.gigStats(emptyList(), 300).averageScore, 0.001)
    }

    @Test
    fun `deleted bits are excluded from every metric`() {
        val alive = bit(clock, id = "a", status = BitStatus.POLISHED, attitude = Attitude.HARD, durationSec = 60)
        val dead = bit(clock, id = "b", status = BitStatus.POLISHED, attitude = Attitude.WEIRD, durationSec = 600)
            .let { it.copy(meta = it.meta.copy(deletedAt = clock.nowMillis())) }
        val bits = listOf(alive, dead)

        assertEquals(1, Metrics.funnel(bits).count(BitStatus.POLISHED))
        assertEquals(1.0, Metrics.polishedMinutes(bits), 0.001)
        assertEquals(mapOf(Attitude.HARD to 1), Metrics.attitudeSpread(bits))
    }

    @Test
    fun `act-out ratio looks only at material that reached the stage`() {
        val bits = listOf(
            bit(clock, id = "a", status = BitStatus.TESTED, punch = punch, actOut = ActOut("играю")),
            bit(clock, id = "b", status = BitStatus.POLISHED, punch = punch),
            // Черновик ещё не выносился на сцену и не должен портить статистику.
            bit(clock, id = "c", status = BitStatus.DRAFT, punch = punch),
        )
        assertEquals(0.5, Metrics.actOutRatio(bits), 0.001)
        assertEquals(0.0, Metrics.actOutRatio(emptyList()), 0.001)
    }

    @Test
    fun `average score per bit feeds the set list rules`() {
        val perf = listOf(
            performance(clock, "a", LaughResult.BIG_LAUGH, lamport = 1),
            performance(clock, "a", LaughResult.LAUGH, lamport = 2),
            performance(clock, "b", LaughResult.SILENCE, lamport = 3),
        )
        val scores = Metrics.averageScoreByBit(perf)
        assertEquals(2.5, scores.getValue(Id("a")), 0.001)
        assertEquals(0.0, scores.getValue(Id("b")), 0.001)
    }

    @Test
    fun `funnel shows where material piles up`() {
        val bits = listOf(
            bit(clock, id = "a", status = BitStatus.SEED),
            bit(clock, id = "b", status = BitStatus.DRAFT),
            bit(clock, id = "c", status = BitStatus.DRAFT),
            bit(clock, id = "d", status = BitStatus.POLISHED),
        )
        val funnel = Metrics.funnel(bits)
        assertEquals(2, funnel.count(BitStatus.DRAFT))
        assertEquals(0, funnel.count(BitStatus.RETIRED))
        assertTrue(funnel.counts.isNotEmpty())
    }
}
