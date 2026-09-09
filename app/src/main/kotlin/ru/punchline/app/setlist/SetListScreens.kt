package ru.punchline.app.setlist

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.model.Id
import ru.punchline.model.SetListIssue
import ru.punchline.model.SetListRole

@Composable
fun SetListsScreen(viewModel: SetListsViewModel, onOpen: (Id) -> Unit, onStage: (Id) -> Unit) {
    val lists by viewModel.all.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("5") }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setlist_new_title)) },
        )
        OutlinedTextField(
            value = minutes,
            onValueChange = { minutes = it.filter(Char::isDigit).take(3) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.setlist_new_minutes)) },
        )
        Button(
            onClick = {
                viewModel.create(title, minutes.toIntOrNull() ?: DEFAULT_MINUTES)
                title = ""
            },
            enabled = title.isNotBlank(),
        ) { Text(stringResource(R.string.setlist_create)) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(lists, key = { it.id.value }) { list ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(list.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = stringResource(
                                R.string.setlist_target,
                                list.targetDurationSec / 60,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row {
                            TextButton(onClick = { onOpen(list.id) }) {
                                Text(stringResource(R.string.setlist_edit))
                            }
                            TextButton(onClick = { onStage(list.id) }) {
                                Text(stringResource(R.string.setlist_stage))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Редактор сета. Замечания движка показываются рядом, но ничего не блокируют:
 * на сцену выходит автор, а не проверка.
 */
@Composable
fun SetListDetailScreen(viewModel: SetListDetailViewModel) {
    val card by viewModel.card.collectAsStateWithLifecycle()
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val current = card ?: return

    val plannedSec = current.setList.items.sumOf { it.plannedDurationSec ?: 0 }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.setlist_timing,
                    plannedSec / 60,
                    current.setList.targetDurationSec / 60,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(current.issues.toList()) { issue ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
            ) {
                Text(issue.text(), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        items(current.setList.items.sortedBy { it.order }, key = { it.id.value }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.setlist_position,
                            item.order + 1,
                            stringResource(item.role.labelRes()),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Row {
                        TextButton(onClick = { viewModel.move(item.id, -1) }) {
                            Text(stringResource(R.string.setlist_up))
                        }
                        TextButton(onClick = { viewModel.move(item.id, 1) }) {
                            Text(stringResource(R.string.setlist_down))
                        }
                        TextButton(onClick = { viewModel.setRole(item.id, item.role.next()) }) {
                            Text(stringResource(R.string.setlist_role))
                        }
                        TextButton(onClick = { viewModel.remove(item.id) }) {
                            Text(stringResource(R.string.setlist_remove))
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.setlist_candidates),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        if (candidates.isEmpty()) {
            item {
                // Пустой список без объяснения выглядит как поломка.
                // Причина всегда одна: материал ещё не дошёл до черновика.
                Text(
                    text = stringResource(R.string.setlist_no_candidates),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }

        items(candidates, key = { "cand-" + it.bit.id.value }) { candidate ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.add(candidate.bit.id) }
            ) {
                Text(candidate.bit.title, Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun SetListIssue.text(): String = when (this) {
    is SetListIssue.CallbackBeforeSource -> stringResource(R.string.issue_callback_order)
    is SetListIssue.SameTopicInARow -> stringResource(R.string.issue_same_topic)
    is SetListIssue.WeakCloser -> stringResource(R.string.issue_weak_closer)
    is SetListIssue.OverTime -> stringResource(R.string.issue_over_time, (plannedSec - targetSec) / 60)
    is SetListIssue.UnderTime -> stringResource(R.string.issue_under_time, (targetSec - plannedSec) / 60)
}

private fun SetListRole.labelRes(): Int = when (this) {
    SetListRole.OPENER -> R.string.role_opener
    SetListRole.BODY -> R.string.role_body
    SetListRole.CLOSER -> R.string.role_closer
    SetListRole.CALLBACK -> R.string.role_callback
}

/** Роль переключается по кругу: выпадающий список ради четырёх значений избыточен. */
private fun SetListRole.next(): SetListRole {
    val all = SetListRole.entries
    return all[(ordinal + 1) % all.size]
}

private const val DEFAULT_MINUTES = 5
