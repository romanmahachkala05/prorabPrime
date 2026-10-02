package ru.prorabprime.feature.sync

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ChangeAction
import ru.prorabprime.domain.model.ChangeKind
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus

@OptIn(ExperimentalTestApi::class)
class SyncStatusContentTest {

    private val events = mutableListOf<SyncEvent>()
    private var settingsOpened = 0

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: SyncUiState) = setContent {
        ProrabTheme { SyncStatusContent(state, { events += it }, { settingsOpened++ }) }
    }

    @Test
    fun `nothing is said while all is well`() = runComposeUiTest {
        show(SyncUiState(status = SyncStatus(isSyncing = true)))

        onNodeWithText("Нет связи").assertDoesNotExist()
        onNodeWithText("Ждут отправки: 0").assertDoesNotExist()
    }

    @Test
    fun `no signal and waiting changes are said together`() = runComposeUiTest {
        show(SyncUiState(status = SyncStatus(pending = 3, offline = true)))

        onNodeWithText("Нет связи · ждут отправки: 3").assertIsDisplayed()
    }

    @Test
    fun `changes waiting with a signal, and being sent, are said too`() = runComposeUiTest {
        show(SyncUiState(status = SyncStatus(pending = 2)))
        onNodeWithText("Ждут отправки: 2").assertIsDisplayed()
    }

    @Test
    fun `a refused token is said and a tap goes to the settings`() = runComposeUiTest {
        show(SyncUiState(status = SyncStatus(pending = 1, unauthorized = true)))

        onNodeWithText("Сервер не принял токен").performClick()

        assertThat(settingsOpened).isEqualTo(1)
    }

    @Test
    fun `a tap on the chip is passed on`() = runComposeUiTest {
        val refused = FailedChange(5, ChangeKind.CONTACT, ChangeAction.CREATE, "Анна", "validation")
        show(SyncUiState(status = SyncStatus(failed = 1), failed = persistentListOf(refused)))

        onNodeWithText("Не отправлено: 1").performClick()

        assertThat(events).containsExactly(SyncEvent.ChipClicked)
    }

    @Test
    fun `refused changes are listed with their reasons and can be retried or given up on`() = runComposeUiTest {
        val refused = FailedChange(5, ChangeKind.CONTACT, ChangeAction.CREATE, "Анна", "validation")
        show(SyncUiState(status = SyncStatus(failed = 1), failed = persistentListOf(refused), sheetOpen = true))

        onNodeWithText("Создание: контакт «Анна»").assertIsDisplayed()
        onNodeWithText("Сервер не принял данные").assertIsDisplayed()
        onNodeWithText("Отменить изменение").performClick()
        onNodeWithText("Повторить все").performClick()

        assertThat(events).containsExactly(SyncEvent.DiscardClicked(5), SyncEvent.RetryClicked(null)).inOrder()
    }
}
