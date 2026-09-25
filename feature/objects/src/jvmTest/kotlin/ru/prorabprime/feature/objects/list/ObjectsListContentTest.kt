package ru.prorabprime.feature.objects.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class ObjectsListContentTest {

    private val events = mutableListOf<ObjectsListEvent>()
    private val opened = mutableListOf<String>()
    private var created = 0

    private val cards = persistentListOf(
        ObjectCardUi("1", "Кухня", "Тверская, 5", ObjectStatus.IN_PROGRESS, 3, null),
        ObjectCardUi("2", "Арбат, 3", null, ObjectStatus.DONE, 0, null),
    )

    @Composable
    private fun show(state: ObjectsListState) {
        ProrabTheme {
            ObjectsListContent(
                state = state,
                onEvent = { events += it },
                onOpenObject = { opened += it },
                onCreateObject = { created++ },
                onOpenSettings = {},
            )
        }
    }

    @Test
    fun `cards show title, address, status and photo count, and open on tap`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithText("Тверская, 5").assertIsDisplayed()
        onNodeWithText("В работе").assertIsDisplayed()
        onNodeWithText("3 фото").assertIsDisplayed()
        onNodeWithText("Кухня").performClick()

        assertThat(opened).containsExactly("1")
    }

    @Test
    fun `no objects offers to add the first one`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Empty)) }

        onNodeWithText("Добавить первый объект").performClick()

        assertThat(created).isEqualTo(1)
    }

    @Test
    fun `a search with no match says so`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.NothingFound, search = "xyz")) }

        onNodeWithText("Ничего не найдено").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Error(UiText.Raw("Сервер не отвечает")))) }

        onNodeWithText("Сервер не отвечает").assertIsDisplayed()
        onNodeWithText("Повторить").performClick()

        assertThat(events).containsExactly(ObjectsListEvent.Retry)
    }

    @Test
    fun `typing sends the search text`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithText("Поиск по адресу или названию").performTextInput("лен")

        assertThat(events).containsExactly(ObjectsListEvent.SearchChanged("лен"))
    }

    @Test
    fun `the sort menu lists every option and sends the chosen one`() = runComposeUiTest {
        setContent {
            show(ObjectsListState(status = ObjectsListStatus.Content, items = cards, sort = ObjectSort.UPDATED_NEWEST))
        }

        onNodeWithText("Недавно изменённые").performClick()
        onNodeWithText("Адрес Я–А").performClick()

        assertThat(events).containsExactly(ObjectsListEvent.SortSelected(ObjectSort.ADDRESS_DESC))
    }

    @Test
    fun `the button adds an object`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithText("Добавить объект", useUnmergedTree = true).performClick()

        assertThat(created).isEqualTo(1)
    }
}
