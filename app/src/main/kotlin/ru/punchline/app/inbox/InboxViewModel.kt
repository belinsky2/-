package ru.punchline.app.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.BitRepository
import ru.punchline.model.Bit

class InboxViewModel(private val bits: BitRepository) : ViewModel() {

    val inbox: StateFlow<List<Bit>> = bits.observeInbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Захват идеи. Пустую строку не сохраняем — это не «пустая шутка», это промах по кнопке. */
    fun capture(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { bits.capture(text) }
    }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}
