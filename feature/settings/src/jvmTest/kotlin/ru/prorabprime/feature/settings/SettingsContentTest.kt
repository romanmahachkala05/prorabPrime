package ru.prorabprime.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class SettingsContentTest {

    private val events = mutableListOf<SettingsEvent>()
    private val content = SettingsState(status = SettingsStatus.Content, baseUrl = "http://h:1", apiToken = "t")

    @Composable
    private fun show(state: SettingsState) {
        ProrabTheme { SettingsContent(state = state, onEvent = { events += it }, onBack = {}) }
    }

    @Test
    fun `typing in the address field sends the new text`() = runComposeUiTest {
        setContent { show(content) }

        onNodeWithText("http://h:1").performTextReplacement("http://h:2")

        assertThat(events).containsExactly(SettingsEvent.BaseUrlChanged("http://h:2"))
    }

    @Test
    fun `the buttons send check and save`() = runComposeUiTest {
        setContent { show(content) }

        onNodeWithText("Проверить соединение").performClick()
        onNodeWithText("Сохранить").performClick()

        assertThat(events).containsExactly(SettingsEvent.CheckClicked, SettingsEvent.SaveClicked).inOrder()
    }

    @Test
    fun `a failed check shows why`() = runComposeUiTest {
        setContent { show(content.copy(check = ConnectionCheck.Failed(UiText.Raw("Токен не принят")))) }

        onNodeWithText("Токен не принят").assertIsDisplayed()
    }

    @Test
    fun `a running check cannot be started again`() = runComposeUiTest {
        setContent { show(content.copy(check = ConnectionCheck.Running)) }

        onNodeWithText("Проверить соединение").assertIsNotEnabled()
        onNodeWithText("Проверяю…").assertIsDisplayed()
    }

    @Test
    fun `an address error is shown under the field`() = runComposeUiTest {
        setContent { show(content.copy(addressError = UiText.Raw("Нужен http://"))) }

        onNodeWithText("Нужен http://").assertIsDisplayed()
    }
}
