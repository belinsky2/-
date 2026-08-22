package ru.punchline.app.stage

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.punchline.app.R
import ru.punchline.model.GigType

/**
 * Режим сцены.
 *
 * Экран, ради которого существует всё остальное: тёмный зал, телефон в руке
 * или на табурете, и нужно одним взглядом понять, что дальше. Поэтому здесь
 * нет ничего, кроме крупных опорных слов, таймера и одного жеста — тап
 * переводит к следующему номеру.
 */
@Composable
fun StageScreen(viewModel: StageViewModel, onExit: () -> Unit) {
    val cues by viewModel.cues.collectAsStateWithLifecycle()
    val index by viewModel.index.collectAsStateWithLifecycle()
    val gigId by viewModel.gigId.collectAsStateWithLifecycle()

    var elapsed by remember { mutableIntStateOf(0) }
    val started = gigId != null

    // Экран не должен гаснуть посреди выступления.
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(started) {
        while (started) {
            delay(1_000)
            elapsed += 1
        }
    }

    if (!started) {
        StartPrompt(onStart = { type, venue -> viewModel.start(type, venue) }, onExit = onExit)
        return
    }

    val cue = cues.getOrNull(index)
    val target = cues.sumOf { it.plannedSec ?: 0 }
    val overrun = target > 0 && elapsed > target

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { viewModel.next() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.stage_timer,
                    elapsed / 60,
                    elapsed % 60,
                    target / 60,
                ),
                color = if (overrun) MaterialTheme.colorScheme.error else Color.Gray,
                fontSize = TIMER_SP.sp,
            )

            Text(
                text = cue?.cue.orEmpty(),
                color = Color.White,
                fontSize = CUE_SP.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().align(Alignment.CenterHorizontally),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.stage_position, index + 1, cues.size),
                    color = Color.Gray,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::previous) {
                        Text(stringResource(R.string.stage_back))
                    }
                    Button(onClick = {
                        viewModel.finish(elapsed, audioHash = null)
                        onExit()
                    }) { Text(stringResource(R.string.stage_finish)) }
                }
            }
        }
    }
}

@Composable
private fun StartPrompt(onStart: (GigType, String) -> Unit, onExit: () -> Unit) {
    var venue by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.stage_before), style = MaterialTheme.typography.bodyLarge)
        androidx.compose.material3.OutlinedTextField(
            value = venue,
            onValueChange = { venue = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.stage_venue)) },
        )
        Button(
            onClick = { onStart(GigType.OPEN_MIC, venue) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.stage_start_open_mic)) }
        OutlinedButton(
            onClick = { onStart(GigType.REHEARSAL, venue) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.stage_start_rehearsal)) }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.stage_cancel))
        }
    }
}

private const val CUE_SP = 34
private const val TIMER_SP = 18
