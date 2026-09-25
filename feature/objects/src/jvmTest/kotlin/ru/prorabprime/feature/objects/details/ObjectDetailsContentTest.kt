package ru.prorabprime.feature.objects.details

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class ObjectDetailsContentTest {

    private val events = mutableListOf<ObjectDetailsEvent>()
    private var edits = 0

    private val details =
        ObjectDetailsUi("Кухня", "Тверская, 5", ObjectStatus.PAUSED, "Иван", "+7 900 123-45-67", "Ключи у соседа")

    @Composable
    private fun show(state: ObjectDetailsState) {
        ProrabTheme { ObjectDetailsContent(state, onEvent = { events += it }, onEdit = { edits++ }, onBack = {}) }
    }

    @Test
    fun `every filled field is shown`() = runComposeUiTest {
        setContent { show(ObjectDetailsState(status = ObjectDetailsStatus.Content, details = details)) }

        onNodeWithText("Тверская, 5").assertIsDisplayed()
        onNodeWithText("На паузе").assertIsDisplayed()
        onNodeWithText("Иван").assertIsDisplayed()
        onNodeWithText("+7 900 123-45-67").assertIsDisplayed()
        onNodeWithText("Ключи у соседа").assertIsDisplayed()
    }

    @Test
    fun `the toolbar edits and asks to delete`() = runComposeUiTest {
        setContent { show(ObjectDetailsState(status = ObjectDetailsStatus.Content, details = details)) }

        onNodeWithContentDescription("Редактировать").performClick()
        onNodeWithContentDescription("Удалить объект").performClick()

        assertThat(edits).isEqualTo(1)
        assertThat(events).containsExactly(ObjectDetailsEvent.DeleteClicked)
    }

    @Test
    fun `the confirmation dialog confirms and dismisses`() = runComposeUiTest {
        val dialog = DialogModel.Confirmation(
            UiText.Raw("Удалить объект?"),
            UiText.Raw("Навсегда."),
            UiText.Raw("Удалить"),
            true,
        )
        setContent {
            show(ObjectDetailsState(status = ObjectDetailsStatus.Content, details = details, dialog = dialog))
        }

        onNodeWithText("Навсегда.").assertIsDisplayed()
        onNodeWithText("Удалить").performClick()

        assertThat(events).containsExactly(ObjectDetailsEvent.DialogConfirmed)
    }

    @Test
    fun `an error offers a retry`() = runComposeUiTest {
        setContent { show(ObjectDetailsState(status = ObjectDetailsStatus.Error(UiText.Raw("Нет связи")))) }

        onNodeWithText("Повторить").performClick()

        assertThat(events).containsExactly(ObjectDetailsEvent.Retry)
    }
}
