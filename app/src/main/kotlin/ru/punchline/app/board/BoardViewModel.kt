package ru.punchline.app.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.punchline.data.repo.BitCard
import ru.punchline.data.repo.BitRepository
import ru.punchline.model.BitStatus

/** Доска материала: всё, что в работе, сгруппированное по состоянию. */
class BoardViewModel(bits: BitRepository) : ViewModel() {

    val inProgress: StateFlow<List<BitCard>> = bits.observeCards()
        .map { cards ->
            cards.filter { it.bit.status in IN_PROGRESS }
                .sortedWith(
                    // Сначала то, по чему движок что-то предлагает: доска должна
                    // подсказывать следующий шаг, а не быть просто списком.
                    compareByDescending<BitCard> { it.promotionOffered || it.hints.isNotEmpty() }
                        .thenByDescending { it.bit.meta.updatedAt }
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val myAct: StateFlow<List<BitCard>> = bits.observeCards()
        .map { cards -> cards.filter { it.bit.status == BitStatus.POLISHED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val IN_PROGRESS = setOf(
            BitStatus.SEED, BitStatus.PREMISE, BitStatus.DRAFT, BitStatus.TESTED,
        )
    }
}
