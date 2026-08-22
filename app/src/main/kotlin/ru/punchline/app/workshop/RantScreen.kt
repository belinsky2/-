package ru.punchline.app.workshop

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
 * Рант по теме. Одна кнопка и таймер: экран не должен отвлекать от того,
 * ради чего он существует, — говорить не останавливаясь.
 */
@Composable
fun RantScreen(viewModel: RantViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var seconds by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }

    val recording = state is RantState.Recording

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.start() }

    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        seconds = 0
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.rant_explainer),
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            text = stringResource(R.string.rant_timer, seconds / 60, seconds % 60),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (recording) {
            Button(onClick = viewModel::stop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.rant_stop))
            }
        } else {
            Button(
                onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.rant_start)) }
        }

        when (val current = state) {
            is RantState.Saved -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.rant_saved, current.durationSec),
                    modifier = Modifier.padding(16.dp),
                )
            }

            RantState.NothingRecorded -> Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.rant_nothing),
                    modifier = Modifier.padding(16.dp),
                )
            }

            else -> Unit
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.rant_note_hint)) },
            minLines = 3,
        )
        OutlinedButton(
            onClick = { viewModel.harvest(note); note = "" },
            enabled = note.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.rant_harvest)) }
    }
}
