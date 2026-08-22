package ru.punchline.app.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.punchline.app.R
import ru.punchline.data.repo.BitCard
import ru.punchline.model.BitHint
import ru.punchline.model.BitStatus
import ru.punchline.model.Id

/** Доска материала — секция «Jokes in Progress» из тетради. */
@Composable
fun BoardScreen(viewModel: BoardViewModel, onOpenBit: (Id) -> Unit) {
    val cards by viewModel.inProgress.collectAsStateWithLifecycle()

    if (cards.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            Text(stringResource(R.string.board_empty))
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(cards, key = { it.bit.id.value }) { card ->
            BitRow(card, onClick = { onOpenBit(card.bit.id) })
        }
    }
}

@Composable
private fun BitRow(card: BitCard, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.bit.title, style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(card.bit.status.labelRes()),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (card.isScheduled) {
                    Text(
                        text = stringResource(R.string.board_scheduled),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            if (card.promotionOffered) {
                AssistChip(
                    onClick = onClick,
                    label = {
                        Text(
                            stringResource(
                                R.string.board_promotion,
                                stringResource(card.deservedStatus.labelRes()),
                            )
                        )
                    },
                )
            }

            card.hints.forEach { hint ->
                Text(
                    text = hint.text(),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BitHint.text(): String = when (this) {
    is BitHint.RewriteOrPark -> stringResource(R.string.hint_rewrite_or_park, silencesInARow)
    BitHint.MissingActOut -> stringResource(R.string.hint_missing_act_out)
    is BitHint.StuckInDraft -> stringResource(R.string.hint_stuck_in_draft, days)
    is BitHint.UnusedPolished -> stringResource(R.string.hint_unused_polished, days)
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
