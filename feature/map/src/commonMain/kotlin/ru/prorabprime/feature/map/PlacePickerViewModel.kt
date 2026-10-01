package ru.prorabprime.feature.map

import androidx.lifecycle.ViewModel
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.domain.model.PickedPlace
import ru.prorabprime.domain.model.PickedPlaceStore
import ru.prorabprime.domain.usecase.FindAddressUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.launchCatching

internal class PlacePickerViewModel(
    private val stateHolder: IPlacePickerStateHolder,
    private val findAddress: FindAddressUseCase,
    private val pickedPlace: PickedPlaceStore,
) : ViewModel(),
    StateOwner<PlacePickerState> by stateHolder {

    /**
     * Hands the point to the form, with the address found there. A lookup that fails or finds nothing
     * still keeps the point: the pin is what matters, and the address can be typed.
     */
    fun confirm(point: GeoPoint) {
        if (state.value.isLooking) return
        stateHolder.setLooking(true)
        launchCatching(onFailure = { give(point, address = null) }) {
            give(point, findAddress(point).getOrNull())
        }
    }

    private fun give(point: GeoPoint, address: String?) {
        pickedPlace.put(PickedPlace(point, address))
        stateHolder.markDone()
    }
}
