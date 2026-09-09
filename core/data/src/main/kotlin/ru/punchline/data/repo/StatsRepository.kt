package ru.punchline.data.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.punchline.data.db.BitDao
import ru.punchline.data.db.BitPerformanceDao
import ru.punchline.data.db.GigDao
import ru.punchline.model.Clock
import ru.punchline.model.Progress
import ru.punchline.model.Metrics

class StatsRepository(
    private val bits: BitDao,
    private val performances: BitPerformanceDao,
    private val gigs: GigDao,
    private val streaks: StreakRepository,
    private val clock: Clock,
) {
    // Воронка, минуты и перекос по отношениям считаются только из шуток:
    // результаты зала попадают в них уже через изменившийся статус.
    fun observeProgress(goalMinutes: Int): Flow<Progress> = combine(
        bits.observeAlive(),
        gigs.observeCountSince(clock.nowMillis() - THIRTY_DAYS_MS),
        streaks.observeCurrentLength(),
    ) { bitRows, recentGigs, streak ->
        val domain = bitRows.map { it.toDomain() }
        Progress(
            funnel = Metrics.funnel(domain),
            polishedMinutes = Metrics.polishedMinutes(domain),
            goalMinutes = goalMinutes,
            actOutRatio = Metrics.actOutRatio(domain),
            attitudeSpread = Metrics.attitudeSpread(domain),
            gigsLast30Days = recentGigs,
            streakDays = streak,
        )
    }

    /** Топ и антитоп по среднему результату — что тащит акт, а что его топит. */
    fun observeExtremes(limit: Int = 5): Flow<Pair<List<RankedBit>, List<RankedBit>>> = combine(
        bits.observeAlive(),
        performances.observeAll(),
    ) { bitRows, performanceRows ->
        val scores = Metrics.averageScoreByBit(performanceRows.map { it.toDomain() })
        val ranked = bitRows
            .mapNotNull { row -> scores[ru.punchline.model.Id(row.id)]?.let { RankedBit(row.title, it) } }
            .sortedByDescending { it.score }
        ranked.take(limit) to ranked.takeLast(limit).reversed()
    }

    private companion object { const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000 }
}

data class RankedBit(val title: String, val score: Double)
