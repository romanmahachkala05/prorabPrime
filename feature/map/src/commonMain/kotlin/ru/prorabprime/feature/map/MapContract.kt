package ru.prorabprime.feature.map

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface MapStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : MapStatus

    data object Loading : MapStatus

    data class Error(
        val message: UiText,
    ) : MapStatus
}

@Immutable
internal data class MapObjectUi(
    val id: String,
    val title: String,
    val address: String?,
    /** Null while the object's address has not been found on the map. */
    val point: GeoPoint?,
)

@Immutable
internal data class MapState(
    val status: MapStatus = MapStatus.Loading,
    val located: ImmutableList<MapObjectUi> = persistentListOf(),
    val unlocated: ImmutableList<MapObjectUi> = persistentListOf(),
    val selectedId: String? = null,
    val showUnlocated: Boolean = false,
) {
    val selected: MapObjectUi? get() = located.find { it.id == selectedId }
}

internal sealed interface MapEvent {
    data object Retry : MapEvent

    data class MarkerTapped(
        val objectId: String,
    ) : MapEvent

    data object SelectionCleared : MapEvent

    data object UnlocatedOpened : MapEvent

    data object UnlocatedClosed : MapEvent

    /** Asks the server to find this object's address on the map again. */
    data class FindClicked(
        val objectId: String,
    ) : MapEvent
}

/** A pin on the map. */
internal data class MapMarker(
    val id: String,
    val title: String,
    val point: GeoPoint,
)
