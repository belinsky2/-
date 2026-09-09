package ru.punchline.app.workshop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import ru.punchline.app.undo.labelRes
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
        TitleField(current, viewModel)

        StepBar(current = step, bit = current, onSelect = viewModel::goTo)

        when (step) {
            WorkshopStep.ATTITUDE -> AttitudeStep(current, viewModel)
            WorkshopStep.PREMISE -> PremiseStep(current, viewModel)
            WorkshopStep.PUNCH -> PunchStep(current, viewModel)
            WorkshopStep.ACT_OUT -> ActOutStep(current, viewModel)
            WorkshopStep.TAGS -> TagsStep(current, viewModel)
        }
    }
}

/** Название правится прямо здесь: заголовок шутки — тоже часть работы над ней. */
@Composable
private fun TitleField(bit: Bit, viewModel: BitWorkshopViewModel) {
    var title by remember(bit.id, bit.title) { mutableStateOf(bit.title) }
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.workshop_title_hint)) },
        singleLine = false,
        trailingIcon = {
            if (title.trim() != bit.title) {
                Button(onClick = { viewModel.setTitle(title) }) {
                    Text(stringResource(R.string.workshop_save_short))
                }
            }
        },
    )
}

/**
 * Полоса шагов. У пройденных стоит галочка — иначе непонятно, что уже сделано,
 * а что нет, и приходится заходить в каждый шаг, чтобы это выяснить.
 */
@Composable
private fun StepBar(current: WorkshopStep, bit: Bit, onSelect: (WorkshopStep) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkshopStep.entries.forEach { step ->
            val done = step.isFilled(bit)
            FilterChip(
                selected = step == current,
                onClick = { onSelect(step) },
                label = {
                    Text(
                        if (done) {
                            stringResource(R.string.workshop_step_done, stringResource(step.labelRes()))
                        } else {
                            stringResource(step.labelRes())
                        }
                    )
                },
            )
        }
    }
}

@Composable
private fun AttitudeStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.workshop_attitude_question),
            style = MaterialTheme.typography.bodyMedium,
        )
        // FlowRow, а не Row: четыре слова не помещаются в ширину телефона,
        // и последний чип сжимался до столбика из букв.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
    var text by remember(bit.id, bit.elements.premise) {
        mutableStateOf(bit.elements.premise.orEmpty())
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Подсказка-шаблон из методики: премиса — это всегда «самое X в Y».
        Text(
            text = bit.attitude
                ?.let {
                    stringResource(R.string.workshop_premise_template, stringResource(it.labelRes()))
                }
                ?: stringResource(R.string.workshop_premise_no_attitude),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        SaveButton(
            changed = text.trim() != bit.elements.premise.orEmpty().trim(),
            enabled = text.isNotBlank(),
        ) { viewModel.setPremise(text.trim()) }
    }
}

@Composable
private fun PunchStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var text by remember(bit.id, bit.elements.punch?.text) {
        mutableStateOf(bit.elements.punch?.text.orEmpty())
    }
    var technique by remember(bit.id, bit.elements.punch?.technique) {
        mutableStateOf(bit.elements.punch?.technique ?: PunchTechnique.TURN)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.workshop_punch_question),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
        SaveButton(
            changed = text.trim() != bit.elements.punch?.text.orEmpty().trim() ||
                technique != bit.elements.punch?.technique,
            enabled = text.isNotBlank(),
        ) { viewModel.setPunch(text.trim(), technique) }
    }
}

@Composable
private fun ActOutStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var text by remember(bit.id, bit.elements.actOut?.text) {
        mutableStateOf(bit.elements.actOut?.text.orEmpty())
    }
    var spaceWork by remember(bit.id, bit.elements.actOut?.hasSpaceWork) {
        mutableStateOf(bit.elements.actOut?.hasSpaceWork ?: false)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.workshop_actout_question),
            style = MaterialTheme.typography.bodyMedium,
        )
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
        SaveButton(
            changed = text.trim() != bit.elements.actOut?.text.orEmpty().trim() ||
                spaceWork != (bit.elements.actOut?.hasSpaceWork ?: false),
            enabled = text.isNotBlank(),
        ) { viewModel.setActOut(text.trim(), spaceWork, bit.elements.actOut?.audioHash) }
    }
}

@Composable
private fun TagsStep(bit: Bit, viewModel: BitWorkshopViewModel) {
    var draft by remember(bit.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.workshop_tags_question),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (bit.elements.tags.isEmpty()) {
            Text(
                stringResource(R.string.workshop_tags_empty),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bit.elements.tags.forEach { tag ->
                    AssistChip(
                        onClick = { viewModel.removeTag(tag) },
                        label = { Text(stringResource(R.string.workshop_tag_remove, tag)) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { viewModel.addTag(draft); draft = "" },
            enabled = draft.isNotBlank(),
        ) { Text(stringResource(R.string.workshop_tag_add)) }
    }
}

/**
 * Кнопка сохранения меняет текст, когда сохранять нечего. Раньше она выглядела
 * одинаково всегда, и было непонятно, сохранилось ли что-нибудь вообще.
 */
@Composable
private fun SaveButton(changed: Boolean, enabled: Boolean, onSave: () -> Unit) {
    Button(onClick = onSave, enabled = enabled && changed) {
        Text(
            if (changed) stringResource(R.string.workshop_save)
            else stringResource(R.string.workshop_saved)
        )
    }
}

private fun WorkshopStep.isFilled(bit: Bit): Boolean = when (this) {
    WorkshopStep.ATTITUDE -> bit.attitude != null
    WorkshopStep.PREMISE -> !bit.elements.premise.isNullOrBlank()
    WorkshopStep.PUNCH -> bit.elements.punch != null
    WorkshopStep.ACT_OUT -> bit.elements.actOut != null
    WorkshopStep.TAGS -> bit.elements.tags.isNotEmpty()
}

private fun WorkshopStep.labelRes(): Int = when (this) {
    WorkshopStep.ATTITUDE -> R.string.workshop_step_attitude
    WorkshopStep.PREMISE -> R.string.workshop_step_premise
    WorkshopStep.PUNCH -> R.string.workshop_step_punch
    WorkshopStep.ACT_OUT -> R.string.workshop_step_actout
    WorkshopStep.TAGS -> R.string.workshop_step_tags
}
