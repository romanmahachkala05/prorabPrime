package ru.prorabprime.feature.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.feature.sync.resources.Res
import ru.prorabprime.feature.sync.resources.sync_close
import ru.prorabprime.feature.sync.resources.sync_discard
import ru.prorabprime.feature.sync.resources.sync_failed
import ru.prorabprime.feature.sync.resources.sync_failed_hint
import ru.prorabprime.feature.sync.resources.sync_failed_title
import ru.prorabprime.feature.sync.resources.sync_offline
import ru.prorabprime.feature.sync.resources.sync_offline_pending
import ru.prorabprime.feature.sync.resources.sync_pending
import ru.prorabprime.feature.sync.resources.sync_retry
import ru.prorabprime.feature.sync.resources.sync_retry_all
import ru.prorabprime.feature.sync.resources.sync_sending
import ru.prorabprime.feature.sync.resources.sync_unauthorized

/**
 * The one small line the app says about the sync, wherever the user is: nothing while all is well, and
 * otherwise what is going on — no signal, changes waiting, changes refused. A tap sends now, or, when
 * changes were refused, opens the list of them. [onOpenSettings] is where a refused token is fixed.
 */
@Composable
fun SyncStatusHost(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: SyncViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SyncStatusContent(state, viewModel::onEvent, onOpenSettings, modifier)
}

@Composable
internal fun SyncStatusContent(
    state: SyncUiState,
    onEvent: (SyncEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = state.status
    val tone = status.tone()
    if (tone != null) {
        val colors = MaterialTheme.colorScheme
        val (container, content) = when (tone) {
            Tone.Problem -> colors.errorContainer to colors.onErrorContainer
            Tone.Offline -> colors.tertiaryContainer to colors.onTertiaryContainer
            Tone.Waiting -> colors.secondaryContainer to colors.onSecondaryContainer
        }
        Surface(
            color = container,
            contentColor = content,
            shape = MaterialTheme.shapes.small,
            shadowElevation = CHIP_ELEVATION,
            modifier = modifier.clickable {
                if (status.unauthorized && status.failed == 0) onOpenSettings() else onEvent(SyncEvent.ChipClicked)
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                ChipIcon(status, tone)
                Text(chipText(status), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (state.sheetOpen) FailedSheet(state.failed, onEvent)
}

@Composable
private fun ChipIcon(status: SyncStatus, tone: Tone) {
    when {
        tone == Tone.Problem -> Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(ICON))
        status.isSyncing -> CircularProgressIndicator(modifier = Modifier.size(ICON), strokeWidth = SPINNER_STROKE)
        else -> Icon(ProrabIcons.Schedule, contentDescription = null, modifier = Modifier.size(ICON))
    }
}

@Composable
private fun chipText(status: SyncStatus): String = when {
    status.failed > 0 -> stringResource(Res.string.sync_failed, status.failed)
    status.unauthorized -> stringResource(Res.string.sync_unauthorized)
    status.offline && status.pending > 0 -> stringResource(Res.string.sync_offline_pending, status.pending)
    status.offline -> stringResource(Res.string.sync_offline)
    status.isSyncing -> stringResource(Res.string.sync_sending, status.pending)
    else -> stringResource(Res.string.sync_pending, status.pending)
}

private enum class Tone { Problem, Offline, Waiting }

/** Null when there is nothing worth saying; a copy-down with nothing to send is not. */
private fun SyncStatus.tone(): Tone? = when {
    failed > 0 || unauthorized -> Tone.Problem
    offline -> Tone.Offline
    pending > 0 -> Tone.Waiting
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FailedSheet(failed: ImmutableList<FailedChange>, onEvent: (SyncEvent) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(SyncEvent.SheetDismissed) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            Text(stringResource(Res.string.sync_failed_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.sync_failed_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // As tall as its rows need, never so tall that it pushes the buttons below it out of the sheet.
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                failed.forEach { change -> FailedRow(change, onEvent) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onEvent(SyncEvent.RetryClicked(null)) }) {
                    Text(stringResource(Res.string.sync_retry_all))
                }
                TextButton(onClick = {
                    onEvent(SyncEvent.SheetDismissed)
                }) { Text(stringResource(Res.string.sync_close)) }
            }
        }
    }
}

@Composable
private fun FailedRow(change: FailedChange, onEvent: (SyncEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HorizontalDivider()
        Text(describe(change), style = MaterialTheme.typography.bodyLarge)
        Text(
            reasonText(change.reason),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            TextButton(onClick = { onEvent(SyncEvent.RetryClicked(change.id)) }) {
                Text(stringResource(Res.string.sync_retry))
            }
            TextButton(onClick = { onEvent(SyncEvent.DiscardClicked(change.id)) }) {
                Text(stringResource(Res.string.sync_discard), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val ICON = 16.dp
private val SPINNER_STROKE = 2.dp
private val CHIP_ELEVATION = 2.dp
