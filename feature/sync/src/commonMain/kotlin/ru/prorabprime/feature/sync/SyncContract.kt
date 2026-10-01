package ru.prorabprime.feature.sync

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus

@Immutable
internal data class SyncUiState(
    val status: SyncStatus = SyncStatus(),
    val failed: ImmutableList<FailedChange> = persistentListOf(),
    val sheetOpen: Boolean = false,
)

internal sealed interface SyncEvent {
    /** The chip: opens the list of refused changes, or asks for a sync when there are none. */
    data object ChipClicked : SyncEvent

    data object SheetDismissed : SyncEvent

    /** One refused change, or all of them when [id] is null. */
    data class RetryClicked(
        val id: Long?,
    ) : SyncEvent

    data class DiscardClicked(
        val id: Long,
    ) : SyncEvent
}
