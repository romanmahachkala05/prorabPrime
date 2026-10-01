package ru.prorabprime.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.usecase.GeocodeObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.feature.map.resources.Res
import ru.prorabprime.feature.map.resources.map_find_requested
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectSummary
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository()
    private val notifier = FakeSnackbarNotifier()

    private val viewModel by lazy {
        MapViewModel(
            stateHolder = MapStateHolder(),
            observeObjects = ObserveObjectsUseCase(objects),
            geocodeObject = GeocodeObjectUseCase(objects),
            notifier = notifier,
        )
    }

    private val state get() = viewModel.state.value

    private fun withObjects() {
        objects.objects.value = listOf(
            anObjectSummary(
                id = "a",
                title = "Кухня",
                address = "Тверская, 5",
            ).copy(latitude = 55.76, longitude = 37.61),
            anObjectSummary(id = "b", address = "Деревня Гадюкино"),
        )
    }

    @Test
    fun `objects split into those with a pin and those without`() {
        withObjects()

        assertThat(state.status).isEqualTo(MapStatus.Content)
        assertThat(state.located.map { it.id }).containsExactly("a")
        assertThat(state.located.single().title).isEqualTo("Кухня")
        assertThat(state.unlocated.map { it.id }).containsExactly("b")
        assertThat(state.unlocated.single().point).isNull()
    }

    @Test
    fun `tapping a pin selects it, and clearing drops the selection`() {
        withObjects()

        viewModel.onEvent(MapEvent.MarkerTapped("a"))
        assertThat(state.selected?.id).isEqualTo("a")

        viewModel.onEvent(MapEvent.SelectionCleared)
        assertThat(state.selected).isNull()
    }

    @Test
    fun `a selected object that loses its pin is no longer selected`() {
        withObjects()
        viewModel.onEvent(MapEvent.MarkerTapped("a"))

        objects.objects.value = listOf(anObjectSummary(id = "a"))

        assertThat(state.selectedId).isNull()
        assertThat(state.unlocated.map { it.id }).containsExactly("a")
    }

    @Test
    fun `the unlocated list opens and closes, and closes itself once empty`() {
        withObjects()

        viewModel.onEvent(MapEvent.UnlocatedOpened)
        assertThat(state.showUnlocated).isTrue()
        viewModel.onEvent(MapEvent.UnlocatedClosed)
        assertThat(state.showUnlocated).isFalse()

        viewModel.onEvent(MapEvent.UnlocatedOpened)
        objects.objects.value = listOf(anObjectSummary(id = "a").copy(latitude = 1.0, longitude = 2.0))
        assertThat(state.showUnlocated).isFalse()
    }

    @Test
    fun `finding an object on the map asks the server and says so`() {
        withObjects()

        viewModel.onEvent(MapEvent.FindClicked("b"))

        assertThat(objects.geocoded).containsExactly(ObjectId("b"))
        assertThat(notifier.shown).containsExactly(UiText.Resource(Res.string.map_find_requested))
    }

    @Test
    fun `a failed find is shown`() {
        withObjects()
        objects.writeError = AppError.Network

        viewModel.onEvent(MapEvent.FindClicked("b"))

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }

    @Test
    fun `a failed first load takes over the screen, retry loads again, a failed reload only says so`() {
        objects.loadError.value = AppError.Network
        assertThat(state.status).isEqualTo(MapStatus.Error(AppError.Network.toUiText()))

        objects.loadError.value = null
        viewModel.onEvent(MapEvent.Retry)
        withObjects()
        assertThat(state.status).isEqualTo(MapStatus.Content)

        objects.loadError.value = AppError.Network
        assertThat(state.status).isEqualTo(MapStatus.Content)
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }
}
