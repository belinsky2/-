package ru.punchline.app.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.punchline.app.undo.UndoBus
import ru.punchline.app.undo.UndoLabel
import ru.punchline.data.repo.BitRepository
import ru.punchline.model.ActOut
import ru.punchline.model.Attitude
import ru.punchline.model.Bit
import ru.punchline.model.Id
import ru.punchline.model.Punch
import ru.punchline.model.PunchTechnique

/**
 * Шаги мастерской. Порядок — из Part Two: сначала отношение, потом премиса,
 * потом панч, потом игра. Выйти можно на любом шаге: недоделанная шутка
 * остаётся в работе, а не пропадает.
 */
enum class WorkshopStep { ATTITUDE, PREMISE, PUNCH, ACT_OUT, TAGS }

class BitWorkshopViewModel(
    private val bits: BitRepository,
    private val undo: UndoBus,
    private val bitId: Id,
) : ViewModel() {

    private val _bit = MutableStateFlow<Bit?>(null)
    val bit: StateFlow<Bit?> = _bit.asStateFlow()

    private val _step = MutableStateFlow(WorkshopStep.ATTITUDE)
    val step: StateFlow<WorkshopStep> = _step.asStateFlow()

    init { load(chooseStep = true) }

    /**
     * [chooseStep] выставляется только при первом открытии.
     *
     * Раньше шаг пересчитывался после каждой правки, и сохранение добивки
     * отбрасывало пользователя на шаг «Act-out» — выглядело так, будто
     * ничего не сохранилось. Экран не должен уводить с того места,
     * где человек сейчас работает.
     */
    private fun load(chooseStep: Boolean = false) {
        viewModelScope.launch {
            val loaded = bits.byId(bitId)
            _bit.value = loaded
            if (chooseStep) {
                _step.value = loaded?.let(::firstUnfinishedStep) ?: WorkshopStep.ATTITUDE
            }
        }
    }

    /** Открываем не первый шаг, а первый незаполненный — чтобы не листать сделанное. */
    private fun firstUnfinishedStep(bit: Bit): WorkshopStep = when {
        bit.attitude == null -> WorkshopStep.ATTITUDE
        bit.elements.premise.isNullOrBlank() -> WorkshopStep.PREMISE
        bit.elements.punch == null -> WorkshopStep.PUNCH
        bit.elements.actOut == null -> WorkshopStep.ACT_OUT
        else -> WorkshopStep.TAGS
    }

    fun goTo(step: WorkshopStep) { _step.value = step }

    fun setTitle(title: String) =
        edit(UndoLabel.TitleSet(title)) { bits.setTitle(bitId, title.trim()) }

    fun setAttitude(attitude: Attitude) =
        edit(UndoLabel.AttitudeSet(attitude)) { bits.setAttitude(bitId, attitude) }

    fun setPremise(text: String) =
        edit(UndoLabel.PremiseSet) { bits.setPremise(bitId, text) }

    fun setPunch(text: String, technique: PunchTechnique) =
        edit(UndoLabel.PunchSet(technique)) { bits.setPunch(bitId, Punch(text, technique)) }

    fun setActOut(text: String, hasSpaceWork: Boolean, audioHash: String?) =
        edit(UndoLabel.ActOutSet) { bits.setActOut(bitId, ActOut(text, hasSpaceWork, audioHash)) }

    fun addTag(tag: String) {
        val clean = tag.trim()
        if (clean.isBlank()) return
        edit(UndoLabel.TagAdded(clean)) {
            bits.setTags(bitId, (_bit.value?.elements?.tags.orEmpty()) + clean)
        }
    }

    fun removeTag(tag: String) = edit(UndoLabel.TagRemoved(tag)) {
        bits.setTags(bitId, (_bit.value?.elements?.tags.orEmpty()) - tag)
    }

    /**
     * Любая правка выполняется, перечитывается и кладёт в шину отмены способ
     * вернуть прежнее состояние. Если менять было нечего, подсказка не всплывает:
     * сообщать об отмене того, чего не произошло, — обман.
     */
    private fun edit(label: UndoLabel, block: suspend () -> Bit?) {
        viewModelScope.launch {
            val previous = block()
            load()
            if (previous != null) {
                undo.push(label) {
                    bits.restore(previous)
                    load()
                }
            }
        }
    }
}
