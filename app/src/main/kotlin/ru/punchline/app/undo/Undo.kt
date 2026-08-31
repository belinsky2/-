package ru.punchline.app.undo

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.punchline.model.Attitude
import ru.punchline.model.BitStatus
import ru.punchline.model.PunchTechnique

/**
 * Что именно было сделано. Не строка: текст живёт в ресурсах, а сюда
 * попадает только смысл действия — иначе «Отменить» пришлось бы показывать
 * без объяснения, что отменяется, а это ровно то, чего делать нельзя.
 */
sealed interface UndoLabel {
    data class AttitudeSet(val attitude: Attitude) : UndoLabel
    data object PremiseSet : UndoLabel
    data class PunchSet(val technique: PunchTechnique) : UndoLabel
    data object ActOutSet : UndoLabel
    data class TagAdded(val tag: String) : UndoLabel
    data class TagRemoved(val tag: String) : UndoLabel
    data class TitleSet(val title: String) : UndoLabel
    data class BitCreated(val title: String) : UndoLabel
    data class BitDeleted(val title: String) : UndoLabel
    data class StatusChanged(val to: BitStatus) : UndoLabel
    data class TopicAdded(val title: String) : UndoLabel
    data class TopicDeleted(val title: String) : UndoLabel
}

/** Действие, которое можно откатить, вместе с описанием. */
data class UndoableAction(val label: UndoLabel, val undo: suspend () -> Unit)

/**
 * Одна общая шина отмены на приложение.
 *
 * Отмена показывается сразу после действия и рядом с тем, что произошло:
 * постоянная кнопка «Отмена» в углу экрана заставляет гадать, что она откатит,
 * а это хуже, чем её отсутствие. Здесь же в подсказке написано ровно то,
 * что будет отменено.
 *
 * Сохраняется только последнее действие: глубокая история для текста —
 * это уже история версий шутки, у неё другое место и другой экран.
 */
class UndoBus {
    private val _actions = MutableSharedFlow<UndoableAction>(extraBufferCapacity = 8)
    val actions: SharedFlow<UndoableAction> = _actions.asSharedFlow()

    fun push(label: UndoLabel, undo: suspend () -> Unit) {
        _actions.tryEmit(UndoableAction(label, undo))
    }
}
