package ru.punchline.app.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.Progress
import ru.punchline.data.repo.RankedBit
import ru.punchline.data.repo.StatsRepository
import ru.punchline.model.Funnel

/**
 * Сегодня: три числа, которые отвечают на вопрос «в форме ли я» —
 * цепочка, минуты готового материала и выступления за месяц.
 */
class TodayViewModel(
    stats: StatsRepository,
    bits: BitRepository,
    goalMinutes: Int,
) : ViewModel() {

    val progress: StateFlow<Progress> = stats.observeProgress(goalMinutes)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), EMPTY)

    val extremes: StateFlow<Pair<List<RankedBit>, List<RankedBit>>> = stats.observeExtremes()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            emptyList<RankedBit>() to emptyList(),
        )

    val inboxCount: StateFlow<Int> = bits.observeInbox()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val EMPTY = Progress(
            funnel = Funnel(emptyMap()),
            polishedMinutes = 0.0,
            goalMinutes = 0,
            actOutRatio = 0.0,
            attitudeSpread = emptyMap(),
            gigsLast30Days = 0,
            streakDays = 0,
        )
    }
}
