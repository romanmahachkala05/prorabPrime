package ru.prorabprime.feature.map

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IMapStateHolder : StateOwner<MapState> {
    /** The objects split into those with a pin and those without; a gone selection is dropped. */
    fun showObjects(located: ImmutableList<MapObjectUi>, unlocated: ImmutableList<MapObjectUi>)

    fun showLoading()

    fun showError(message: UiText)

    fun select(objectId: String?)

    fun setShowUnlocated(show: Boolean)
}

internal class MapStateHolder : IMapStateHolder {

    private val _state = MutableStateFlow(MapState())
    override val state: StateFlow<MapState> = _state.asStateFlow()

    override fun showObjects(located: ImmutableList<MapObjectUi>, unlocated: ImmutableList<MapObjectUi>) =
        _state.update { state ->
            state.copy(
                status = MapStatus.Content,
                located = located,
                unlocated = unlocated,
                selectedId = state.selectedId?.takeIf { id -> located.any { it.id == id } },
                showUnlocated = state.showUnlocated && unlocated.isNotEmpty(),
            )
        }

    override fun showLoading() = _state.update { it.copy(status = MapStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = MapStatus.Error(message)) }

    override fun select(objectId: String?) = _state.update { it.copy(selectedId = objectId) }

    override fun setShowUnlocated(show: Boolean) = _state.update { it.copy(showUnlocated = show) }
}
