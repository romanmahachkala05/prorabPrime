package ru.prorabprime.feature.map

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
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.ui.UiText

@OptIn(ExperimentalTestApi::class)
class MapContentTest {

    private val events = mutableListOf<MapEvent>()
    private val opened = mutableListOf<String>()
    private var backs = 0

    private val kitchen = MapObjectUi("a", "Кухня", "Тверская, 5", GeoPoint(55.76, 37.61))
    private val village = MapObjectUi("b", "Деревня Гадюкино", null, null)

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: MapState) = setContent {
        ProrabTheme { MapContent(state, { events += it }, { opened += it }, { backs++ }) }
    }

    @Test
    fun `the map shows its attribution and the pins' controls`() = runComposeUiTest {
        show(MapState(status = MapStatus.Content, located = persistentListOf(kitchen)))

        onNodeWithText("© участники OpenStreetMap").assertIsDisplayed()
        onNodeWithContentDescription("Приблизить").assertIsDisplayed()
        onNodeWithContentDescription("Отдалить").assertIsDisplayed()
        onNodeWithContentDescription("Показать все объекты").assertIsDisplayed()
    }

    @Test
    fun `the selected object has a card that opens it and closes`() = runComposeUiTest {
        show(MapState(status = MapStatus.Content, located = persistentListOf(kitchen), selectedId = "a"))

        onNodeWithText("Тверская, 5").assertIsDisplayed()
        onNodeWithText("Открыть объект").performClick()
        onNodeWithContentDescription("Закрыть").performClick()

        assertThat(opened).containsExactly("a")
        assertThat(events).containsExactly(MapEvent.SelectionCleared)
    }

    @Test
    fun `objects without a pin are counted and their sheet offers to find each`() = runComposeUiTest {
        show(
            MapState(
                status = MapStatus.Content,
                located = persistentListOf(kitchen),
                unlocated = persistentListOf(village),
                showUnlocated = true,
            ),
        )

        onNodeWithText("Без метки на карте: 1").assertIsDisplayed()
        onNodeWithText("Деревня Гадюкино").assertIsDisplayed()
        onNodeWithText("Найти на карте").performClick()

        assertThat(events).containsExactly(MapEvent.FindClicked("b"))
    }

    @Test
    fun `the chip opens the unlocated sheet`() = runComposeUiTest {
        show(
            MapState(
                status = MapStatus.Content,
                located = persistentListOf(kitchen),
                unlocated = persistentListOf(village),
            ),
        )

        onNodeWithText("Без метки на карте: 1").performClick()

        assertThat(events).containsExactly(MapEvent.UnlocatedOpened)
    }

    @Test
    fun `no objects says so, an error offers a retry and back leaves`() = runComposeUiTest {
        show(MapState(status = MapStatus.Content))
        onNodeWithText("Пока нет ни одного объекта").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry and back leaves`() = runComposeUiTest {
        show(MapState(status = MapStatus.Error(UiText.Raw("Сервер не отвечает"))))

        onNodeWithText("Повторить").performClick()
        onNodeWithContentDescription("Назад").performClick()

        assertThat(events).containsExactly(MapEvent.Retry)
        assertThat(backs).isEqualTo(1)
    }
}
