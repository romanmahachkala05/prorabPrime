package ru.prorabprime.feature.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.PendingMark
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.tasks.resources.Res
import ru.prorabprime.feature.tasks.resources.tasks_add
import ru.prorabprime.feature.tasks.resources.tasks_back
import ru.prorabprime.feature.tasks.resources.tasks_empty
import ru.prorabprime.feature.tasks.resources.tasks_hide_month
import ru.prorabprime.feature.tasks.resources.tasks_mark_done
import ru.prorabprime.feature.tasks.resources.tasks_mark_open
import ru.prorabprime.feature.tasks.resources.tasks_next_day
import ru.prorabprime.feature.tasks.resources.tasks_overdue
import ru.prorabprime.feature.tasks.resources.tasks_previous_day
import ru.prorabprime.feature.tasks.resources.tasks_show_month
import ru.prorabprime.feature.tasks.resources.tasks_title
import ru.prorabprime.feature.tasks.resources.tasks_today
import ru.prorabprime.ui.UiText

@Composable
fun TasksScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TasksViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasksContent(state, viewModel::onEvent, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TasksContent(
    state: TasksState,
    onEvent: (TasksEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.tasks_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.tasks_back))
                    }
                },
                actions = {
                    val monthOpen = state.view == TasksView.MONTH
                    IconButton(onClick = { onEvent(TasksEvent.MonthToggled) }) {
                        Icon(
                            ProrabIcons.CalendarMonth,
                            stringResource(if (monthOpen) Res.string.tasks_hide_month else Res.string.tasks_show_month),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onEvent(TaskEditorEvent.Add) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, stringResource(Res.string.tasks_add))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.view == TasksView.MONTH) MonthGrid(state, onEvent)
            DayBar(state, onEvent)
            when (val status = state.status) {
                TasksStatus.Content -> TaskList(state, onEvent)
                TasksStatus.Loading -> LoadingBox()
                is TasksStatus.Error -> ErrorMessage(status.message, onRetry = { onEvent(TasksEvent.Retry) })
            }
        }
    }
    state.editor?.let { TaskEditorDialog(it, onEvent) }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(TasksEvent.DialogConfirmed) },
        onDismiss = { onEvent(TasksEvent.DialogDismissed) },
    )
}

/** Yesterday, tomorrow, and a way back to today. */
@Composable
private fun DayBar(state: TasksState, onEvent: (TasksEvent) -> Unit) {
    // With the grid paged to another month, "today" is also the way back to this one.
    val monthAway = state.view == TasksView.MONTH && state.month != state.today.firstOfMonth()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(TasksEvent.PreviousDay) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(Res.string.tasks_previous_day))
        }
        Text(
            dayTitle(state.day, state.today),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (!state.isToday || monthAway) {
            TextButton(onClick = { onEvent(TasksEvent.TodayClicked) }) { Text(stringResource(Res.string.tasks_today)) }
        }
        IconButton(onClick = { onEvent(TasksEvent.NextDay) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(Res.string.tasks_next_day))
        }
    }
}

@Composable
private fun TaskList(state: TasksState, onEvent: (TasksEvent) -> Unit) {
    if (state.tasks.isEmpty() && state.overdue.isEmpty()) {
        EmptyDay(state.nextPlan) { onEvent(TasksEvent.DayPicked(it)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.overdue.isNotEmpty()) {
            item(key = "overdue-title") {
                Text(
                    stringResource(Res.string.tasks_overdue),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            items(state.overdue, key = { "overdue-" + it.id }) { TaskRow(it, onEvent) }
        }
        items(state.tasks, key = { it.id }) { TaskRow(it, onEvent) }
    }
}

@Composable
private fun TaskRow(task: TaskUi, onEvent: (TasksEvent) -> Unit) {
    val label = stringResource(if (task.task.done) Res.string.tasks_mark_open else Res.string.tasks_mark_done)
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    task.task.title,
                    textDecoration = if (task.task.done) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.task.isPending) PendingMark()
            }
        },
        supportingContent = listOfNotNull(task.dayLabel, task.time).takeIf { it.isNotEmpty() }?.let { parts ->
            { Text(parts.joinToString(" · ")) }
        },
        leadingContent = {
            Checkbox(
                checked = task.task.done,
                onCheckedChange = { onEvent(TasksEvent.DoneToggled(task.id)) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
        modifier = Modifier.clickable { onEvent(TaskEditorEvent.Edit(task.id)) },
    )
}
