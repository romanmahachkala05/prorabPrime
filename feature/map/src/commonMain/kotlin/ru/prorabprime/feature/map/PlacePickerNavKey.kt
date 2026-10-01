package ru.prorabprime.feature.map

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The map that picks a place for an object; it opens on the given point when there is one. */
@Serializable
data class PlacePickerNavKey(
    val latitude: Double? = null,
    val longitude: Double? = null,
) : NavKey
