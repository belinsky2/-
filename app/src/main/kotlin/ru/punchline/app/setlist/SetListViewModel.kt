package ru.punchline.app.setlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.BitCard
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.GigRepository
import ru.punchline.data.repo.SetListCard
import ru.punchline.data.repo.SetListRepository
import ru.punchline.model.BitStatus
import ru.punchline.model.Id
import ru.punchline.model.SetListRole

class SetListsViewModel(
    private val setLists: SetListRepository,
) : ViewModel() {

    val all = setLists.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun create(title: String, minutes: Int) {
        if (title.isBlank()) return
        viewModelScope.launch { setLists.create(title, minutes * 60) }
    }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}

class SetListDetailViewModel(
    private val setLists: SetListRepository,
    private val bits: BitRepository,
    gigs: GigRepository,
    private val setListId: Id,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val card: StateFlow<SetListCard?> = setLists
        .observeCard(setListId, gigs.observeAverageScores())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * Кандидаты в сет: только то, что уже видело зал или прошло мастерскую.
     * Зерно из входящих на сцену не выносят.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val candidates: StateFlow<List<BitCard>> = bits.observeCards()
        .map { cards ->
            cards.filter { it.bit.status in CANDIDATE_STATUSES }
                .sortedByDescending { it.bit.status == BitStatus.POLISHED }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun add(bitId: Id) {
        viewModelScope.launch { setLists.addBit(setListId, bitId) }
    }

    fun setRole(itemId: Id, role: SetListRole) {
        viewModelScope.launch { setLists.setRole(itemId, setListId, role) }
    }

    fun remove(itemId: Id) {
        viewModelScope.launch { setLists.removeItem(itemId, setListId) }
    }

    /** Перемещение номера на одну позицию: drag-and-drop на телефоне промахивается. */
    fun move(itemId: Id, delta: Int) {
        val items = card.value?.setList?.items?.sortedBy { it.order } ?: return
        val index = items.indexOfFirst { it.id == itemId }
        val target = index + delta
        if (index < 0 || target !in items.indices) return

        val reordered = items.toMutableList().apply { add(target, removeAt(index)) }
        viewModelScope.launch { setLists.reorder(setListId, reordered.map { it.id }) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val CANDIDATE_STATUSES = setOf(BitStatus.DRAFT, BitStatus.TESTED, BitStatus.POLISHED)
    }
}
