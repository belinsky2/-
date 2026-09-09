package ru.punchline.app.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.model.Attitude
import ru.punchline.model.BitStatus

@Composable
fun TodayScreen(viewModel: TodayViewModel, onOpenInbox: () -> Unit, onOpenBackup: () -> Unit) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val extremes by viewModel.extremes.collectAsStateWithLifecycle()
    val (best, worst) = extremes
    val inbox by viewModel.inboxCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.today_streak, progress.streakDays),
            style = MaterialTheme.typography.headlineSmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(
                        R.string.today_material,
                        progress.polishedMinutes.toInt(),
                        progress.goalMinutes,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { progress.goalRatio.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.today_gigs, progress.gigsLast30Days),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        if (inbox > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.today_inbox, inbox))
                    TextButton(onClick = onOpenInbox) {
                        Text(stringResource(R.string.today_inbox_action))
                    }
                }
            }
        }

        progress.bottleneck?.let { stage ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.today_bottleneck,
                        progress.funnel.count(stage),
                        stringResource(stage.labelRes()),
                    ),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        // Перекос по отношениям: если весь материал «про сложное», методика
        // предлагает попробовать странное или страшное.
        if (progress.attitudeSpread.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.today_attitudes),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Attitude.entries.forEach { attitude ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(stringResource(attitude.labelRes()))
                            Text((progress.attitudeSpread[attitude] ?: 0).toString())
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.today_act_out_ratio,
                            (progress.actOutRatio * 100).toInt(),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        if (best.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.today_best),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    best.forEach { Text("${it.title} — ${String.format("%.1f", it.score)}") }
                    if (worst.isNotEmpty() && worst != best) {
                        Text(
                            text = stringResource(R.string.today_worst),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        worst.forEach { Text("${it.title} — ${String.format("%.1f", it.score)}") }
                    }
                }
            }
        }

        TextButton(onClick = onOpenBackup) {
            Text(stringResource(R.string.today_backup))
        }
    }
}

private fun Attitude.labelRes(): Int = when (this) {
    Attitude.HARD -> R.string.attitude_hard
    Attitude.WEIRD -> R.string.attitude_weird
    Attitude.SCARY -> R.string.attitude_scary
    Attitude.STUPID -> R.string.attitude_stupid
}

private fun BitStatus.labelRes(): Int = when (this) {
    BitStatus.SEED -> R.string.status_seed
    BitStatus.PREMISE -> R.string.status_premise
    BitStatus.DRAFT -> R.string.status_draft
    BitStatus.TESTED -> R.string.status_tested
    BitStatus.POLISHED -> R.string.status_polished
    BitStatus.PARKED -> R.string.status_parked
    BitStatus.RETIRED -> R.string.status_retired
}
