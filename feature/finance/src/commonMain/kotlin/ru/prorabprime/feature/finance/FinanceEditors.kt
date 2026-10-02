package ru.prorabprime.feature.finance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_add_extra
import ru.prorabprime.feature.finance.resources.finance_add_payment
import ru.prorabprime.feature.finance.resources.finance_cancel
import ru.prorabprime.feature.finance.resources.finance_delete
import ru.prorabprime.feature.finance.resources.finance_edit_extra
import ru.prorabprime.feature.finance.resources.finance_edit_payment
import ru.prorabprime.feature.finance.resources.finance_extra_amount
import ru.prorabprime.feature.finance.resources.finance_extra_status
import ru.prorabprime.feature.finance.resources.finance_extra_title
import ru.prorabprime.feature.finance.resources.finance_payment_amount
import ru.prorabprime.feature.finance.resources.finance_payment_date
import ru.prorabprime.feature.finance.resources.finance_payment_method
import ru.prorabprime.feature.finance.resources.finance_payment_note
import ru.prorabprime.feature.finance.resources.finance_pick_date
import ru.prorabprime.feature.finance.resources.finance_save
import ru.prorabprime.feature.finance.resources.finance_total_client
import ru.prorabprime.feature.finance.resources.finance_total_crew
import ru.prorabprime.feature.finance.resources.finance_totals_title

/** The one form that is open, if any. */
@Composable
internal fun FinanceEditorHost(editor: FinanceEditorUi?, onEvent: (FinanceEvent) -> Unit) {
    when (editor) {
        is FinanceEditorUi.Payment -> PaymentDialog(editor, onEvent)
        is FinanceEditorUi.Work -> WorkDialog(editor, onEvent)
        is FinanceEditorUi.Terms -> TermsDialog(editor, onEvent)
        null -> Unit
    }
}

@Composable
private fun PaymentDialog(editor: FinanceEditorUi.Payment, onEvent: (FinanceEvent) -> Unit) {
    val title = if (editor.paymentId == null) Res.string.finance_add_payment else Res.string.finance_edit_payment
    FormDialog(
        title = title,
        isSaving = editor.isSaving,
        onSave = { onEvent(PaymentEvent.Save) },
        onDismiss = { onEvent(PaymentEvent.Dismiss) },
        onDelete = editor.paymentId?.let { id -> { onEvent(PaymentEvent.Delete(id)) } },
    ) {
        AmountField(
            value = editor.amountText,
            label = Res.string.finance_payment_amount,
            problem = editor.errors[ObjectField.PAYMENT_AMOUNT],
            onChange = { onEvent(PaymentEvent.AmountChanged(it)) },
        )
        DateField(editor.day, editor.errors[ObjectField.PAYMENT_DATE]) { onEvent(PaymentEvent.DayChanged(it)) }
        Label(Res.string.finance_payment_method)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = method == editor.method,
                    onClick = { onEvent(PaymentEvent.MethodChanged(method)) },
                    label = { Text(stringResource(method.label)) },
                )
            }
        }
        TextField(
            value = editor.note,
            label = Res.string.finance_payment_note,
            problem = editor.errors[ObjectField.PAYMENT_NOTE],
            onChange = { onEvent(PaymentEvent.NoteChanged(it)) },
        )
    }
}

@Composable
private fun WorkDialog(editor: FinanceEditorUi.Work, onEvent: (FinanceEvent) -> Unit) {
    val title = if (editor.workId == null) Res.string.finance_add_extra else Res.string.finance_edit_extra
    FormDialog(
        title = title,
        isSaving = editor.isSaving,
        onSave = { onEvent(WorkEvent.Save) },
        onDismiss = { onEvent(WorkEvent.Dismiss) },
        onDelete = editor.workId?.let { id -> { onEvent(WorkEvent.Delete(id)) } },
    ) {
        TextField(
            value = editor.title,
            label = Res.string.finance_extra_title,
            problem = editor.errors[ObjectField.WORK_TITLE],
            onChange = { onEvent(WorkEvent.TitleChanged(it)) },
        )
        AmountField(
            value = editor.amountText,
            label = Res.string.finance_extra_amount,
            problem = editor.errors[ObjectField.WORK_AMOUNT],
            onChange = { onEvent(WorkEvent.AmountChanged(it)) },
        )
        Label(Res.string.finance_extra_status)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ExtraWorkStatus.entries.forEach { status ->
                FilterChip(
                    selected = status == editor.status,
                    onClick = { onEvent(WorkEvent.StatusChanged(status)) },
                    label = { Text(stringResource(status.label)) },
                )
            }
        }
    }
}

@Composable
private fun TermsDialog(editor: FinanceEditorUi.Terms, onEvent: (FinanceEvent) -> Unit) {
    val problem = editor.errors[ObjectField.TOTAL_AMOUNT]
    FormDialog(
        title = Res.string.finance_totals_title,
        isSaving = editor.isSaving,
        onSave = { onEvent(TermsEvent.Save) },
        onDismiss = { onEvent(TermsEvent.Dismiss) },
        onDelete = null,
    ) {
        AmountField(editor.clientText, Res.string.finance_total_client, problem) {
            onEvent(TermsEvent.ClientChanged(it))
        }
        AmountField(editor.crewText, Res.string.finance_total_crew, problem) { onEvent(TermsEvent.CrewChanged(it)) }
    }
}

@Composable
private fun FormDialog(
    title: StringResource,
    isSaving: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text(stringResource(title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = FORM_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) { content() }
        },
        confirmButton = {
            TextButton(onClick = onSave, loading = isSaving) { Text(stringResource(Res.string.finance_save)) }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it, enabled = !isSaving) {
                        Text(stringResource(Res.string.finance_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(Res.string.finance_cancel)) }
            }
        },
    )
}

@Composable
private fun Label(label: StringResource) {
    Text(
        stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AmountField(
    value: String,
    label: StringResource,
    problem: FieldProblem?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.message)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextField(
    value: String,
    label: StringResource,
    problem: FieldProblem?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.message)) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A field that shows the day and opens a date picker when tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    day: LocalDay?,
    problem: FieldProblem?,
    onPick: (LocalDay) -> Unit,
) {
    // Whether the picker is open is view state with no meaning beyond this field.
    var picking by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = day?.format().orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.finance_payment_date)) },
            isError = problem != null,
            supportingText = problem?.let { { Text(stringResource(it.message)) } },
            modifier = Modifier.fillMaxWidth().clickable { picking = true },
            enabled = false,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledSupportingTextColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
    if (picking) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = day?.let(LocalDay::toUtcMillis))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(LocalDay.ofUtcMillis(it)) }
                    picking = false
                }) { Text(stringResource(Res.string.finance_pick_date)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.finance_cancel)) }
            },
        ) { DatePicker(state = pickerState) }
    }
}

private val FORM_MAX_HEIGHT = 420.dp
