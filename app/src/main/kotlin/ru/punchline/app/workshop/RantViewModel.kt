package ru.punchline.app.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.punchline.app.audio.AudioRecorder
import ru.punchline.data.repo.WorkshopRepository
import ru.punchline.model.Id

/** Состояние экрана ранта. Записи без сохранения быть не должно. */
sealed interface RantState {
    data object Idle : RantState
    data object Recording : RantState
    data class Saved(val durationSec: Int) : RantState
    data object NothingRecorded : RantState
}

/**
 * Рант (упражнение 11): выговориться по теме вслух, не останавливаясь.
 *
 * Аудио — результат сам по себе, транскрипт не обязателен: распознавание
 * длинной непрерывной речи ненадёжно, и запись не должна от него зависеть.
 */
class RantViewModel(
    private val workshop: WorkshopRepository,
    private val recorder: AudioRecorder,
    private val topicId: Id?,
) : ViewModel() {

    private val _state = MutableStateFlow<RantState>(RantState.Idle)
    val state: StateFlow<RantState> = _state.asStateFlow()

    fun start() {
        runCatching { recorder.start() }
            .onSuccess { _state.value = RantState.Recording }
            .onFailure { _state.value = RantState.NothingRecorded }
    }

    fun stop() {
        val recording = recorder.stop()
        if (recording == null) {
            _state.value = RantState.NothingRecorded
            return
        }
        viewModelScope.launch {
            workshop.saveRant(
                topicId = topicId,
                audioHash = recording.blob.hash,
                durationSec = recording.durationSec,
                transcript = null,
            )
            _state.value = RantState.Saved(recording.durationSec)
        }
    }

    /** Записанный кусок мысли, набранный по ходу или после, — сразу в материал. */
    fun harvest(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { workshop.harvest(topicId, text) }
    }

    override fun onCleared() {
        // Уход с экрана во время записи не должен оставлять висящий рекордер.
        if (recorder.isRecording) recorder.cancel()
        super.onCleared()
    }
}
