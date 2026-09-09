package ru.punchline.app.review

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.data.repo.Promotion
import ru.punchline.model.BitStatus
import ru.punchline.model.LaughResult

/**
 * Разбор выступления. Каждая шутка отмечается одним тапом; когда все отмечены,
 * движок пересобирает акт по данным и говорит, что именно изменилось.
 */
@Composable
fun GigReviewScreen(viewModel: GigReviewViewModel, onDone: () -> Unit) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val promotions by viewModel.promotions.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val (marked, total) = progress

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.review_progress, marked, total),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.review_stats,
                        String.format("%.1f", stats.averageScore),
                        String.format("%.1f", stats.laughsPerMinute),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        items(rows, key = { it.bitId.value }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title, style = MaterialTheme.typography.bodyLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        LaughResult.entries.forEach { result ->
                            FilterChip(
                                selected = row.marked == result,
                                onClick = { viewModel.mark(row.bitId, result) },
                                label = { Text(stringResource(result.labelRes())) },
                            )
                        }
                    }
                }
            }
        }

        if (promotions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.review_promotions),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        promotions.forEach { Text(it.line()) }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::applyPromotions,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = marked > 0,
                ) { Text(stringResource(R.string.review_apply)) }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.review_done))
                }
            }
        }
    }
}

@Composable
private fun Promotion.line(): String =
    stringResource(R.string.review_promotion_line, title, stringResource(to.labelRes()))

private fun LaughResult.labelRes(): Int = when (this) {
    LaughResult.SILENCE -> R.string.laugh_silence
    LaughResult.CHUCKLE -> R.string.laugh_chuckle
    LaughResult.LAUGH -> R.string.laugh_laugh
    LaughResult.BIG_LAUGH -> R.string.laugh_big
    LaughResult.APPLAUSE_BREAK -> R.string.laugh_applause
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
