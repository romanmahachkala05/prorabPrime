package ru.prorabprime.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.model.PickedPlace
import ru.prorabprime.domain.model.PickedPlaceStore
import ru.prorabprime.domain.usecase.FindAddressUseCase
import ru.prorabprime.testing.FakePlacesRepository
import ru.prorabprime.testing.MainDispatcherRule

class PlacePickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val places = FakePlacesRepository(address = "Екатеринбург, улица Ленина, 5")
    private val store = PickedPlaceStore()
    private val point = GeoPoint(56.84, 60.61)

    private val viewModel by lazy {
        PlacePickerViewModel(PlacePickerStateHolder(), FindAddressUseCase(places), store)
    }

    @Test
    fun `confirming hands over the point with the address found there`() {
        viewModel.confirm(point)

        assertThat(places.asked).containsExactly(point)
        assertThat(store.place.value).isEqualTo(PickedPlace(point, "Екатеринбург, улица Ленина, 5"))
        assertThat(viewModel.state.value.isDone).isTrue()
    }

    @Test
    fun `a place with no address, or a failed lookup, still keeps the point`() {
        places.address = null
        viewModel.confirm(point)
        assertThat(store.place.value).isEqualTo(PickedPlace(point, null))
        assertThat(viewModel.state.value.isDone).isTrue()

        store.clear()
        places.error = AppError.Network
        PlacePickerViewModel(PlacePickerStateHolder(), FindAddressUseCase(places), store).confirm(point)
        assertThat(store.place.value).isEqualTo(PickedPlace(point, null))
    }

    @Test
    fun `nothing is handed over before the user confirms`() {
        assertThat(viewModel.state.value).isEqualTo(PlacePickerState())
        assertThat(store.place.value).isNull()
    }
}
