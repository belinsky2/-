package ru.punchline.app.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val bitId: Id,
) : ViewModel() {

    private val _bit = MutableStateFlow<Bit?>(null)
    val bit: StateFlow<Bit?> = _bit.asStateFlow()

    private val _step = MutableStateFlow(WorkshopStep.ATTITUDE)
    val step: StateFlow<WorkshopStep> = _step.asStateFlow()

    init { reload() }

    private fun reload() {
        viewModelScope.launch {
            val loaded = bits.byId(bitId)
            _bit.value = loaded
            _step.value = loaded?.let(::firstUnfinishedStep) ?: WorkshopStep.ATTITUDE
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

    fun setAttitude(attitude: Attitude) = edit { bits.setAttitude(bitId, attitude) }

    fun setPremise(text: String) = edit { bits.setPremise(bitId, text) }

    fun setPunch(text: String, technique: PunchTechnique) =
        edit { bits.setPunch(bitId, Punch(text, technique)) }

    fun setActOut(text: String, hasSpaceWork: Boolean, audioHash: String?) =
        edit { bits.setActOut(bitId, ActOut(text, hasSpaceWork, audioHash)) }

    fun setTags(tags: List<String>) = edit {
        bits.update(bitId) { it.copy(elements = it.elements.copy(tags = tags)) }
    }

    private fun edit(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            reload()
        }
    }
}
