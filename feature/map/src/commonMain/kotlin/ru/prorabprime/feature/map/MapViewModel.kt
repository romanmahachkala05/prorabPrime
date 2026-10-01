package ru.prorabprime.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.GeocodeObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.feature.map.resources.Res
import ru.prorabprime.feature.map.resources.map_find_requested
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching
import ru.prorabprime.ui.toUiText

internal class MapViewModel(
    private val stateHolder: IMapStateHolder,
    private val observeObjects: ObserveObjectsUseCase,
    private val geocodeObject: GeocodeObjectUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<MapState> by stateHolder {

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: MapEvent) {
        when (event) {
            MapEvent.Retry -> {
                stateHolder.showLoading()
                load()
            }

            is MapEvent.MarkerTapped -> stateHolder.select(event.objectId)

            MapEvent.SelectionCleared -> stateHolder.select(null)

            MapEvent.UnlocatedOpened -> stateHolder.setShowUnlocated(true)

            MapEvent.UnlocatedClosed -> stateHolder.setShowUnlocated(false)

            is MapEvent.FindClicked -> find(event.objectId)
        }
    }

    /** The repository reloads this after any write, so a freshly found address gets its pin by itself. */
    private fun load() {
        loadJob?.cancel()
        loadJob = observeObjects(ObjectQuery())
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    private fun render(result: Result<ImmutableList<ObjectSummary>>) {
        result
            .onSuccess { objects ->
                val (located, unlocated) = objects.map { it.toUi() }.partition { it.point != null }
                stateHolder.showObjects(located.toImmutableList(), unlocated.toImmutableList())
            }
            .onFailure { failure -> viewModelScope.launch { onLoadFailure(failure.asAppError()) } }
    }

    /** A failed reload keeps the pins on screen; only a first load takes the screen over. */
    private suspend fun onLoadFailure(error: AppError) {
        if (state.value.status == MapStatus.Content) {
            notifier.showError(error.toUiText())
        } else {
            stateHolder.showError(error.toUiText())
        }
    }

    private fun find(objectId: String) {
        launchCatching(onFailure = { notifier.showError(it.asAppError().toUiText()) }) {
            geocodeObject(ObjectId(objectId))
                .onSuccess { notifier.showSuccess(UiText.Resource(Res.string.map_find_requested)) }
                .onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }
}

private fun ObjectSummary.toUi() = MapObjectUi(
    id = id.value,
    title = displayTitle,
    address = address.takeIf { title != null },
    point = point,
)
