package ru.prorabprime.feature.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.PendingTitle
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_add_extra
import ru.prorabprime.feature.finance.resources.finance_add_payment
import ru.prorabprime.feature.finance.resources.finance_agreed
import ru.prorabprime.feature.finance.resources.finance_edit_totals
import ru.prorabprime.feature.finance.resources.finance_extras
import ru.prorabprime.feature.finance.resources.finance_extras_total_agreed
import ru.prorabprime.feature.finance.resources.finance_extras_total_pending
import ru.prorabprime.feature.finance.resources.finance_no_extras
import ru.prorabprime.feature.finance.resources.finance_no_payments
import ru.prorabprime.feature.finance.resources.finance_not_set
import ru.prorabprime.feature.finance.resources.finance_overpaid
import ru.prorabprime.feature.finance.resources.finance_remaining

/** One side of the books: the agreed amount, what moved, what is left, and its payments. */
@Composable
internal fun SideSection(
    title: StringResource,
    paidLabel: StringResource,
    card: SideCardUi,
    payments: ImmutableList<PaymentUi>,
    onEditTotals: () -> Unit,
    onAddPayment: () -> Unit,
    onOpenPayment: (PaymentUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AmountLine(Res.string.finance_agreed, card.agreed ?: stringResource(Res.string.finance_not_set))
                AmountLine(paidLabel, card.paid)
                card.remaining?.let {
                    AmountLine(
                        if (card.isOverpaid) Res.string.finance_overpaid else Res.string.finance_remaining,
                        it,
                        emphasized = true,
                    )
                }
                OutlinedButton(onClick = onEditTotals) { Text(stringResource(Res.string.finance_edit_totals)) }
            }
        }
        if (payments.isEmpty()) {
            Text(
                stringResource(Res.string.finance_no_payments),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        payments.forEach { payment ->
            ListItem(
                headlineContent = { PendingTitle(payment.amount, payment.payment.isPending) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            payment.date,
                            stringResource(payment.payment.method.label),
                            payment.payment.note,
                        ).joinToString(" · "),
                    )
                },
                modifier = Modifier.clickable { onOpenPayment(payment) },
            )
        }
        OutlinedButton(onClick = onAddPayment) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(stringResource(Res.string.finance_add_payment))
        }
    }
}

@Composable
private fun AmountLine(
    label: StringResource,
    value: String,
    emphasized: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The extra works table: what, how much, and whether the client agreed to it. */
@Composable
internal fun ExtraWorksSection(
    finance: FinanceUi,
    onAdd: () -> Unit,
    onOpen: (ExtraWorkUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.finance_extras), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, stringResource(Res.string.finance_add_extra))
            }
        }
        if (finance.extraWorks.isEmpty()) {
            Text(
                stringResource(Res.string.finance_no_extras),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        finance.extraWorks.forEach { work ->
            ListItem(
                headlineContent = { PendingTitle(work.work.title, work.work.isPending) },
                supportingContent = { Text(stringResource(work.work.status.label)) },
                trailingContent = { Text(work.amount) },
                modifier = Modifier.clickable { onOpen(work) },
            )
        }
        Text(
            stringResource(Res.string.finance_extras_total_agreed, finance.extrasAgreed),
            style = MaterialTheme.typography.bodyMedium,
        )
        finance.extrasPending?.let {
            Text(
                stringResource(Res.string.finance_extras_total_pending, it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
