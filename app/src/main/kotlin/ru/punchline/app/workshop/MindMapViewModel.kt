package ru.punchline.app.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.punchline.data.repo.MindMapNode
import ru.punchline.data.repo.WorkshopRepository
import ru.punchline.model.Id

class MindMapViewModel(
    private val workshop: WorkshopRepository,
    private val topicId: Id,
) : ViewModel() {

    val nodes: StateFlow<List<MindMapNode>> = workshop.observeMindMap(topicId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Узел, к которому сейчас дописывают ветку. null — корень. */
    private val _attachTo = MutableStateFlow<Id?>(null)
    val attachTo: StateFlow<Id?> = _attachTo.asStateFlow()

    fun attachTo(nodeId: Id?) { _attachTo.value = nodeId }

    fun add(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { workshop.addNode(topicId, _attachTo.value, text) }
    }

    fun promote(nodeId: Id) {
        viewModelScope.launch { workshop.promoteNode(nodeId) }
    }

    fun delete(nodeId: Id) {
        viewModelScope.launch { workshop.deleteNode(nodeId) }
    }

    private companion object { const val STOP_TIMEOUT_MS = 5_000L }
}
