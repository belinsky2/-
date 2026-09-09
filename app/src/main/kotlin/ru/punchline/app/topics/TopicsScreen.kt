package ru.punchline.app.topics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import ru.punchline.model.Id
import ru.punchline.model.Topic

/**
 * Темы. Упражнения 8 и 9: сначала набросать список без фильтра, потом честно
 * оценить, где внутри действительно есть злость, и оставить три главные.
 */
@Composable
fun TopicsScreen(
    viewModel: TopicsViewModel,
    onOpenMindMap: (Id) -> Unit,
    onOpenRant: (Id) -> Unit,
) {
    val topics by viewModel.all.collectAsStateWithLifecycle()
    val overLimit by viewModel.overLimit.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.topics_add_hint)) },
        )
        TextButton(
            onClick = { viewModel.add(draft); draft = "" },
            enabled = draft.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) { Text(stringResource(R.string.topics_add_action)) }

        if (overLimit) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.topics_over_limit, viewModel.coreLimit),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = viewModel::dismissWarning) {
                        Text(stringResource(R.string.topics_over_limit_ok))
                    }
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(topics, key = { it.id.value }) { topic ->
                TopicCard(
                    topic = topic,
                    onPassion = { viewModel.setPassion(topic.id, it) },
                    onToggleCore = { viewModel.toggleCore(topic) },
                    onOpenMindMap = { onOpenMindMap(topic.id) },
                    onOpenRant = { onOpenRant(topic.id) },
                )
            }
        }
    }
}

@Composable
private fun TopicCard(
    topic: Topic,
    onPassion: (Int) -> Unit,
    onToggleCore: () -> Unit,
    onOpenMindMap: () -> Unit,
    onOpenRant: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(topic.title, style = MaterialTheme.typography.titleMedium)

            Text(
                text = stringResource(R.string.topics_passion, topic.passionScore),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = topic.passionScore.toFloat(),
                onValueChange = { onPassion(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = topic.isCore,
                    onClick = onToggleCore,
                    label = { Text(stringResource(R.string.topics_core)) },
                )
                TextButton(onClick = onOpenMindMap) {
                    Text(stringResource(R.string.topics_mind_map))
                }
                TextButton(onClick = onOpenRant) {
                    Text(stringResource(R.string.topics_rant))
                }
            }
        }
    }
}
