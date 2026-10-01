package ru.prorabprime.feature.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.feature.expenses.resources.Res
import ru.prorabprime.feature.expenses.resources.expenses_add_title
import ru.prorabprime.feature.expenses.resources.expenses_amount_crew
import ru.prorabprime.feature.expenses.resources.expenses_amount_receipt
import ru.prorabprime.feature.expenses.resources.expenses_cancel
import ru.prorabprime.feature.expenses.resources.expenses_day_crew
import ru.prorabprime.feature.expenses.resources.expenses_day_receipt
import ru.prorabprime.feature.expenses.resources.expenses_kind_crew
import ru.prorabprime.feature.expenses.resources.expenses_kind_receipt
import ru.prorabprime.feature.expenses.resources.expenses_method
import ru.prorabprime.feature.expenses.resources.expenses_note
import ru.prorabprime.feature.expenses.resources.expenses_object_field
import ru.prorabprime.feature.expenses.resources.expenses_object_required
import ru.prorabprime.feature.expenses.resources.expenses_pick_day
import ru.prorabprime.feature.expenses.resources.expenses_pick_object
import ru.prorabprime.feature.expenses.resources.expenses_pick_picture
import ru.prorabprime.feature.expenses.resources.expenses_picture_chosen
import ru.prorabprime.feature.expenses.resources.expenses_picture_required
import ru.prorabprime.feature.expenses.resources.expenses_save

/** The form for an expense by hand: a payment to the crew, or a receipt from a picture file. */
@Composable
internal fun AddExpenseDialog(
    form: NewExpenseUi,
    objects: ImmutableList<ObjectChoiceUi>,
    onEvent: (ExpensesEvent) -> Unit,
) {
    val pickPicture = rememberImagePicker { onEvent(ExpenseFormEvent.ImagePicked(it)) }
    AlertDialog(
        onDismissRequest = { if (!form.isSaving) onEvent(ExpenseFormEvent.Dismiss) },
        title = { Text(stringResource(Res.string.expenses_add_title)) },
        text = {
            ExpenseFormFields(form, objects, onEvent, pickPicture)
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ExpenseFormEvent.Save) }, loading = form.isSaving) {
                Text(stringResource(Res.string.expenses_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ExpenseFormEvent.Dismiss) }, enabled = !form.isSaving) {
                Text(stringResource(Res.string.expenses_cancel))
            }
        },
    )
}

/** The fields of the form, which differ a little between a payment and a receipt. */
@Composable
private fun ExpenseFormFields(
    form: NewExpenseUi,
    objects: ImmutableList<ObjectChoiceUi>,
    onEvent: (ExpensesEvent) -> Unit,
    pickPicture: () -> Unit,
) {
    val isReceipt = form.kind == ExpenseKind.RECEIPT
    Column(
        modifier = Modifier.heightIn(max = FORM_MAX_HEIGHT).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ExpenseKind.entries.forEach { kind ->
                FilterChip(
                    selected = kind == form.kind,
                    onClick = { onEvent(ExpenseFormEvent.KindChanged(kind)) },
                    label = { Text(stringResource(kind.label)) },
                )
            }
        }
        ObjectPicker(form, objects, onEvent)
        TextInput(
            value = form.amountText,
            label = if (isReceipt) Res.string.expenses_amount_receipt else Res.string.expenses_amount_crew,
            problem = form.errors[ObjectField.PAYMENT_AMOUNT],
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        ) { onEvent(ExpenseFormEvent.AmountChanged(it)) }
        DayField(
            day = form.day,
            label = if (isReceipt) Res.string.expenses_day_receipt else Res.string.expenses_day_crew,
        ) { onEvent(ExpenseFormEvent.DayChanged(it)) }
        if (isReceipt) {
            PictureField(form, pickPicture)
        } else {
            MethodField(form.method) { onEvent(ExpenseFormEvent.MethodChanged(it)) }
        }
        TextInput(
            value = form.note,
            label = Res.string.expenses_note,
            problem = form.errors[ObjectField.PAYMENT_NOTE],
            keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = false,
        ) { onEvent(ExpenseFormEvent.NoteChanged(it)) }
    }
}

@Composable
private fun TextInput(
    value: String,
    label: StringResource,
    problem: FieldProblem?,
    keyboard: KeyboardOptions,
    singleLine: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.message)) } },
        singleLine = singleLine,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MethodField(selected: PaymentMethod, onChoose: (PaymentMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.expenses_method),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            PaymentMethod.entries.forEach { method ->
                FilterChip(
                    selected = method == selected,
                    onClick = { onChoose(method) },
                    label = { Text(stringResource(method.label)) },
                )
            }
        }
    }
}

/** The object the expense goes on, chosen from a menu under the button. */
@Composable
private fun ObjectPicker(
    form: NewExpenseUi,
    objects: ImmutableList<ObjectChoiceUi>,
    onEvent: (ExpensesEvent) -> Unit,
) {
    // Whether the menu is open is view state with no meaning beyond this field.
    var open by remember { mutableStateOf(false) }
    val chosen = objects.find { it.id == form.objectId }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.expenses_object_field),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                Text(chosen?.title ?: stringResource(Res.string.expenses_pick_object))
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                objects.forEach { choice ->
                    DropdownMenuItem(
                        text = { Text(choice.title) },
                        onClick = {
                            open = false
                            onEvent(ExpenseFormEvent.ObjectChosen(choice.id))
                        },
                    )
                }
            }
        }
        if (form.needsObject) Problem(Res.string.expenses_object_required)
    }
}

@Composable
private fun PictureField(form: NewExpenseUi, pick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(
                    if (form.image == null) Res.string.expenses_pick_picture else Res.string.expenses_picture_chosen,
                ),
            )
        }
        if (form.needsPicture) Problem(Res.string.expenses_picture_required)
    }
}

@Composable
private fun Problem(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** The day, with a date picker behind a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayField(
    day: LocalDay,
    label: StringResource,
    onPick: (LocalDay) -> Unit,
) {
    // Whether the picker is open is view state with no meaning beyond this field.
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(day.format()) }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDay.toUtcMillis(day))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(LocalDay.ofUtcMillis(it)) }
                    picking = false
                }) { Text(stringResource(Res.string.expenses_pick_day)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.expenses_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

private val ExpenseKind.label: StringResource
    get() = when (this) {
        ExpenseKind.CREW -> Res.string.expenses_kind_crew
        ExpenseKind.RECEIPT -> Res.string.expenses_kind_receipt
    }

private val FORM_MAX_HEIGHT = 480.dp
