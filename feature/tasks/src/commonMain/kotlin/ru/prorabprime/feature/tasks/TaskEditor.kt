package ru.prorabprime.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.feature.tasks.resources.Res
import ru.prorabprime.feature.tasks.resources.tasks_add
import ru.prorabprime.feature.tasks.resources.tasks_cancel
import ru.prorabprime.feature.tasks.resources.tasks_clear_time
import ru.prorabprime.feature.tasks.resources.tasks_day_field
import ru.prorabprime.feature.tasks.resources.tasks_delete
import ru.prorabprime.feature.tasks.resources.tasks_edit
import ru.prorabprime.feature.tasks.resources.tasks_no_reminder
import ru.prorabprime.feature.tasks.resources.tasks_ok
import ru.prorabprime.feature.tasks.resources.tasks_pick_day
import ru.prorabprime.feature.tasks.resources.tasks_pick_time
import ru.prorabprime.feature.tasks.resources.tasks_save
import ru.prorabprime.feature.tasks.resources.tasks_time_field
import ru.prorabprime.feature.tasks.resources.tasks_title_field

@Composable
internal fun TaskEditorDialog(editor: TaskEditorUi, onEvent: (TasksEvent) -> Unit) {
    val title = if (editor.taskId == null) Res.string.tasks_add else Res.string.tasks_edit
    AlertDialog(
        onDismissRequest = { onEvent(TaskEditorEvent.Dismiss) },
        title = { Text(stringResource(title)) },
        text = { TaskForm(editor, onEvent) },
        confirmButton = {
            TextButton(onClick = { onEvent(TaskEditorEvent.Save) }, enabled = !editor.isSaving) {
                Text(stringResource(Res.string.tasks_save))
            }
        },
        dismissButton = {
            Row {
                editor.taskId?.let { id ->
                    TextButton(onClick = { onEvent(TaskEditorEvent.Delete(id)) }) {
                        Text(stringResource(Res.string.tasks_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { onEvent(TaskEditorEvent.Dismiss) }) {
                    Text(stringResource(Res.string.tasks_cancel))
                }
            }
        },
    )
}

@Composable
private fun TaskForm(editor: TaskEditorUi, onEvent: (TasksEvent) -> Unit) {
    val problem = editor.errors[ObjectField.TASK_TITLE]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = editor.title,
            onValueChange = { onEvent(TaskEditorEvent.TitleChanged(it)) },
            label = { Text(stringResource(Res.string.tasks_title_field)) },
            isError = problem != null,
            supportingText = problem?.let { { Text(stringResource(it.message)) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        DayRow(editor.day) { onEvent(TaskEditorEvent.DayChanged(it)) }
        TimeRow(editor.minutes) { onEvent(TaskEditorEvent.TimeChanged(it)) }
    }
}

/** The day, with a date picker behind a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayRow(day: LocalDay, onPick: (LocalDay) -> Unit) {
    // Whether the picker is open is view state with no meaning beyond this row.
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.tasks_day_field),
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
                }) { Text(stringResource(Res.string.tasks_pick_day)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.tasks_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** The reminder time: none, or the picked one, with a time picker behind a tap. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(minutes: Int?, onChange: (Int?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.tasks_time_field),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { picking = true }) {
                Text(minutes?.let(::formatMinutes) ?: stringResource(Res.string.tasks_pick_time))
            }
            if (minutes != null) {
                TextButton(onClick = { onChange(null) }) { Text(stringResource(Res.string.tasks_clear_time)) }
            } else {
                Text(
                    stringResource(Res.string.tasks_no_reminder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (picking) {
        val initial = minutes ?: DEFAULT_REMINDER_MINUTES
        val state = rememberTimePickerState(initial / MINUTES_IN_HOUR, initial % MINUTES_IN_HOUR, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.hour * MINUTES_IN_HOUR + state.minute)
                    picking = false
                }) { Text(stringResource(Res.string.tasks_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(Res.string.tasks_cancel)) }
            },
        )
    }
}

private const val DEFAULT_REMINDER_MINUTES = 9 * 60
private const val MINUTES_IN_HOUR = 60
