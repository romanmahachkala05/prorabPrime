package ru.prorabprime.feature.tasks

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface ITasksStateHolder : StateOwner<TasksState> {
    fun showTasks(tasks: ImmutableList<TaskUi>, overdue: ImmutableList<TaskUi>)

    fun showLoading()

    fun showError(message: UiText)

    /** Moves to [day]; what was shown stays until the new day's tasks arrive. */
    fun showDay(day: LocalDay)

    fun openEditor(editor: TaskEditorUi)

    /** Applies [transform] to the open form; does nothing when none is open. */
    fun editForm(transform: (TaskEditorUi) -> TaskEditorUi)

    fun closeEditor()

    fun askToDelete(dialog: DialogModel, taskId: String)

    /** Closes the dialog and forgets what it would have deleted. */
    fun dismissDialog()
}

internal class TasksStateHolder(
    today: LocalDay,
) : ITasksStateHolder {

    private val _state = MutableStateFlow(TasksState(day = today, today = today))
    override val state: StateFlow<TasksState> = _state.asStateFlow()

    override fun showTasks(tasks: ImmutableList<TaskUi>, overdue: ImmutableList<TaskUi>) = _state.update {
        it.copy(status = TasksStatus.Content, tasks = tasks, overdue = overdue)
    }

    override fun showLoading() = _state.update { it.copy(status = TasksStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = TasksStatus.Error(message)) }

    override fun showDay(day: LocalDay) = _state.update { it.copy(day = day) }

    override fun openEditor(editor: TaskEditorUi) = _state.update { it.copy(editor = editor) }

    override fun editForm(transform: (TaskEditorUi) -> TaskEditorUi) = _state.update { state ->
        state.copy(editor = state.editor?.let(transform))
    }

    override fun closeEditor() = _state.update { it.copy(editor = null) }

    override fun askToDelete(dialog: DialogModel, taskId: String) = _state.update {
        it.copy(dialog = dialog, pendingDeleteId = taskId)
    }

    override fun dismissDialog() = _state.update { it.copy(dialog = null, pendingDeleteId = null) }
}
