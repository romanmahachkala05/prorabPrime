package ru.prorabprime.feature.trash

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class TrashContentTest {

    private val events = mutableListOf<TrashEvent>()
    private var backed = 0

    private val state = TrashState(
        status = TrashStatus.Content,
        objects = persistentListOf(
            TrashObjectUi("o1", "Кухня", "Ленина, 1", null, photoCount = 3, daysLeft = 29),
        ),
        photos = persistentListOf(
            TrashPhotoUi(
                "p1",
                "Арбат, 3",
                AttachmentKind.RECEIPT,
                ServerFilePath("/files/o2/p1_thumb.jpg"),
                daysLeft = 1,
            ),
        ),
    )

    @Composable
    private fun show(state: TrashState) {
        ProrabTheme { TrashContent(state, { events += it }, { backed++ }) }
    }

    @Test
    fun `what is kept is shown with where it came from and the days left`() = runComposeUiTest {
        setContent { show(state) }

        onNodeWithText("Кухня").assertIsDisplayed()
        // The ending of the days depends on the plural rules of the machine running the test.
        onNodeWithText("Ленина, 1 · Фото: 3 · остал", substring = true).assertIsDisplayed()
        onNodeWithText("Арбат, 3").assertIsDisplayed()
        onNodeWithText("Чек · остал", substring = true).assertIsDisplayed()
    }

    @Test
    fun `an item is restored or removed by its buttons`() = runComposeUiTest {
        setContent { show(state) }

        onAllNodesWithContentDescription("Восстановить").onFirst().performClick()
        onAllNodesWithContentDescription("Удалить навсегда").onFirst().performClick()

        assertThat(
            events,
        ).containsExactly(TrashEvent.RestoreObject("o1"), TrashEvent.PurgeObjectClicked("o1")).inOrder()
    }

    @Test
    fun `the clear button asks to empty, and an empty trash says what it is for`() = runComposeUiTest {
        setContent { show(state) }
        onNodeWithText("Очистить").performClick()
        assertThat(events).containsExactly(TrashEvent.EmptyClicked)
    }

    @Test
    fun `an empty trash says so and offers no clearing`() = runComposeUiTest {
        setContent { show(TrashState(status = TrashStatus.Content)) }

        onNodeWithText("В корзине пусто", substring = true).assertIsDisplayed()
        onNodeWithText("Очистить").assertDoesNotExist()
        onNodeWithContentDescription("Назад").performClick()
        assertThat(backed).isEqualTo(1)
    }

    @Test
    fun `an error offers a retry`() = runComposeUiTest {
        setContent { show(TrashState(status = TrashStatus.Error(UiText.Raw("Нет связи")))) }

        onNodeWithText("Нет связи").assertIsDisplayed()
        onNodeWithText("Повторить").performClick()

        assertThat(events).containsExactly(TrashEvent.Retry)
    }
}
