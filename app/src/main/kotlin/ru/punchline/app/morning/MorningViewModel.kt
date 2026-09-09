package ru.punchline.app.morning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.BitRepository
import ru.punchline.data.repo.PracticeRepository
import ru.punchline.data.repo.StreakRepository

/** Одна запись утренних страниц в виде, пригодном для списка. */
data class MorningEntry(val id: String, val dateMillis: Long, val text: String)

class MorningViewModel(
    private val practice: PracticeRepository,
    private val streaks: StreakRepository,
    private val bits: BitRepository,
) : ViewModel() {

    val entries: StateFlow<List<MorningEntry>> = practice.observeMorningWritings()
        .map { rows -> rows.map { MorningEntry(it.id, it.dateMillis, it.text) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val streak: StateFlow<Int> = streaks.observeCurrentLength()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val _harvested = MutableStateFlow(0)
    /** Сколько кусков утреннего текста уже утащено в материал за эту сессию. */
    val harvested: StateFlow<Int> = _harvested.asStateFlow()

    /**
     * Сохранить утреннюю запись. Минуты идут в цепочку Сайнфелда: главная
     * метрика приложения — непрерывность, а не объём написанного.
     */
    fun save(text: String, minutes: Int) {
        if (text.isBlank()) return
        viewModelScope.launch {
            practice.saveMorningWriting(text, audioHash = null, promptId = null)
            streaks.recordWriting(minutes)
        }
    }

    /** Выделенный кусок утреннего текста становится зерном будущей шутки. */
    fun harvest(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            bits.capture(text)
            _harvested.value += 1
        }
    }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}
