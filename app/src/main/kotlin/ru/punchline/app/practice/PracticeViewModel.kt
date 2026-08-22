package ru.punchline.app.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.ExerciseCard
import ru.punchline.data.repo.PracticeRepository

/** Упражнения, сгруппированные по частям книги, в порядке чтения. */
data class PracticeSection(val part: String, val exercises: List<ExerciseCard>)

class PracticeViewModel(private val practice: PracticeRepository) : ViewModel() {

    val sections: StateFlow<List<PracticeSection>> = practice.observeExercises()
        .map { all ->
            PART_ORDER.mapNotNull { part ->
                all.filter { it.part == part }
                    .takeIf { it.isNotEmpty() }
                    ?.let { PracticeSection(part, it.sortedBy(ExerciseCard::number)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val progress: StateFlow<Pair<Int, Int>> = practice.observeExercises()
        .map { all -> all.count { it.isDone } to all.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0 to 0)

    fun saveAnswer(card: ExerciseCard, field: String, value: String) {
        viewModelScope.launch {
            practice.saveAnswers(card.number, card.answers + (field to value), card.isDone)
        }
    }

    fun toggleDone(card: ExerciseCard) {
        viewModelScope.launch {
            practice.saveAnswers(card.number, card.answers, !card.isDone)
        }
    }

    fun rename(card: ExerciseCard, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { practice.renameExercise(card, title) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val PART_ORDER = listOf("intro", "one", "two", "three", "four")
    }
}
