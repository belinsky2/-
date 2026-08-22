package ru.punchline.app.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.GigRepository
import ru.punchline.data.repo.Promotion
import ru.punchline.data.repo.SetListRepository
import ru.punchline.model.GigStats
import ru.punchline.model.Id
import ru.punchline.model.LaughResult

/** Строка разбора: шутка и уже поставленная (или ещё нет) отметка. */
data class ReviewRow(
    val bitId: Id,
    val title: String,
    val marked: LaughResult?,
)

/**
 * Разбор выступления — упражнение 35. Здесь замыкается петля: реакция зала
 * превращается в данные, а данные пересобирают акт. Без этого шага «Мой акт»
 * наполняется по ощущениям, а ощущения врут в обе стороны.
 */
class GigReviewViewModel(
    private val gigs: GigRepository,
    private val setLists: SetListRepository,
    private val bits: BitRepository,
    private val gigId: Id,
    private val setListId: Id?,
) : ViewModel() {

    private val _marks = MutableStateFlow<Map<Id, LaughResult>>(emptyMap())

    /**
     * Порядок строк — порядок сета: разбирать выступление удобно в той же
     * последовательности, в какой оно шло, а не по алфавиту.
     */
    private val setBitIds: Flow<List<Id>> =
        setListId?.let(setLists::observeItemBitIds) ?: flowOf(emptyList())

    val rows: StateFlow<List<ReviewRow>> = combine(
        bits.observeCards(),
        setBitIds,
        _marks,
    ) { cards, orderedIds, marks ->
        val byId = cards.associateBy { it.bit.id }
        orderedIds.mapNotNull { id ->
            byId[id]?.let { ReviewRow(id, it.bit.title, marks[id]) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val stats: StateFlow<GigStats> = gigs.observeStats(gigId)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            GigStats(0, 0.0, 0.0),
        )

    private val _promotions = MutableStateFlow<List<Promotion>>(emptyList())
    /** Что движок изменил по итогам разбора. Показывается явно, а не молча. */
    val promotions: StateFlow<List<Promotion>> = _promotions.asStateFlow()

    val progress: StateFlow<Pair<Int, Int>> = rows
        .map { list -> list.count { it.marked != null } to list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0 to 0)

    /** Отметка в один тап: разбор десятка шуток не должен превращаться в анкету. */
    fun mark(bitId: Id, result: LaughResult) {
        _marks.value += bitId to result
        viewModelScope.launch { gigs.mark(gigId, bitId, result) }
    }

    /** Применить заслуженные повышения. Вызывается явно, когда разбор закончен. */
    fun applyPromotions() {
        viewModelScope.launch { _promotions.value = gigs.applyDeservedPromotions() }
    }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}
