package ru.prorabprime.feature.objects.viewer

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ServerFilePath

@OptIn(ExperimentalTestApi::class)
class PhotoViewerContentTest {

    private val notes = mutableListOf<Pair<String, String>>()
    private val receipts = mutableListOf<Triple<String, String, String>>()
    private val rotated = mutableListOf<String>()

    private fun stateWith(
        note: String? = null,
        receiptLine: String? = null,
        isReceipt: Boolean = false,
        amountInput: String = "",
        dateInput: String = "",
    ) = PhotoViewerState(
        status = PhotoViewerStatus.Content,
        photos = persistentListOf(
            ViewerPhoto(
                id = "p1",
                path = ServerFilePath("/files/o/p1.jpg"),
                note = note,
                receiptLine = receiptLine,
                isReceipt = isReceipt,
                amountInput = amountInput,
                dateInput = dateInput,
            ),
        ),
    )

    @Composable
    private fun show(state: PhotoViewerState) {
        ProrabTheme {
            PhotoViewerContent(
                state = state,
                onBack = {},
                onRotate = { rotated += it },
                onSaveNote = { id, text -> notes += id to text },
                onSaveReceipt = { id, amount, date -> receipts += Triple(id, amount, date) },
            )
        }
    }

    @Test
    fun `the note is shown over the photo`() = runComposeUiTest {
        setContent { show(stateWith(note = "Трещина над окном")) }

        onNodeWithText("Трещина над окном").assertIsDisplayed()
    }

    @Test
    fun `a receipt's sum and time are shown above the note`() = runComposeUiTest {
        setContent { show(stateWith(isReceipt = true, receiptLine = "790 ₽ · 01.10.2026 15:26")) }

        onNodeWithText("790 ₽ · 01.10.2026 15:26").assertIsDisplayed()
    }

    @Test
    fun `without a note the bar invites to add one`() = runComposeUiTest {
        setContent { show(stateWith()) }

        onNodeWithText("Добавить заметку").assertIsDisplayed()
    }

    @Test
    fun `a tap on the bar opens the editor, and saving sends the text for that photo`() = runComposeUiTest {
        setContent { show(stateWith()) }

        onNodeWithText("Добавить заметку").performClick()
        onAllNodes(hasSetTextAction())[0].performTextInput("Заменить до пятницы")
        onNodeWithText("Сохранить").performClick()

        assertThat(notes).containsExactly("p1" to "Заменить до пятницы")
    }

    @Test
    fun `cancelling the editor sends nothing`() = runComposeUiTest {
        setContent { show(stateWith(note = "была")) }

        onNodeWithText("была").performClick()
        onNodeWithText("Отмена").performClick()

        assertThat(notes).isEmpty()
    }

    @Test
    fun `the rotate button names the photo it turns`() = runComposeUiTest {
        setContent { show(stateWith()) }

        onNodeWithContentDescription("Повернуть").performClick()

        assertThat(rotated).containsExactly("p1")
    }

    @Test
    fun `a receipt without a sum invites to give one, and the editor sends the sum and the day`() = runComposeUiTest {
        setContent { show(stateWith(isReceipt = true)) }

        onNodeWithText("Указать сумму и дату").performClick()
        onAllNodes(hasSetTextAction())[0].performTextInput("1250,50")
        onAllNodes(hasSetTextAction())[1].performTextInput("01.10.2026")
        onNodeWithText("Сохранить").performClick()

        assertThat(receipts).containsExactly(Triple("p1", "1250,50", "01.10.2026"))
    }

    @Test
    fun `a plain photo has no receipt row`() = runComposeUiTest {
        setContent { show(stateWith()) }

        onNodeWithText("Указать сумму и дату").assertDoesNotExist()
    }
}
