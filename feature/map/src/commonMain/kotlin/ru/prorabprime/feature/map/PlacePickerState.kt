package ru.prorabprime.feature.map

import androidx.compose.runtime.Immutable

@Immutable
internal data class PlacePickerState(
    /** The address under the pin is being asked for. */
    val isLooking: Boolean = false,
    /** The choice has been handed over; the screen can close. */
    val isDone: Boolean = false,
)
