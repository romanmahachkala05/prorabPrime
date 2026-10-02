package ru.prorabprime.feature.objects.gallery

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.ServerFilePath

@OptIn(ExperimentalTestApi::class)
class GalleryContentTest {

    private val events = mutableListOf<GalleryEvent>()
    private val opened = mutableListOf<String>()
    private var backed = 0

    private fun tile(id: String, amount: String? = null) = GalleryPhotoUi(
        id,
        ServerFilePath("/files/$id.jpg"),
        isCover = false,
        isPending = false,
        quarterTurns = 0,
        amount,
    )

    private val photos = persistentListOf(tile("p1"), tile("p2", "790 ₽"), tile("p3"))

    @Composable
    private fun show(state: GalleryState) {
        ProrabTheme { GalleryContent(state, { events += it }, { opened += it }, { backed++ }) }
    }

    @Test
    fun `the title says which folder, and a tile with a sum shows it`() = runComposeUiTest {
        setContent { show(GalleryState(status = GalleryStatus.Content, receipts = true, photos = photos)) }

        onNodeWithText("Чеки").assertIsDisplayed()
        onNodeWithText("790 ₽").assertIsDisplayed()
        onNodeWithContentDescription("Назад").performClick()
        assertThat(backed).isEqualTo(1)
    }

    @Test
    fun `a folder with nothing in it says so and offers no selecting`() = runComposeUiTest {
        setContent { show(GalleryState(status = GalleryStatus.Content)) }

        onNodeWithText("Здесь пока пусто").assertIsDisplayed()
        onNodeWithText("Выбрать").assertDoesNotExist()
    }

    @Test
    fun `the select button starts selecting`() = runComposeUiTest {
        setContent { show(GalleryState(status = GalleryStatus.Content, photos = photos)) }

        onNodeWithText("Выбрать").performClick()

        assertThat(events).containsExactly(GalleryEvent.SelectingToggled)
    }

    @Test
    fun `while selecting the bar counts, picks all and deletes`() = runComposeUiTest {
        setContent {
            show(
                GalleryState(
                    status = GalleryStatus.Content,
                    photos = photos,
                    selecting = true,
                    selected = persistentSetOf("p1", "p3"),
                ),
            )
        }

        onNodeWithText("Выбрано: 2").assertIsDisplayed()
        onNodeWithText("Выбрать все").performClick()
        onNodeWithContentDescription("Удалить выбранные").performClick()
        onNodeWithContentDescription("Отменить выбор").performClick()

        assertThat(events).containsExactly(
            GalleryEvent.SelectAllToggled,
            GalleryEvent.DeleteClicked,
            GalleryEvent.SelectingToggled,
        ).inOrder()
    }
}
