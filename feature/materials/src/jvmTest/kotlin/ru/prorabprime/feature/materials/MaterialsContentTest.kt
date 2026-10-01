package ru.prorabprime.feature.materials

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class MaterialsContentTest {

    private val events = mutableListOf<MaterialsEvent>()
    private var backs = 0

    private val materials = MaterialsUi(
        persistentListOf(
            MaterialUi("m1", "Плитка", MaterialStatus.IN_APARTMENT),
            MaterialUi("m2", "Двери", MaterialStatus.CHOSEN),
            MaterialUi("m3", "Ламинат", MaterialStatus.NOT_CHOSEN),
        ),
    )

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: MaterialsState) = setContent {
        ProrabTheme { MaterialsContent(state, { events += it }, { backs++ }) }
    }

    @Test
    fun `every material shows its state and the progress`() = runComposeUiTest {
        show(MaterialsState(status = MaterialsStatus.Content, materials = materials))

        onNodeWithText("Плитка").assertIsDisplayed()
        onNodeWithText("В квартире").assertIsDisplayed()
        onNodeWithText("Выбран").assertIsDisplayed()
        onNodeWithText("Не выбран").assertIsDisplayed()
        onNodeWithText("В квартире: 1 из 3").assertIsDisplayed()
    }

    @Test
    fun `a tap on a status moves it on, a tap on the title opens the form`() = runComposeUiTest {
        show(MaterialsState(status = MaterialsStatus.Content, materials = materials))

        onNodeWithText("Не выбран").performClick()
        onNodeWithText("Двери").performClick()

        assertThat(
            events,
        ).containsExactly(MaterialsEvent.StatusTapped("m3"), MaterialsEvent.EditClicked("m2")).inOrder()
    }

    @Test
    fun `an empty list offers the standard set`() = runComposeUiTest {
        show(MaterialsState(status = MaterialsStatus.Content, materials = MaterialsUi()))

        onNodeWithText("Список материалов пуст").assertIsDisplayed()
        onNodeWithText("Добавить стандартный набор").performClick()

        assertThat(events).containsExactly(MaterialsEvent.AddDefaultsClicked)
    }

    @Test
    fun `the plus adds, an error offers a retry, back leaves`() = runComposeUiTest {
        show(MaterialsState(status = MaterialsStatus.Error(UiText.Raw("Сервер не отвечает"))))

        onNodeWithText("Повторить").performClick()
        onNodeWithContentDescription("Добавить материал").performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertThat(events).containsExactly(MaterialsEvent.Retry, MaterialsEvent.AddClicked).inOrder()
        assertThat(backs).isEqualTo(1)
    }

    @Test
    fun `the open form shows its fields and saves`() = runComposeUiTest {
        show(
            MaterialsState(
                status = MaterialsStatus.Content,
                materials = materials,
                editor = MaterialEditorUi(title = "Обои"),
            ),
        )

        onNodeWithText("Название *").assertIsDisplayed()
        onNodeWithText("Сохранить").performClick()

        assertThat(events).containsExactly(MaterialsEvent.SaveClicked)
    }
}
