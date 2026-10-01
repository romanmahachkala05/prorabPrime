package ru.prorabprime.feature.tasks

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.usecase.DeleteTaskUseCase
import ru.prorabprime.domain.usecase.ObserveDayTasksUseCase
import ru.prorabprime.domain.usecase.ObserveOverdueTasksUseCase
import ru.prorabprime.domain.usecase.ObserveTasksRangeUseCase
import ru.prorabprime.domain.usecase.SaveTaskUseCase
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.FakeTasksRepository
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aTask
import ru.prorabprime.ui.toUiText

class TasksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTasksRepository()
    private val notifier = FakeSnackbarNotifier()
    private val today = LocalDay.of(2026, 9, 25)

    private val viewModel by lazy {
        val holder = TasksStateHolder(today)
        TasksViewModel(
            stateHolder = holder,
            errorHandler = TasksErrorHandler(holder, notifier),
            actions = TasksActions(
                observeDay = ObserveDayTasksUseCase(repository),
                observeOverdue = ObserveOverdueTasksUseCase(repository),
                observeRange = ObserveTasksRangeUseCase(repository),
                saveTask = SaveTaskUseCase(repository),
                deleteTask = DeleteTaskUseCase(repository),
            ),
        )
    }

    private val state get() = viewModel.state.value

    private fun withTasks() {
        repository.tasks.value = listOf(
            aTask("a", "Купить плитку", today, remindAtMinutes = 9 * 60 + 5),
            aTask("b", "Позвонить", today, done = true),
            aTask("old", "Забыл вчера", today.plusDays(-1)),
            aTask("tomorrow", "Завтра", today.plusDays(1), remindAtMinutes = 600),
        )
    }

    @Test
    fun `today shows its tasks with their times and what was left over from earlier days`() {
        withTasks()

        assertThat(state.status).isEqualTo(TasksStatus.Content)
        assertThat(state.tasks.map { it.id }).containsExactly("a", "b")
        assertThat(state.tasks.first().time).isEqualTo("09:05")
        assertThat(state.tasks.first().dayLabel).isNull()
        assertThat(state.overdue.map { it.id }).containsExactly("old")
        assertThat(state.overdue.single().dayLabel).isEqualTo("24.09.2026")
    }

    @Test
    fun `another day shows only its own tasks and no leftovers`() {
        withTasks()

        viewModel.onEvent(TasksEvent.NextDay)

        assertThat(state.day).isEqualTo(today.plusDays(1))
        assertThat(state.isToday).isFalse()
        assertThat(state.tasks.map { it.id }).containsExactly("tomorrow")
        assertThat(state.overdue).isEmpty()

        viewModel.onEvent(TasksEvent.PreviousDay)
        viewModel.onEvent(TasksEvent.PreviousDay)
        assertThat(state.tasks.map { it.id }).containsExactly("old")
        assertThat(state.overdue).isEmpty()

        viewModel.onEvent(TasksEvent.TodayClicked)
        assertThat(state.isToday).isTrue()
        assertThat(state.overdue.map { it.id }).containsExactly("old")
    }

    @Test
    fun `the month grid opens on the month of the day and marks the days that have tasks`() {
        withTasks()

        viewModel.onEvent(TasksEvent.MonthToggled)

        assertThat(state.view).isEqualTo(TasksView.MONTH)
        assertThat(state.month).isEqualTo(LocalDay.of(2026, 9, 1))
        // Today has one open and one done task, yesterday one open; tomorrow is also in September.
        assertThat(state.marks[today]).isEqualTo(DayMarkUi(open = 1, done = 1))
        assertThat(state.marks[today.plusDays(-1)]).isEqualTo(DayMarkUi(open = 1, done = 0))
        assertThat(state.marks[today.plusDays(1)]).isEqualTo(DayMarkUi(open = 1, done = 0))
        assertThat(state.marks[today.plusDays(2)]).isNull()

        viewModel.onEvent(TasksEvent.MonthToggled)
        assertThat(state.view).isEqualTo(TasksView.DAY)
    }

    @Test
    fun `a task added while the grid is open appears in it`() {
        withTasks()
        viewModel.onEvent(TasksEvent.MonthToggled)

        repository.tasks.value += aTask("new", "Новое", today.plusDays(2))

        assertThat(state.marks[today.plusDays(2)]).isEqualTo(DayMarkUi(open = 1, done = 0))
    }

    @Test
    fun `a day tapped in the grid becomes the day on screen and the grid stays open`() {
        withTasks()
        viewModel.onEvent(TasksEvent.MonthToggled)

        viewModel.onEvent(TasksEvent.DayPicked(today.plusDays(1)))

        assertThat(state.day).isEqualTo(today.plusDays(1))
        assertThat(state.view).isEqualTo(TasksView.MONTH)
        assertThat(state.tasks.map { it.id }).containsExactly("tomorrow")
    }

    @Test
    fun `the grid pages through months and follows the day when it leaves the month`() {
        withTasks()
        repository.tasks.value += aTask("oct", "Октябрьское", LocalDay.of(2026, 10, 3))
        viewModel.onEvent(TasksEvent.MonthToggled)

        viewModel.onEvent(TasksEvent.NextMonth)
        assertThat(state.month).isEqualTo(LocalDay.of(2026, 10, 1))
        assertThat(state.marks.keys).containsExactly(LocalDay.of(2026, 10, 3))
        viewModel.onEvent(TasksEvent.PreviousMonth)
        viewModel.onEvent(TasksEvent.PreviousMonth)
        assertThat(state.month).isEqualTo(LocalDay.of(2026, 8, 1))

        viewModel.onEvent(TasksEvent.TodayClicked)
        assertThat(state.month).isEqualTo(LocalDay.of(2026, 9, 1))
        viewModel.onEvent(TasksEvent.DayPicked(LocalDay.of(2026, 10, 3)))
        assertThat(state.month).isEqualTo(LocalDay.of(2026, 10, 1))
        assertThat(state.tasks.map { it.id }).containsExactly("oct")
    }

    @Test
    fun `months step over a year and a leap February`() {
        assertThat(LocalDay.of(2026, 12, 1).nextMonth()).isEqualTo(LocalDay.of(2027, 1, 1))
        assertThat(LocalDay.of(2026, 1, 15).previousMonth()).isEqualTo(LocalDay.of(2025, 12, 1))
        assertThat(LocalDay.of(2024, 2, 10).lastOfMonth()).isEqualTo(LocalDay.of(2024, 2, 29))
    }

    @Test
    fun `a tick saves the task as done, and a second tick as open again`() {
        withTasks()

        viewModel.onEvent(TasksEvent.DoneToggled("a"))
        viewModel.onEvent(TasksEvent.DoneToggled("b"))

        assertThat(repository.updated).containsExactly(
            TaskId("a") to TaskDraft("Купить плитку", today, 545, done = true),
            TaskId("b") to TaskDraft("Позвонить", today, null, done = false),
        ).inOrder()
    }

    @Test
    fun `a new task opens for the day on screen, and saving stores it and closes the form`() {
        withTasks()
        viewModel.onEvent(TasksEvent.NextDay)

        viewModel.onEvent(TaskEditorEvent.Add)
        assertThat(state.editor).isEqualTo(TaskEditorUi(day = today.plusDays(1)))
        viewModel.onEvent(TaskEditorEvent.TitleChanged(" Заказать двери "))
        viewModel.onEvent(TaskEditorEvent.TimeChanged(8 * 60))
        viewModel.onEvent(TaskEditorEvent.Save)

        assertThat(repository.added).containsExactly(TaskDraft("Заказать двери", today.plusDays(1), 480))
        assertThat(state.editor).isNull()
    }

    @Test
    fun `a task without a title stays open with the field marked, until it is typed`() {
        withTasks()
        viewModel.onEvent(TaskEditorEvent.Add)

        viewModel.onEvent(TaskEditorEvent.Save)

        assertThat(repository.added).isEmpty()
        assertThat(state.editor?.errors).containsExactly(ObjectField.TASK_TITLE, FieldProblem.REQUIRED)
        assertThat(state.editor?.isSaving).isFalse()

        viewModel.onEvent(TaskEditorEvent.TitleChanged("З"))
        assertThat(state.editor?.errors).isEmpty()
    }

    @Test
    fun `editing fills the form from the task, the time can be taken away, and saving updates it`() {
        withTasks()

        viewModel.onEvent(TaskEditorEvent.Edit("a"))
        assertThat(state.editor).isEqualTo(TaskEditorUi("a", "Купить плитку", today, 545, done = false))
        viewModel.onEvent(TaskEditorEvent.TimeChanged(null))
        viewModel.onEvent(TaskEditorEvent.DayChanged(today.plusDays(3)))
        viewModel.onEvent(TaskEditorEvent.Save)

        assertThat(repository.updated)
            .containsExactly(TaskId("a") to TaskDraft("Купить плитку", today.plusDays(3), null, done = false))
    }

    @Test
    fun `an overdue task can be opened and ticked like any other`() {
        withTasks()

        viewModel.onEvent(TaskEditorEvent.Edit("old"))
        assertThat(state.editor?.title).isEqualTo("Забыл вчера")
        viewModel.onEvent(TaskEditorEvent.Dismiss)
        viewModel.onEvent(TasksEvent.DoneToggled("old"))

        assertThat(repository.updated.single().first).isEqualTo(TaskId("old"))
        assertThat(repository.updated.single().second.done).isTrue()
    }

    @Test
    fun `deleting asks first, then deletes, and a dismissed dialog forgets it`() {
        withTasks()
        viewModel.onEvent(TaskEditorEvent.Edit("a"))

        viewModel.onEvent(TaskEditorEvent.Delete("a"))
        assertThat(state.editor).isNull()
        assertThat(state.pendingDeleteId).isEqualTo("a")
        viewModel.onEvent(TasksEvent.DialogDismissed)
        viewModel.onEvent(TasksEvent.DialogConfirmed)
        assertThat(repository.deleted).isEmpty()

        viewModel.onEvent(TaskEditorEvent.Delete("a"))
        viewModel.onEvent(TasksEvent.DialogConfirmed)

        assertThat(repository.deleted).containsExactly(TaskId("a"))
        assertThat(state.dialog).isNull()
    }

    @Test
    fun `a failed first load takes over, retry loads again, a failed reload and a failed write only say so`() {
        repository.loadError.value = AppError.Network
        assertThat(state.status).isEqualTo(TasksStatus.Error(AppError.Network.toUiText()))

        repository.loadError.value = null
        viewModel.onEvent(TasksEvent.Retry)
        withTasks()
        assertThat(state.status).isEqualTo(TasksStatus.Content)

        repository.loadError.value = AppError.Network
        assertThat(state.status).isEqualTo(TasksStatus.Content)
        repository.loadError.value = null

        repository.writeError = AppError.Network
        viewModel.onEvent(TasksEvent.DoneToggled("a"))
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText(), AppError.Network.toUiText())
    }

    @Test
    fun `minutes are shown as hours and minutes`() {
        assertThat(formatMinutes(0)).isEqualTo("00:00")
        assertThat(formatMinutes(9 * 60 + 5)).isEqualTo("09:05")
        assertThat(formatMinutes(23 * 60 + 59)).isEqualTo("23:59")
    }
}
