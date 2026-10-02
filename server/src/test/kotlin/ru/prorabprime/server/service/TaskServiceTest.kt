package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.TaskLimits
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeTaskRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.model.TaskQuery

class TaskServiceTest {

    private val repository = FakeTaskRepository()
    private val clock = FixedClock()
    private val service = TaskService(repository, clock)

    private fun Result<*>.problems() =
        ((exceptionOrNull() as? ServiceException)?.error as? ServiceError.Validation)?.fieldErrors
            ?.map { it.field to it.problem }

    private fun request(
        title: String = "Купить плитку",
        day: String = "2026-09-25",
        minutes: Int? = null,
        done: Boolean = false,
    ) = TaskRequestDto(title, day, minutes, done)

    @Test
    fun `a task is trimmed and stored with its day and time`() = runTest {
        val created = service.create(
            TEST_OWNER,
            request(
                title = "  Позвонить Ивану ",
                day = " 2026-09-25 ",
                minutes =
                    9 * 60 + 30,
            ),
        )
            .getOrThrow()

        assertThat(created.fields.title).isEqualTo("Позвонить Ивану")
        assertThat(created.fields.day).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(created.fields.remindAtMinutes).isEqualTo(570)
        assertThat(created.fields.done).isFalse()
        assertThat(created.createdAt).isEqualTo(FIXED_NOW)
    }

    @Test
    fun `a task needs a title, a real day and a minute of the day`() = runTest {
        assertThat(service.create(TEST_OWNER, request(title = " ")).problems())
            .containsExactly(ObjectFieldDto.TASK_TITLE to FieldProblemDto.REQUIRED)
        assertThat(service.create(TEST_OWNER, request(title = "я".repeat(TaskLimits.TITLE + 1))).problems())
            .containsExactly(ObjectFieldDto.TASK_TITLE to FieldProblemDto.TOO_LONG)
        assertThat(service.create(TEST_OWNER, request(day = "завтра")).problems())
            .containsExactly(ObjectFieldDto.TASK_DAY to FieldProblemDto.INVALID)
        assertThat(service.create(TEST_OWNER, request(day = "2026-02-30")).problems())
            .containsExactly(ObjectFieldDto.TASK_DAY to FieldProblemDto.INVALID)
        assertThat(service.create(TEST_OWNER, request(minutes = 24 * 60)).problems())
            .containsExactly(ObjectFieldDto.TASK_TIME to FieldProblemDto.INVALID)
        assertThat(service.create(TEST_OWNER, request(minutes = -1)).problems())
            .containsExactly(ObjectFieldDto.TASK_TIME to FieldProblemDto.INVALID)
        assertThat(service.create(TEST_OWNER, request(minutes = 0)).isSuccess).isTrue()
        assertThat(service.create(TEST_OWNER, request(minutes = 24 * 60 - 1)).isSuccess).isTrue()
    }

    @Test
    fun `the list is ordered by day, then time with untimed tasks last, then entry`() = runTest {
        clock.now = FIXED_NOW
        val untimed = service.create(TEST_OWNER, request(title = "без времени")).getOrThrow()
        clock.now = FIXED_NOW + 1.minutes
        val late = service.create(TEST_OWNER, request(title = "вечером", minutes = 18 * 60)).getOrThrow()
        val early = service.create(TEST_OWNER, request(title = "утром", minutes = 8 * 60)).getOrThrow()
        val tomorrow = service.create(
            TEST_OWNER,
            request(title = "завтра", day = "2026-09-26", minutes = 7 * 60),
        ).getOrThrow()

        val all = service.list(TEST_OWNER, TaskQuery())

        assertThat(all.map { it.id }).containsExactly(early.id, late.id, untimed.id, tomorrow.id).inOrder()
    }

    @Test
    fun `the list can be narrowed to a span of days and to the tasks still open`() = runTest {
        service.create(TEST_OWNER, request(title = "вчера", day = "2026-09-24")).getOrThrow()
        val today = service.create(TEST_OWNER, request(title = "сегодня")).getOrThrow()
        service.create(TEST_OWNER, request(title = "сделано", done = true)).getOrThrow()
        service.create(TEST_OWNER, request(title = "завтра", day = "2026-09-26")).getOrThrow()

        val onlyToday = service.list(
            TEST_OWNER,
            TaskQuery(from = LocalDate.of(2026, 9, 25), to = LocalDate.of(2026, 9, 25)),
        )
        val overdue = service.list(TEST_OWNER, TaskQuery(to = LocalDate.of(2026, 9, 24), openOnly = true))
        val open = service.list(TEST_OWNER, TaskQuery(from = LocalDate.of(2026, 9, 25), openOnly = true))

        assertThat(onlyToday.map { it.fields.title }).containsExactly("сегодня", "сделано")
        assertThat(overdue.map { it.fields.title }).containsExactly("вчера")
        assertThat(open.map { it.fields.title }).containsExactly("сегодня", "завтра")
        assertThat(open.first().id).isEqualTo(today.id)
    }

    @Test
    fun `a task is updated, marked done and deleted`() = runTest {
        val task = service.create(TEST_OWNER, request()).getOrThrow()

        service.update(
            TEST_OWNER,
            task.id,
            request(title = "Купить плитку 60х60", minutes = 600, done = true),
        ).getOrThrow()
        val stored = repository.records.getValue(task.id).fields
        assertThat(stored.title).isEqualTo("Купить плитку 60х60")
        assertThat(stored.done).isTrue()
        assertThat(stored.remindAtMinutes).isEqualTo(600)

        service.delete(TEST_OWNER, task.id).getOrThrow()
        assertThat(repository.records).isEmpty()
    }

    @Test
    fun `an unknown task is not found`() = runTest {
        val missing = UUID.randomUUID()

        assertThat((service.update(TEST_OWNER, missing, request()).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.NotFound::class.java)
        assertThat((service.delete(TEST_OWNER, missing).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.NotFound::class.java)
    }
}
