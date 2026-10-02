package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.Reminder
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.model.toReminders
import ru.prorabprime.testing.FakeReminderScheduler
import ru.prorabprime.testing.FakeTasksRepository
import ru.prorabprime.testing.aTask

class TaskUseCasesTest {

    private val repository = FakeTasksRepository()
    private val save = SaveTaskUseCase(repository)
    private val day = LocalDay.of(2026, 9, 25)

    private fun Result<*>.problems() = (exceptionOrNull()?.asAppError() as? AppError.Validation)?.fieldErrors

    @Test
    fun `a task is normalized and stored`() = runTest {
        assertThat(save.create(TaskDraft(" Позвонить ", day, 570)).isSuccess).isTrue()
        assertThat(save.update(TaskId("t"), TaskDraft("Позвонить", day, null, done = true)).isSuccess).isTrue()

        assertThat(repository.added).containsExactly(TaskDraft("Позвонить", day, 570))
        assertThat(repository.updated).containsExactly(TaskId("t") to TaskDraft("Позвонить", day, null, done = true))
    }

    @Test
    fun `a task needs a title, a day and a real minute of the day`() = runTest {
        assertThat(save.create(TaskDraft(" ", day)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TASK_TITLE to FieldProblem.REQUIRED))
        assertThat(save.create(TaskDraft("x", null)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TASK_DAY to FieldProblem.INVALID))
        assertThat(save.create(TaskDraft("x", day, 24 * 60)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TASK_TIME to FieldProblem.INVALID))
        assertThat(save.create(TaskDraft("x", day, -1)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TASK_TIME to FieldProblem.INVALID))
        assertThat(save.create(TaskDraft("x".repeat(TaskDraft.MAX_TITLE + 1), day)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TASK_TITLE to FieldProblem.TOO_LONG))
        assertThat(repository.added).isEmpty()
    }

    @Test
    fun `deleting passes through, failures included`() = runTest {
        assertThat(DeleteTaskUseCase(repository)(TaskId("t")).isSuccess).isTrue()
        assertThat(repository.deleted).containsExactly(TaskId("t"))

        repository.writeError = AppError.NotFound
        assertThat(DeleteTaskUseCase(repository)(TaskId("t")).exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.NotFound)
    }

    @Test
    fun `the day and the overdue are told apart`() = runTest {
        repository.tasks.value = listOf(
            aTask("old", day = day.plusDays(-2)),
            aTask("oldDone", day = day.plusDays(-1), done = true),
            aTask("today", day = day),
        )

        assertThat(ObserveDayTasksUseCase(repository)(day).first().getOrThrow().map { it.id.value })
            .containsExactly("today")
        assertThat(ObserveOverdueTasksUseCase(repository)(day).first().getOrThrow().map { it.id.value })
            .containsExactly("old")
    }

    @Test
    fun `only open tasks with a time ask for a reminder`() {
        val reminders = listOf(
            aTask("a", remindAtMinutes = 540),
            aTask("b", remindAtMinutes = 600, done = true),
            aTask("c", remindAtMinutes = null),
        ).toReminders()

        assertThat(reminders).containsExactly(Reminder(TaskId("a"), "Купить плитку", day, 540))
    }

    @Test
    fun `the alarms follow the tasks as they change, and a failed load leaves them alone`() = runTest {
        val scheduler = FakeReminderScheduler()
        val keep = KeepRemindersUseCase(repository, scheduler) { day }
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { keep.run() }

        assertThat(scheduler.reminders).isEmpty()

        repository.tasks.value = listOf(aTask("a", remindAtMinutes = 540))
        assertThat(scheduler.reminders.map { it.taskId.value }).containsExactly("a")

        repository.tasks.value = listOf(aTask("a", remindAtMinutes = 540, done = true))
        assertThat(scheduler.reminders).isEmpty()

        repository.tasks.value = listOf(aTask("b", remindAtMinutes = 700))
        val syncsBefore = scheduler.syncs
        repository.loadError.value = AppError.Network
        assertThat(scheduler.syncs).isEqualTo(syncsBefore)
        assertThat(scheduler.reminders.map { it.taskId.value }).containsExactly("b")
        job.cancel()
    }
}
