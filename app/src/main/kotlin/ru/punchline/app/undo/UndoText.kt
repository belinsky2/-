package ru.punchline.app.undo

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.punchline.app.R
import ru.punchline.model.Attitude
import ru.punchline.model.BitStatus
import ru.punchline.model.PunchTechnique

/** Перевод описания действия в текст подсказки. Все строки — из ресурсов. */
@Composable
fun UndoLabel.text(): String = when (this) {
    is UndoLabel.AttitudeSet ->
        stringResource(R.string.undo_attitude, stringResource(attitude.labelRes()))
    UndoLabel.PremiseSet -> stringResource(R.string.undo_premise)
    is UndoLabel.PunchSet ->
        stringResource(R.string.undo_punch, stringResource(technique.labelRes()))
    UndoLabel.ActOutSet -> stringResource(R.string.undo_act_out)
    is UndoLabel.TagAdded -> stringResource(R.string.undo_tag_added, tag)
    is UndoLabel.TagRemoved -> stringResource(R.string.undo_tag_removed, tag)
    is UndoLabel.TitleSet -> stringResource(R.string.undo_title, title)
    is UndoLabel.BitCreated -> stringResource(R.string.undo_bit_created, title)
    is UndoLabel.BitDeleted -> stringResource(R.string.undo_bit_deleted, title)
    is UndoLabel.StatusChanged ->
        stringResource(R.string.undo_status, stringResource(to.labelRes()))
    is UndoLabel.TopicAdded -> stringResource(R.string.undo_topic_added, title)
    is UndoLabel.TopicDeleted -> stringResource(R.string.undo_topic_deleted, title)
}

fun Attitude.labelRes(): Int = when (this) {
    Attitude.HARD -> R.string.attitude_hard
    Attitude.WEIRD -> R.string.attitude_weird
    Attitude.SCARY -> R.string.attitude_scary
    Attitude.STUPID -> R.string.attitude_stupid
}

fun PunchTechnique.labelRes(): Int = when (this) {
    PunchTechnique.MIX -> R.string.punch_mix
    PunchTechnique.TURN -> R.string.punch_turn
    PunchTechnique.LIST_OF_THREE -> R.string.punch_list_of_three
    PunchTechnique.SELF_MOCKING -> R.string.punch_self_mocking
    PunchTechnique.OTHER -> R.string.punch_other
}

fun BitStatus.labelRes(): Int = when (this) {
    BitStatus.SEED -> R.string.status_seed
    BitStatus.PREMISE -> R.string.status_premise
    BitStatus.DRAFT -> R.string.status_draft
    BitStatus.TESTED -> R.string.status_tested
    BitStatus.POLISHED -> R.string.status_polished
    BitStatus.PARKED -> R.string.status_parked
    BitStatus.RETIRED -> R.string.status_retired
}
