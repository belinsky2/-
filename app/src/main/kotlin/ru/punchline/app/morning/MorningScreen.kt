package ru.punchline.app.morning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import ru.punchline.app.R

/**
 * Утренние страницы (упражнение 4). Свободное письмо по таймеру, после
 * которого зацепившее вытаскивается в материал.
 */
@Composable
fun MorningScreen(viewModel: MorningViewModel) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val streak by viewModel.streak.collectAsStateWithLifecycle()

    var text by remember { mutableStateOf("") }
    var seconds by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running) {
            delay(1_000)
            seconds += 1
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.morning_streak, streak),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text = stringResource(R.string.morning_timer, seconds / 60, seconds % 60),
            style = MaterialTheme.typography.labelLarge,
        )

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                // Таймер стартует от первого символа: отдельная кнопка «начать»
                // это лишний шаг между пробуждением и письмом.
                if (!running && it.isNotEmpty()) running = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.morning_hint)) },
            minLines = 6,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.save(text, (seconds / 60).coerceAtLeast(1))
                    text = ""
                    seconds = 0
                    running = false
                },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.morning_save)) }

            OutlinedButton(
                onClick = { viewModel.harvest(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.morning_harvest)) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.text.take(PREVIEW_CHARS),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

private const val PREVIEW_CHARS = 240
