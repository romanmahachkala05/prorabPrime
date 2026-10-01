package ru.prorabprime.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.prorabprime.domain.usecase.DiscardFailedChangeUseCase
import ru.prorabprime.domain.usecase.ObserveFailedChangesUseCase
import ru.prorabprime.domain.usecase.ObserveSyncStatusUseCase
import ru.prorabprime.domain.usecase.RetryFailedChangesUseCase
import ru.prorabprime.domain.usecase.SyncNowUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.launchCatching

/** What the screens need of the phone's sync: whether to say something, and the list of refused changes. */
internal class SyncViewModel(
    private val stateHolder: ISyncStateHolder,
    observeStatus: ObserveSyncStatusUseCase,
    observeFailed: ObserveFailedChangesUseCase,
    private val syncNow: SyncNowUseCase,
    private val retry: RetryFailedChangesUseCase,
    private val discard: DiscardFailedChangeUseCase,
) : ViewModel(),
    StateOwner<SyncUiState> by stateHolder {

    init {
        observeStatus().onEach(stateHolder::showStatus).launchIn(viewModelScope)
        observeFailed().onEach(stateHolder::showFailed).launchIn(viewModelScope)
    }

    fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.ChipClicked ->
                if (state.value.failed.isNotEmpty()) {
                    stateHolder.setSheetOpen(true)
                } else {
                    launchCatching({
                    }) { syncNow() }
                }

            SyncEvent.SheetDismissed -> stateHolder.setSheetOpen(false)

            is SyncEvent.RetryClicked -> launchCatching({}) { retry(event.id) }

            is SyncEvent.DiscardClicked -> launchCatching({}) { discard(event.id) }
        }
    }
}
