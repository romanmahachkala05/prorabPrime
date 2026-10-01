package ru.prorabprime.feature.objects.list

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class ObjectsListContentTest {

    private val events = mutableListOf<ObjectsListEvent>()
    private val opened = mutableListOf<String>()
    private var created = 0
    private var photoCameraOpened = 0
    private var receiptCameraOpened = 0
    private var expensesOpened = 0
    private var offerExpenses = false

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
                onOpenPhotoCamera = { photoCameraOpened++ },
                onOpenReceiptCamera = { receiptCameraOpened++ },
                onOpenExpenses = if (offerExpenses) ({ expensesOpened++ }) else null,
            )
        }
    }

    @Test
    fun `tiles show title and status, and open on tap`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithText("Арбат, 3").assertIsDisplayed()
        onNodeWithText("В работе").assertIsDisplayed()
        onNodeWithText("Кухня").performClick()

        assertThat(opened).containsExactly("1")
    }

    @Test
    fun `the expenses button is only there where the report is offered`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }
        onNodeWithContentDescription("Расходы").assertDoesNotExist()
    }

    @Test
    fun `the expenses button opens the report`() = runComposeUiTest {
        offerExpenses = true
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithContentDescription("Расходы").performClick()

        assertThat(expensesOpened).isEqualTo(1)
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

        onNode(hasSetTextAction()).performTextInput("лен")

        assertThat(events).containsExactly(ObjectsListEvent.SearchChanged("лен"))
    }

    @Test
    fun `the sort menu lists every option and sends the chosen one`() = runComposeUiTest {
        setContent {
            show(ObjectsListState(status = ObjectsListStatus.Content, items = cards, sort = ObjectSort.UPDATED_NEWEST))
        }

        onNodeWithContentDescription("Недавно изменённые").performClick()
        onNodeWithText("Адрес Я–А").performClick()

        assertThat(events).containsExactly(ObjectsListEvent.SortSelected(ObjectSort.ADDRESS_DESC))
    }

    @Test
    fun `the top bar adds an object`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithContentDescription("Добавить объект").performClick()

        assertThat(created).isEqualTo(1)
    }

    @Test
    fun `the two camera buttons open the camera for a photo and for a receipt`() = runComposeUiTest {
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards)) }

        onNodeWithContentDescription("Сфотографировать объект").performClick()
        assertThat(photoCameraOpened).isEqualTo(1)
        assertThat(receiptCameraOpened).isEqualTo(0)

        onNodeWithContentDescription("Сфотографировать чек").performClick()
        assertThat(receiptCameraOpened).isEqualTo(1)
    }

    @Test
    fun `a captured picture asks for the object only and sends the chosen one`() = runComposeUiTest {
        val capture = CaptureUi(persistentListOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.RECEIPT)
        setContent { show(ObjectsListState(status = ObjectsListStatus.Content, items = cards, capture = capture)) }

        onNodeWithText("Чек: на какой объект?").assertIsDisplayed()
        // The same title is on the list behind the sheet; the sheet is the last one.
        onAllNodesWithText("Кухня").onLast().performClick()

        assertThat(events).containsExactly(ObjectsListEvent.CaptureTargetChosen("1"))
    }
}
