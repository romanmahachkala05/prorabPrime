package ru.prorabprime.feature.objects.edit

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectStatus

@OptIn(ExperimentalTestApi::class)
class ObjectEditContentTest {

    private val events = mutableListOf<ObjectEditEvent>()

    @Composable
    private fun show(state: ObjectEditState) {
        ProrabTheme { ObjectEditContent(state, onEvent = { events += it }, onBack = {}) }
    }

    private val content = ObjectEditState(status = ObjectEditStatus.Content)

    @Test
    fun `a new object and an edit have their own titles`() = runComposeUiTest {
        setContent { show(content) }
        onNodeWithText("Новый объект").assertIsDisplayed()
    }

    @Test
    fun `typing sends the field it belongs to`() = runComposeUiTest {
        setContent { show(content) }

        onNodeWithText("Адрес *").performTextInput("Тверская")

        assertThat(events).containsExactly(ObjectEditEvent.FieldChanged(ObjectField.ADDRESS, "Тверская"))
    }

    @Test
    fun `a status chip selects that status`() = runComposeUiTest {
        setContent { show(content) }

        onNodeWithText("Сдан").performClick()

        assertThat(events).containsExactly(ObjectEditEvent.StatusChanged(ObjectStatus.DONE))
    }

    @Test
    fun `a field error is shown under its field`() = runComposeUiTest {
        setContent { show(content.copy(fieldErrors = persistentMapOf(ObjectField.ADDRESS to FieldProblem.REQUIRED))) }

        onNodeWithText("Обязательное поле").assertIsDisplayed()
    }

    @Test
    fun `save sends the form`() = runComposeUiTest {
        setContent { show(content) }

        onNodeWithText("Сохранить").performClick()

        assertThat(events).containsExactly(ObjectEditEvent.SaveClicked)
    }
}
