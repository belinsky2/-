package ru.punchline.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressTest {

    private fun progress(
        counts: Map<BitStatus, Int> = emptyMap(),
        polished: Double = 0.0,
        goal: Int = 5,
    ) = Progress(
        funnel = Funnel(counts),
        polishedMinutes = polished,
        goalMinutes = goal,
        actOutRatio = 0.0,
        attitudeSpread = emptyMap(),
        gigsLast30Days = 0,
        streakDays = 0,
    )

    @Test
    fun `bottleneck points at the fullest stage before the act`() {
        val p = progress(
            mapOf(
                BitStatus.SEED to 2,
                BitStatus.DRAFT to 7,
                BitStatus.TESTED to 1,
                BitStatus.POLISHED to 40,
            )
        )
        assertEquals(
            "готовый акт не затык, даже если его больше всего",
            BitStatus.DRAFT,
            p.bottleneck,
        )
    }

    @Test
    fun `no material means no bottleneck to report`() {
        assertNull(progress().bottleneck)
        assertNull(progress(mapOf(BitStatus.POLISHED to 5)).bottleneck)
    }

    @Test
    fun `goal ratio never exceeds one`() {
        assertEquals(1.0, progress(polished = 30.0, goal = 5).goalRatio, 0.001)
        assertEquals(0.5, progress(polished = 5.0, goal = 10).goalRatio, 0.001)
    }

    @Test
    fun `an unset goal does not divide by zero`() {
        assertEquals(0.0, progress(polished = 12.0, goal = 0).goalRatio, 0.001)
    }
}
