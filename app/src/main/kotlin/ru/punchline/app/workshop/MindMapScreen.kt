package ru.punchline.app.workshop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import ru.punchline.data.repo.MindMapNode

/**
 * Дерево ассоциаций (упражнение 10). Аутлайн вместо свободного холста:
 * методике важно ветвление, а не расположение на плоскости, — и это
 * единственный способ уместить упражнение на телефонный экран.
 */
@Composable
fun MindMapScreen(viewModel: MindMapViewModel) {
    val nodes by viewModel.nodes.collectAsStateWithLifecycle()
    val attachTo by viewModel.attachTo.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }

    val attachTarget = nodes.firstOrNull { it.id == attachTo }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = attachTarget
                ?.let { stringResource(R.string.mindmap_branch_of, it.text) }
                ?: stringResource(R.string.mindmap_branch_root),
            style = MaterialTheme.typography.labelLarge,
        )

        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.mindmap_add_hint)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (attachTarget != null) {
                TextButton(onClick = { viewModel.attachTo(null) }) {
                    Text(stringResource(R.string.mindmap_to_root))
                }
            }
            TextButton(
                onClick = { viewModel.add(draft); draft = "" },
                enabled = draft.isNotBlank(),
            ) { Text(stringResource(R.string.mindmap_add_action)) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(nodes, key = { it.id.value }) { node ->
                NodeRow(
                    node = node,
                    onBranch = { viewModel.attachTo(node.id) },
                    onPromote = { viewModel.promote(node.id) },
                    onDelete = { viewModel.delete(node.id) },
                )
            }
        }
    }
}

@Composable
private fun NodeRow(
    node: MindMapNode,
    onBranch: () -> Unit,
    onPromote: () -> Unit,
    onDelete: () -> Unit,
) {
    val alreadyUsed = node.promotedBitId != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (node.depth * INDENT_DP).dp),
        colors = if (alreadyUsed) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(node.text, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onBranch) { Text(stringResource(R.string.mindmap_branch)) }
                if (alreadyUsed) {
                    Text(
                        text = stringResource(R.string.mindmap_already_bit),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                } else {
                    TextButton(onClick = onPromote) {
                        Text(stringResource(R.string.mindmap_promote))
                    }
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.mindmap_delete)) }
            }
        }
    }
}

private const val INDENT_DP = 16
