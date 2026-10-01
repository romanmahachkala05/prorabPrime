package ru.prorabprime.feature.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_client
import ru.prorabprime.feature.finance.resources.finance_crew
import ru.prorabprime.feature.finance.resources.finance_history
import ru.prorabprime.feature.finance.resources.finance_history_empty

/** Every change to a payment, newest first; a deleted payment is still here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(revisions: ImmutableList<RevisionUi>, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.l)) {
            Text(stringResource(Res.string.finance_history), style = MaterialTheme.typography.titleMedium)
            if (revisions.isEmpty()) {
                Text(
                    stringResource(Res.string.finance_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
            LazyColumn(Modifier.heightIn(max = SHEET_MAX_HEIGHT)) {
                items(revisions, key = { it.id }) { revision ->
                    val side = stringResource(
                        if (revision.side == PaymentSide.CLIENT) Res.string.finance_client else Res.string.finance_crew,
                    )
                    ListItem(
                        headlineContent = { Text("${stringResource(revision.action.label)} · ${revision.amount}") },
                        supportingContent = {
                            Text(
                                listOfNotNull(side, revision.date, stringResource(revision.method.label), revision.note)
                                    .joinToString(" · "),
                            )
                        },
                    )
                }
            }
        }
    }
}

private val SHEET_MAX_HEIGHT = 420.dp
