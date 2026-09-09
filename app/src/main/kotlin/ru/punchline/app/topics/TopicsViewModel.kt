package ru.punchline.app.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.CoreResult
import ru.punchline.data.repo.TopicRepository
import ru.punchline.model.Id
import ru.punchline.model.Topic

class TopicsViewModel(private val topics: TopicRepository) : ViewModel() {

    val all: StateFlow<List<Topic>> = topics.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Показывается, когда главных тем стало больше трёх. Предупреждение, не запрет. */
    private val _overLimit = MutableStateFlow(false)
    val overLimit: StateFlow<Boolean> = _overLimit.asStateFlow()

    val coreLimit: Int = topics.coreLimit

    fun add(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { topics.add(title) }
    }

    fun setPassion(id: Id, score: Int) {
        viewModelScope.launch { topics.setPassion(id, score) }
    }

    fun toggleCore(topic: Topic) {
        viewModelScope.launch {
            val result = topics.setCore(topic.id, !topic.isCore)
            _overLimit.value = result is CoreResult.OverLimit
        }
    }

    fun dismissWarning() { _overLimit.value = false }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}
