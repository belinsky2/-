package ru.punchline.app.stage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.GigRepository
import ru.punchline.data.repo.SetListRepository
import ru.punchline.data.repo.StreakRepository
import ru.punchline.model.GigType
import ru.punchline.model.Id

/** Одна позиция в режиме сцены: только то, что нужно увидеть боковым зрением. */
data class StageCue(
    val bitId: Id,
    val position: Int,
    val cue: String,
    val plannedSec: Int?,
)

class StageViewModel(
    private val setLists: SetListRepository,
    private val bits: BitRepository,
    private val gigs: GigRepository,
    private val streaks: StreakRepository,
    private val setListId: Id,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val cues: StateFlow<List<StageCue>> = combine(
        setLists.observeCard(setListId, gigs.observeAverageScores()),
        bits.observeCards(),
    ) { card, allBits ->
        val byId = allBits.associate { it.bit.id to it.bit }
        card?.setList?.items.orEmpty().sortedBy { it.order }.mapIndexed { index, item ->
            val bit = byId[item.bitId]
            StageCue(
                bitId = item.bitId,
                position = index + 1,
                // На сцене нужны опорные слова, а не полный текст: читать шутку
                // с экрана — самый быстрый способ её убить.
                cue = bit?.let(::cueFor).orEmpty(),
                plannedSec = item.plannedDurationSec,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _gigId = MutableStateFlow<Id?>(null)
    val gigId: StateFlow<Id?> = _gigId.asStateFlow()

    val total: StateFlow<Int> = cues
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    fun start(type: GigType, venue: String) {
        viewModelScope.launch {
            _gigId.value = gigs.start(type, venue, setListId)
            _index.value = 0
        }
    }

    fun next() {
        val size = cues.value.size
        if (_index.value < size - 1) _index.value += 1
    }

    fun previous() {
        if (_index.value > 0) _index.value -= 1
    }

    /** Завершение выхода. Цепочка Сайнфелда закрывается сценой не хуже, чем письмом. */
    fun finish(elapsedSec: Int, audioHash: String?) {
        val id = _gigId.value ?: return
        viewModelScope.launch {
            gigs.finish(id, elapsedSec, audioHash)
            streaks.recordPerformance()
        }
    }

    private fun cueFor(bit: ru.punchline.model.Bit): String =
        listOfNotNull(
            bit.title.takeIf { it.isNotBlank() },
            bit.elements.punch?.text?.take(CUE_CHARS),
        ).joinToString(SEPARATOR)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val CUE_CHARS = 60
        const val SEPARATOR = "  →  "
    }
}
