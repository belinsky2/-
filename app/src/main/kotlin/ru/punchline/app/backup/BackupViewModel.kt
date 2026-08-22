package ru.punchline.app.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.punchline.data.repo.MutationSink
import ru.punchline.data.vault.VaultService
import ru.punchline.vault.ImportOutcome
import ru.punchline.vault.ImportPreview

/** Состояние экрана переноса. Все переходы явные — тихих действий здесь быть не должно. */
sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data class Exported(val bytes: Long) : BackupState
    data class Inspected(val preview: ImportPreview, val source: Uri) : BackupState
    data class Imported(val blobCount: Int) : BackupState
    data class Failed(val reason: String) : BackupState
}

class BackupViewModel(
    private val context: Context,
    private val vault: VaultService,
    private val sink: MutationSink,
) : ViewModel() {

    private val _state = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = _state.asStateFlow()

    fun export(target: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        vault.export(out, sink.current())
                    } ?: error("no stream")
                    documentSize(target)
                }
            }.onSuccess { _state.value = BackupState.Exported(it) }
                .onFailure { _state.value = BackupState.Failed(it.message.orEmpty()) }
        }
    }

    /** Осмотр архива до применения: пользователь должен видеть, что разворачивает. */
    fun inspect(source: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { vault.inspect { openInput(source) } }
            }.onSuccess { _state.value = BackupState.Inspected(it, source) }
                .onFailure { _state.value = BackupState.Failed(it.message.orEmpty()) }
        }
    }

    fun confirmImport(source: Uri) {
        _state.value = BackupState.Working
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { vault.import { openInput(source) } }
            }.onSuccess { outcome ->
                _state.value = when (outcome) {
                    is ImportOutcome.Restored -> BackupState.Imported(outcome.blobCount)
                    is ImportOutcome.Failed -> BackupState.Failed(outcome.problem.toString())
                }
            }.onFailure { _state.value = BackupState.Failed(it.message.orEmpty()) }
        }
    }

    fun dismiss() { _state.value = BackupState.Idle }

    private fun openInput(source: Uri) =
        context.contentResolver.openInputStream(source) ?: error("no stream")

    private fun documentSize(uri: Uri): Long =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
}
