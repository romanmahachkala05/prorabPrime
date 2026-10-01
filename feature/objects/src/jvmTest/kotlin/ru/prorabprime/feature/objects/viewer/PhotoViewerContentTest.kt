package ru.prorabprime.feature.objects.viewer

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

    private val saved = mutableListOf<Pair<String, String>>()
    private val rotated = mutableListOf<String>()

    private fun stateWith(note: String?) = PhotoViewerState(
        status = PhotoViewerStatus.Content,
        photos = persistentListOf(ViewerPhoto("p1", ServerFilePath("/files/o/p1.jpg"), note = note)),
    )

    @Test
    fun `the note is shown over the photo, or an invitation to write one`() = runComposeUiTest {
        setContent {
            ProrabTheme {
                PhotoViewerContent(stateWith("Трещина над окном"), {}, { rotated += it }, { id, t ->
                    saved +=
                        id to t
                })
            }
        }
        onNodeWithText("Трещина над окном").assertIsDisplayed()
    }

    @Test
    fun `a receipt's sum and time are shown above the note`() = runComposeUiTest {
        val state = PhotoViewerState(
            status = PhotoViewerStatus.Content,
            photos = persistentListOf(
                ViewerPhoto("p1", ServerFilePath("/files/o/p1.jpg"), receiptLine = "790 ₽ · 01.10.2026 15:26"),
            ),
        )
        setContent { ProrabTheme { PhotoViewerContent(state, {}, {}, { _, _ -> }) } }

        onNodeWithText("790 ₽ · 01.10.2026 15:26").assertIsDisplayed()
    }

    @Test
    fun `without a note the bar invites to add one`() = runComposeUiTest {
        setContent { ProrabTheme { PhotoViewerContent(stateWith(null), {}, {}, { _, _ -> }) } }

        onNodeWithText("Добавить заметку").assertIsDisplayed()
    }

    @Test
    fun `a tap on the bar opens the editor, and saving sends the text for that photo`() = runComposeUiTest {
        setContent {
            ProrabTheme { PhotoViewerContent(stateWith(null), {}, {}, { id, text -> saved += id to text }) }
        }

        onNodeWithText("Добавить заметку").performClick()
        onNode(hasSetTextAction()).performTextInput("Заменить до пятницы")
        onNodeWithText("Сохранить").performClick()

        assertThat(saved).containsExactly("p1" to "Заменить до пятницы")
    }

    @Test
    fun `cancelling the editor sends nothing`() = runComposeUiTest {
        setContent { ProrabTheme { PhotoViewerContent(stateWith("была"), {}, {}, { id, t -> saved += id to t }) } }

        onNodeWithText("была").performClick()
        onNodeWithText("Отмена").performClick()

        assertThat(saved).isEmpty()
    }

    @Test
    fun `the rotate button names the photo it turns`() = runComposeUiTest {
        setContent { ProrabTheme { PhotoViewerContent(stateWith(null), {}, { rotated += it }, { _, _ -> }) } }

        onNodeWithContentDescription("Повернуть").performClick()

        assertThat(rotated).containsExactly("p1")
    }
}
