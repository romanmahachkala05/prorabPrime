package ru.prorabprime.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A place chosen on the map: the point, and the address found there when there was one. */
data class PickedPlace(
    val point: GeoPoint,
    val address: String?,
)

/**
 * Carries the choice of the map screen back to the form that asked for it: the two screens do
 * not know each other, and the form's view model outlives the map on top of it.
 */
class PickedPlaceStore {
    private val _place = MutableStateFlow<PickedPlace?>(null)
    val place: StateFlow<PickedPlace?> = _place.asStateFlow()

    fun put(place: PickedPlace) {
        _place.value = place
    }

    /** The form has taken what it was given. */
    fun clear() {
        _place.value = null
    }
}
