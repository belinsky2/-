package ru.punchline.app.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
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
import ru.punchline.data.repo.ExerciseCard

/**
 * Практика: 48 упражнений тетради.
 *
 * Формулировок из книги здесь нет — только номер, ярлык и страница.
 * Пользователь читает упражнение в книге, а сюда пишет свой ответ.
 */
@Composable
fun PracticeScreen(viewModel: PracticeViewModel) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val (done, total) by rememberProgress(viewModel)

    Column(modifier = Modifier.fillMaxSize()) {
        if (total > 0) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.practice_progress, done, total),
                    style = MaterialTheme.typography.labelLarge,
                )
                LinearProgressIndicator(
                    progress = { done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { section ->
                item(key = "part-${section.part}") {
                    Text(
                        text = stringResource(section.part.labelRes()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.exercises, key = { it.number }) { card ->
                    ExerciseRow(card, viewModel)
                }
            }
        }
    }
}

@Composable
private fun rememberProgress(viewModel: PracticeViewModel): androidx.compose.runtime.State<Pair<Int, Int>> =
    viewModel.progress.collectAsStateWithLifecycle()

@Composable
private fun ExerciseRow(card: ExerciseCard, viewModel: PracticeViewModel) {
    var expanded by remember(card.number) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = card.isDone, onCheckedChange = { viewModel.toggleDone(card) })
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (card.needsTitle) {
                            stringResource(R.string.practice_untitled, card.number)
                        } else {
                            stringResource(R.string.practice_numbered, card.number, card.title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                    card.bookPage?.let {
                        Text(
                            text = stringResource(R.string.practice_page, it),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (card.needsTitle) TitleEditor(card, viewModel)
                    AnswerEditor(card, viewModel)
                }
            }
        }
    }
}

/**
 * Ввод названия для семи упражнений, которых не удалось восстановить.
 * После правки запись помечается пользовательской и обновление приложения
 * её не затирает.
 */
@Composable
private fun TitleEditor(card: ExerciseCard, viewModel: PracticeViewModel) {
    var draft by remember(card.number) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.practice_title_missing),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.practice_title_hint)) },
        )
        TextButton(onClick = { viewModel.rename(card, draft) }, enabled = draft.isNotBlank()) {
            Text(stringResource(R.string.practice_title_save))
        }
    }
}

@Composable
private fun AnswerEditor(card: ExerciseCard, viewModel: PracticeViewModel) {
    var answer by remember(card.number) { mutableStateOf(card.answers[FIELD_ANSWER].orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = answer,
            onValueChange = { answer = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.practice_answer_hint)) },
            minLines = 4,
        )
        TextButton(onClick = { viewModel.saveAnswer(card, FIELD_ANSWER, answer) }) {
            Text(stringResource(R.string.practice_answer_save))
        }
    }
}

private const val FIELD_ANSWER = "answer"

private fun String.labelRes(): Int = when (this) {
    "intro" -> R.string.part_intro
    "one" -> R.string.part_one
    "two" -> R.string.part_two
    "three" -> R.string.part_three
    else -> R.string.part_four
}
