package ru.punchline.app.workshop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.model.Attitude
import ru.punchline.model.Bit
import ru.punchline.model.PunchTechnique

/**
 * Мастерская одной шутки. Шаги идут в порядке методики, но экран не запирает:
 * можно перепрыгнуть на любой шаг и уйти в любой момент.
 */
@Composable
fun BitWorkshopScreen(viewModel: BitWorkshopViewModel) {
    val bit by viewModel.bit.collectAsStateWithLifecycle()
    val step by viewModel.step.collectAsStateWithLifecycle()
    val current = bit ?: return

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(current.title, style = MaterialTheme.typography.titleLarge)

        StepBar(current = step, onSelect = viewModel::goTo)

        when (step) {
            WorkshopStep.ATTITUDE -> AttitudeStep(current, viewModel)
            WorkshopStep.PREMISE -> PremiseStep(current, viewModel)
            WorkshopStep.PUNCH -> PunchStep(current, viewModel)
            WorkshopStep.ACT_OUT -> ActOutStep(current, viewModel)
            WorkshopStep.TAGS -> TagsStep(current, viewModel)
        }
    }
}

@Composable
private fun StepBar(current: WorkshopStep, onSelect: (WorkshopStep) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkshopStep.entries.forEach { step ->
            FilterChip(
                selected = step == current,
                onClick = { onSelect(step) },
                label = { Text(stringResource(step.labelRes())) },
            )
        }
    }
}

@Composable
private fun AttitudeStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workshop_attitude_question),
            style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Attitude.entries.forEach { attitude ->
                FilterChip(
                    selected = bit.attitude == attitude,
                    onClick = { viewModel.setAttitude(attitude) },
                    label = { Text(stringResource(attitude.labelRes())) },
                )
            }
        }
    }
}

@Composable
private fun PremiseStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var text by remember(bit.id) { mutableStateOf(bit.elements.premise.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Подсказка-шаблон из методики: премиса — это всегда «самое X в Y».
        Text(
            text = bit.attitude
                ?.let { stringResource(R.string.workshop_premise_template, stringResource(it.labelRes())) }
                ?: stringResource(R.string.workshop_premise_no_attitude),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(onClick = { viewModel.setPremise(text) }, enabled = text.isNotBlank()) {
            Text(stringResource(R.string.workshop_save))
        }
    }
}

@Composable
private fun PunchStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var text by remember(bit.id) { mutableStateOf(bit.elements.punch?.text.orEmpty()) }
    var technique by remember(bit.id) {
        mutableStateOf(bit.elements.punch?.technique ?: PunchTechnique.TURN)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workshop_punch_question),
            style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PunchTechnique.entries.forEach { option ->
                FilterChip(
                    selected = technique == option,
                    onClick = { technique = option },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(onClick = { viewModel.setPunch(text, technique) }, enabled = text.isNotBlank()) {
            Text(stringResource(R.string.workshop_save))
        }
    }
}

@Composable
private fun ActOutStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var text by remember(bit.id) { mutableStateOf(bit.elements.actOut?.text.orEmpty()) }
    var spaceWork by remember(bit.id) {
        mutableStateOf(bit.elements.actOut?.hasSpaceWork ?: false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workshop_actout_question),
            style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = spaceWork, onCheckedChange = { spaceWork = it })
            Text(stringResource(R.string.workshop_space_work))
        }
        Button(
            onClick = {
                viewModel.setActOut(text, spaceWork, bit.elements.actOut?.audioHash)
            },
            enabled = text.isNotBlank(),
        ) { Text(stringResource(R.string.workshop_save)) }
    }
}

@Composable
private fun TagsStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var draft by remember(bit.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.workshop_tags_question),
            style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            bit.elements.tags.forEach { tag ->
                AssistChip(onClick = {
                    viewModel.setTags(bit.elements.tags - tag)
                }, label = { Text(tag) })
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.setTags(bit.elements.tags + draft.trim())
                draft = ""
            },
            enabled = draft.isNotBlank(),
        ) { Text(stringResource(R.string.workshop_tag_add)) }
    }
}

private fun WorkshopStep.labelRes(): Int = when (this) {
    WorkshopStep.ATTITUDE -> R.string.workshop_step_attitude
    WorkshopStep.PREMISE -> R.string.workshop_step_premise
    WorkshopStep.PUNCH -> R.string.workshop_step_punch
    WorkshopStep.ACT_OUT -> R.string.workshop_step_actout
    WorkshopStep.TAGS -> R.string.workshop_step_tags
}

private fun Attitude.labelRes(): Int = when (this) {
    Attitude.HARD -> R.string.attitude_hard
    Attitude.WEIRD -> R.string.attitude_weird
    Attitude.SCARY -> R.string.attitude_scary
    Attitude.STUPID -> R.string.attitude_stupid
}

private fun PunchTechnique.labelRes(): Int = when (this) {
    PunchTechnique.MIX -> R.string.punch_mix
    PunchTechnique.TURN -> R.string.punch_turn
    PunchTechnique.LIST_OF_THREE -> R.string.punch_list_of_three
    PunchTechnique.SELF_MOCKING -> R.string.punch_self_mocking
    PunchTechnique.OTHER -> R.string.punch_other
}
