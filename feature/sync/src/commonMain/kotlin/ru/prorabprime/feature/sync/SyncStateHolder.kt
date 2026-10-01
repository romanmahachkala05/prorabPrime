package ru.prorabprime.feature.sync

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.ui.StateOwner

internal interface ISyncStateHolder : StateOwner<SyncUiState> {
    fun showStatus(status: SyncStatus)

    /** The list of refused changes; with none left, the sheet that showed them closes. */
    fun showFailed(failed: ImmutableList<FailedChange>)

    fun setSheetOpen(open: Boolean)
}

internal class SyncStateHolder : ISyncStateHolder {
    private val _state = MutableStateFlow(SyncUiState())
    override val state: StateFlow<SyncUiState> = _state.asStateFlow()

    override fun showStatus(status: SyncStatus) = _state.update { it.copy(status = status) }

    override fun showFailed(failed: ImmutableList<FailedChange>) =
        _state.update { it.copy(failed = failed, sheetOpen = it.sheetOpen && failed.isNotEmpty()) }

    override fun setSheetOpen(open: Boolean) = _state.update { it.copy(sheetOpen = open) }
}
