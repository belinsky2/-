package ru.punchline.app.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.model.Bit

/**
 * Входящие. Единственная задача экрана — чтобы идея, пойманная на ходу,
 * попадала внутрь за секунды и не терялась. Разбор на премису и панч —
 * позже, в мастерской.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel, onOpenBackup: () -> Unit) {
    val items by viewModel.inbox.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inbox_title)) },
                actions = {
                    IconButton(onClick = onOpenBackup) {
                        Text(stringResource(R.string.inbox_open_backup_icon))
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaptureField(
                value = draft,
                onValueChange = { draft = it },
                onSubmit = {
                    viewModel.capture(draft)
                    draft = ""
                },
            )

            if (items.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id.value }) { SeedCard(it) }
                }
            }
        }
    }
}

@Composable
private fun CaptureField(value: String, onValueChange: (String) -> Unit, onSubmit: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.inbox_capture_hint)) },
            minLines = 2,
        )
        TextButton(
            onClick = onSubmit,
            enabled = value.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.inbox_capture_action))
        }
    }
}

@Composable
private fun SeedCard(bit: Bit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = bit.title,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.inbox_empty),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}
