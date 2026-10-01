package ru.prorabprime.feature.tasks

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.Task
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface TasksStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : TasksStatus

    data object Loading : TasksStatus

    data class Error(
        val message: UiText,
    ) : TasksStatus
}

/** A task with what the list shows of it already formatted; the model stays for opening the form. */
@Immutable
internal data class TaskUi(
    val task: Task,
    /** `09:30`, or null for a task without a reminder. */
    val time: String?,
    /** `25.09.2026` for a task of another day than the one shown, null for one of that day. */
    val dayLabel: String?,
) {
    val id: String get() = task.id.value
}

/** The form open over the screen: a new task when [taskId] is null. [minutes] is the reminder time. */
@Immutable
internal data class TaskEditorUi(
    val taskId: String? = null,
    val title: String = "",
    val day: LocalDay,
    val minutes: Int? = null,
    val done: Boolean = false,
    val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
    val isSaving: Boolean = false,
)

/** One day on screen, or a month grid above it. */
internal enum class TasksView {
    DAY,
    MONTH,
}

/** What the month grid shows under a day: how many of its tasks are open and how many are done. */
@Immutable
internal data class DayMarkUi(
    val open: Int,
    val done: Int,
)

@Immutable
internal data class TasksState(
    val status: TasksStatus = TasksStatus.Loading,
    /** The day on screen. */
    val day: LocalDay,
    val today: LocalDay,
    val tasks: ImmutableList<TaskUi> = persistentListOf(),
    /** Open tasks of earlier days; shown only on today. */
    val overdue: ImmutableList<TaskUi> = persistentListOf(),
    val editor: TaskEditorUi? = null,
    val dialog: DialogModel? = null,
    val pendingDeleteId: String? = null,
    val view: TasksView = TasksView.DAY,
    /** The first day of the month the grid shows; it always holds [day] while the grid is open. */
    val month: LocalDay = day.firstOfMonth(),
    val marks: ImmutableMap<LocalDay, DayMarkUi> = persistentMapOf(),
) {
    val isToday: Boolean get() = day == today
}

internal sealed interface TasksEvent {
    data object Retry : TasksEvent

    data object PreviousDay : TasksEvent

    data object NextDay : TasksEvent

    data object TodayClicked : TasksEvent

    /** The calendar button: opens the month grid above the day, or closes it. */
    data object MonthToggled : TasksEvent

    data object PreviousMonth : TasksEvent

    data object NextMonth : TasksEvent

    /** A tap on a day of the grid. */
    data class DayPicked(
        val day: LocalDay,
    ) : TasksEvent

    /** A tick on a task's checkbox. */
    data class DoneToggled(
        val taskId: String,
    ) : TasksEvent

    data object DialogConfirmed : TasksEvent

    data object DialogDismissed : TasksEvent
}

/** Every event of the task form. */
internal sealed interface TaskEditorEvent : TasksEvent {
    data object Add : TaskEditorEvent

    data class Edit(
        val taskId: String,
    ) : TaskEditorEvent

    data class TitleChanged(
        val text: String,
    ) : TaskEditorEvent

    data class DayChanged(
        val day: LocalDay,
    ) : TaskEditorEvent

    /** Null takes the reminder away. */
    data class TimeChanged(
        val minutes: Int?,
    ) : TaskEditorEvent

    data object Save : TaskEditorEvent

    data class Delete(
        val taskId: String,
    ) : TaskEditorEvent

    data object Dismiss : TaskEditorEvent
}
