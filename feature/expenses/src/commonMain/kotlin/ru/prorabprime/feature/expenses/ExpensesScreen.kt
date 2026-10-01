package ru.prorabprime.feature.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.theme.Corners
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.expenses.resources.Res
import ru.prorabprime.feature.expenses.resources.expenses_all
import ru.prorabprime.feature.expenses.resources.expenses_back
import ru.prorabprime.feature.expenses.resources.expenses_by_month
import ru.prorabprime.feature.expenses.resources.expenses_by_object
import ru.prorabprime.feature.expenses.resources.expenses_crew
import ru.prorabprime.feature.expenses.resources.expenses_empty
import ru.prorabprime.feature.expenses.resources.expenses_entries
import ru.prorabprime.feature.expenses.resources.expenses_filter_all
import ru.prorabprime.feature.expenses.resources.expenses_filter_crew
import ru.prorabprime.feature.expenses.resources.expenses_filter_materials
import ru.prorabprime.feature.expenses.resources.expenses_kind_crew
import ru.prorabprime.feature.expenses.resources.expenses_kind_receipt
import ru.prorabprime.feature.expenses.resources.expenses_materials
import ru.prorabprime.feature.expenses.resources.expenses_no_amount
import ru.prorabprime.feature.expenses.resources.expenses_this_month
import ru.prorabprime.feature.expenses.resources.expenses_title
import ru.prorabprime.feature.expenses.resources.expenses_total
import ru.prorabprime.feature.expenses.resources.expenses_without_amount
import ru.prorabprime.ui.UiText

/** What was spent on every object: receipts and payments to the crew, with the totals. */
@Composable
fun ExpensesScreen(
    onOpenReceipt: (objectId: String, photoId: String) -> Unit,
    onOpenObject: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExpensesViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExpensesContent(state, viewModel::selectFilter, onOpenReceipt, onOpenObject, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpensesContent(
    state: ExpensesState,
    onFilter: (ExpenseFilter) -> Unit,
    onOpenReceipt: (objectId: String, photoId: String) -> Unit,
    onOpenObject: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.expenses_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.expenses_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val status = state.status) {
                ExpensesStatus.Loading -> LoadingBox()

                is ExpensesStatus.Error -> ErrorMessage(status.message, onRetry = onBack)

                ExpensesStatus.Content ->
                    if (state.allRows == 0) {
                        EmptyMessage(UiText.Resource(Res.string.expenses_empty))
                    } else {
                        Report(state, onFilter, onOpenReceipt, onOpenObject)
                    }
            }
        }
    }
}

@Composable
private fun Report(
    state: ExpensesState,
    onFilter: (ExpenseFilter) -> Unit,
    onOpenReceipt: (String, String) -> Unit,
    onOpenObject: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "summary") { Summary(state.summary) }
        if (state.byObject.isNotEmpty()) {
            item(key = "by-object-title") { SectionTitle(Res.string.expenses_by_object) }
            items(state.byObject, key = { "object:" + it.key }) { bar ->
                BarLine(bar, onClick = bar.objectId?.let { id -> { onOpenObject(id) } })
            }
        }
        if (state.byMonth.isNotEmpty()) {
            item(key = "by-month-title") { SectionTitle(Res.string.expenses_by_month) }
            items(state.byMonth, key = { "month:" + it.key }) { BarLine(it, onClick = null) }
        }
        item(key = "all-title") { SectionTitle(Res.string.expenses_all) }
        item(key = "filter") { Filters(state.filter, onFilter) }
        items(state.rows, key = { "row:" + it.id }) { row ->
            ExpenseRow(row) {
                if (row.photoId != null) onOpenReceipt(row.objectId, row.photoId) else onOpenObject(row.objectId)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun Summary(summary: SummaryUi) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatCard(Res.string.expenses_total, summary.total, Modifier.weight(1f))
            StatCard(Res.string.expenses_this_month, summary.thisMonth, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StatCard(Res.string.expenses_materials, summary.materials, Modifier.weight(1f))
            StatCard(Res.string.expenses_crew, summary.crew, Modifier.weight(1f))
        }
        if (summary.withoutAmount > 0) {
            Text(
                stringResource(Res.string.expenses_without_amount, summary.withoutAmount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: StringResource,
    value: String,
    modifier: Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Corners.m),
        modifier = modifier,
    ) {
        Column(Modifier.padding(Spacing.m)) {
            Text(
                stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SectionTitle(title: StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.m),
    )
}

/** A name and a sum, with a bar under them as long as the sum's share of the largest. */
@Composable
private fun BarLine(bar: BarUi, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bar.label, style = MaterialTheme.typography.bodyLarge)
                bar.detail?.let {
                    Text(
                        stringResource(Res.string.expenses_entries, it.toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(bar.value, style = MaterialTheme.typography.titleSmall)
        }
        Box(
            Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(BAR_HEIGHT)
                .clip(RoundedCornerShape(BAR_HEIGHT))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bar.fraction.coerceIn(0f, 1f))
                    .height(BAR_HEIGHT)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun Filters(selected: ExpenseFilter, onFilter: (ExpenseFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        listOf(
            ExpenseFilter.ALL to Res.string.expenses_filter_all,
            ExpenseFilter.MATERIALS to Res.string.expenses_filter_materials,
            ExpenseFilter.CREW to Res.string.expenses_filter_crew,
        ).forEach { (filter, label) ->
            FilterChip(
                selected = selected == filter,
                onClick = { onFilter(filter) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun ExpenseRow(row: ExpenseRowUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(row.objectTitle, style = MaterialTheme.typography.bodyLarge)
            val kind =
                stringResource(if (row.isReceipt) Res.string.expenses_kind_receipt else Res.string.expenses_kind_crew)
            Text(
                "${row.day} · $kind",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (row.amount != null) {
            Text(row.amount, style = MaterialTheme.typography.titleSmall)
        } else {
            Text(
                stringResource(Res.string.expenses_no_amount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private val BAR_HEIGHT = 6.dp
