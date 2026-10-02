package ru.prorabprime.feature.tasks

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.testing.aTask
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class TasksContentTest {

    private val events = mutableListOf<TasksEvent>()
    private var backs = 0
    private val today = LocalDay.of(2026, 9, 25)

    private val buy = TaskUi(aTask("a", "Купить плитку", today, 545), time = "09:05", dayLabel = null)
    private val call = TaskUi(aTask("b", "Позвонить", today, done = true), time = null, dayLabel = null)
    private val late = TaskUi(aTask("old", "Забыл вчера", today.plusDays(-1)), time = null, dayLabel = "24.09.2026")

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: TasksState) = setContent {
        ProrabTheme { TasksContent(state, { events += it }, { backs++ }) }
    }

    private fun state(day: LocalDay = today, status: TasksStatus = TasksStatus.Content) = TasksState(
        status = status,
        day = day,
        today = today,
        tasks = persistentListOf(buy, call),
        overdue = persistentListOf(late),
    )

    @Test
    fun `the day, its tasks with their times and the leftovers are shown`() = runComposeUiTest {
        show(state())

        onNodeWithText("Сегодня, 25.09.2026").assertIsDisplayed()
        onNodeWithText("Купить плитку").assertIsDisplayed()
        onNodeWithText("09:05").assertIsDisplayed()
        onNodeWithText("Не сделано раньше").assertIsDisplayed()
        onNodeWithText("Забыл вчера").assertIsDisplayed()
        onNodeWithText("24.09.2026").assertIsDisplayed()
    }

    @Test
    fun `the calendar button asks for the month grid`() = runComposeUiTest {
        show(state())

        onNodeWithContentDescription("Показать месяц").performClick()
        assertThat(events).containsExactly(TasksEvent.MonthToggled)
    }

    @Test
    fun `an open grid shows the month, its weeks, and picks a day, with the buttons paging months`() =
        runComposeUiTest {
            show(state().copy(view = TasksView.MONTH, marks = persistentMapOf(today to DayMarkUi(1, 0))))

            onNodeWithText("Сентябрь 2026").assertIsDisplayed()
            onNodeWithContentDescription("Скрыть месяц").assertIsDisplayed()
            onNodeWithContentDescription("28.09.2026").performClick()
            onNodeWithContentDescription("Следующий месяц").performClick()
            onNodeWithContentDescription("Предыдущий месяц").performClick()

            assertThat(events).containsExactly(
                TasksEvent.DayPicked(LocalDay.of(2026, 9, 28)),
                TasksEvent.NextMonth,
                TasksEvent.PreviousMonth,
            ).inOrder()
        }

    @Test
    fun `a month is laid out in weeks that start on Monday`() {
        // September 2026 starts on a Tuesday and ends on a Wednesday.
        val weeks = monthWeeks(LocalDay.of(2026, 9, 1))

        assertThat(weeks).hasSize(5)
        assertThat(weeks.all { it.size == 7 }).isTrue()
        assertThat(weeks.first().take(2)).containsExactly(null, LocalDay.of(2026, 9, 1)).inOrder()
        assertThat(weeks.last().filterNotNull().last()).isEqualTo(LocalDay.of(2026, 9, 30))
        assertThat(weeks.flatten().filterNotNull()).hasSize(30)
    }

    @Test
    fun `an empty day names the nearest plan, and its button goes to that day`() = runComposeUiTest {
        val plan = NextPlanUi(today.plusDays(3), "Заказать двери", 3)
        setContent {
            ProrabTheme {
                TasksContent(
                    TasksState(status = TasksStatus.Content, day = today, today = today, nextPlan = plan),
                    { events += it },
                    { },
                )
            }
        }

        onNodeWithText("На этот день дел нет").assertIsDisplayed()
        onNodeWithText("Ближайшие планы — через 3 дня").assertIsDisplayed()
        onNodeWithText("Заказать двери", substring = true).performClick()

        assertThat(events).containsExactly(TasksEvent.DayPicked(today.plusDays(3)))
    }

    @Test
    fun `another day is named by its weekday and offers a way back to today`() = runComposeUiTest {
        show(state(day = LocalDay.of(2026, 9, 28)))

        onNodeWithText("Пн, 28.09.2026").assertIsDisplayed()
        onNodeWithText("Сегодня").performClick()

        assertThat(events).containsExactly(TasksEvent.TodayClicked)
    }

    @Test
    fun `the arrows move a day, a tick completes, a tap opens, the plus adds`() = runComposeUiTest {
        show(state())

        onNodeWithContentDescription("Предыдущий день").performClick()
        onNodeWithContentDescription("Следующий день").performClick()
        onAllNodesWithContentDescription("Отметить сделанным")[1].performClick()
        onNodeWithText("Купить плитку").performClick()
        onNodeWithContentDescription("Добавить дело").performClick()

        assertThat(events).containsExactly(
            TasksEvent.PreviousDay,
            TasksEvent.NextDay,
            TasksEvent.DoneToggled("a"),
            TaskEditorEvent.Edit("a"),
            TaskEditorEvent.Add,
        ).inOrder()
    }

    @Test
    fun `a day with nothing says so`() = runComposeUiTest {
        show(TasksState(status = TasksStatus.Content, day = today, today = today))

        onNodeWithText("На этот день дел нет").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry and back leaves`() = runComposeUiTest {
        show(TasksState(status = TasksStatus.Error(UiText.Raw("Сервер не отвечает")), day = today, today = today))

        onNodeWithText("Повторить").performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertThat(events).containsExactly(TasksEvent.Retry)
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `the open form shows its fields and saves`() = runComposeUiTest {
        show(state().copy(editor = TaskEditorUi(title = "Позвонить", day = today, minutes = 570)))

        onNodeWithText("Что сделать *").assertIsDisplayed()
        onNodeWithText("25.09.2026").assertIsDisplayed()
        onNodeWithText("09:30").assertIsDisplayed()
        onNodeWithText("Сохранить").performClick()

        assertThat(events).containsExactly(TaskEditorEvent.Save)
    }
}
