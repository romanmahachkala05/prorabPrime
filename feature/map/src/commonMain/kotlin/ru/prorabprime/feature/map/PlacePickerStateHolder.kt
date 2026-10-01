package ru.prorabprime.feature.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.ui.StateOwner

internal interface IPlacePickerStateHolder : StateOwner<PlacePickerState> {
    fun setLooking(looking: Boolean)

    fun markDone()
}

internal class PlacePickerStateHolder : IPlacePickerStateHolder {
    private val _state = MutableStateFlow(PlacePickerState())
    override val state: StateFlow<PlacePickerState> = _state.asStateFlow()

    override fun setLooking(looking: Boolean) = _state.update { it.copy(isLooking = looking) }

    override fun markDone() = _state.update { it.copy(isLooking = false, isDone = true) }
}
