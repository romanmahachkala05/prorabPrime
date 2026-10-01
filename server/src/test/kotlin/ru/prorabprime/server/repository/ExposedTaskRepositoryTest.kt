package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord

class ExposedTaskRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var tasks: ExposedTaskRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        tasks = ExposedTaskRepository(DbExecutor(Database.connect(dataSource), Dispatchers.IO))
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun task(
        day: String,
        minutes: Int?,
        minutesLater: Int = 0,
        done: Boolean = false,
        title: String = "t",
    ) = TaskRecord(
        UUID.randomUUID(),
        TaskFields(title, LocalDate.parse(day), minutes, done),
        base + minutesLater.minutes,
    )

    @Test
    fun `a task reads back unchanged`() = runTest {
        val task = task("2026-09-25", 570, title = "Позвонить")
        tasks.insert(task)

        assertThat(tasks.find(task.id)).isEqualTo(task)
    }

    @Test
    fun `tasks come by day, then time with untimed last, then entry`() = runTest {
        val untimed = task("2026-09-25", null, 0)
        val late = task("2026-09-25", 18 * 60, 1)
        val early = task("2026-09-25", 8 * 60, 2)
        val tomorrow = task("2026-09-26", 7 * 60, 3)
        listOf(tomorrow, untimed, late, early).forEach { tasks.insert(it) }

        assertThat(tasks.list(TaskQuery())).containsExactly(early, late, untimed, tomorrow).inOrder()
    }

    @Test
    fun `the query narrows by days, both ends inclusive, and by openness`() = runTest {
        val yesterday = task("2026-09-24", null)
        val today = task("2026-09-25", null)
        val doneToday = task("2026-09-25", null, done = true)
        val tomorrow = task("2026-09-26", null)
        listOf(yesterday, today, doneToday, tomorrow).forEach { tasks.insert(it) }
        val day = LocalDate.of(2026, 9, 25)

        assertThat(tasks.list(TaskQuery(from = day, to = day))).containsExactly(today, doneToday)
        assertThat(tasks.list(TaskQuery(to = LocalDate.of(2026, 9, 24), openOnly = true))).containsExactly(yesterday)
        assertThat(tasks.list(TaskQuery(from = day, openOnly = true))).containsExactly(today, tomorrow)
    }

    @Test
    fun `updating and deleting report whether there was a task`() = runTest {
        val task = task("2026-09-25", null)
        tasks.insert(task)

        val changed = TaskFields("Готово", LocalDate.of(2026, 9, 27), 600, true)
        assertThat(tasks.update(task.id, changed)).isTrue()
        assertThat(tasks.find(task.id)?.fields).isEqualTo(changed)
        assertThat(tasks.update(UUID.randomUUID(), changed)).isFalse()
        assertThat(tasks.delete(task.id)).isTrue()
        assertThat(tasks.delete(task.id)).isFalse()
    }
}
