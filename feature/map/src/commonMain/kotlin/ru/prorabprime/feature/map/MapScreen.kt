package ru.prorabprime.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.map.resources.Res
import ru.prorabprime.feature.map.resources.map_attribution
import ru.prorabprime.feature.map.resources.map_back
import ru.prorabprime.feature.map.resources.map_close_card
import ru.prorabprime.feature.map.resources.map_empty
import ru.prorabprime.feature.map.resources.map_find
import ru.prorabprime.feature.map.resources.map_fit
import ru.prorabprime.feature.map.resources.map_open_object
import ru.prorabprime.feature.map.resources.map_title
import ru.prorabprime.feature.map.resources.map_unlocated
import ru.prorabprime.feature.map.resources.map_unlocated_hint
import ru.prorabprime.feature.map.resources.map_unlocated_title
import ru.prorabprime.feature.map.resources.map_zoom_in
import ru.prorabprime.feature.map.resources.map_zoom_out
import ru.prorabprime.ui.UiText

/** [onOpenObject] gets the object's id, for its card. */
@Composable
fun MapScreen(
    onOpenObject: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MapViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MapContent(state, viewModel::onEvent, onOpenObject, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MapContent(
    state: MapState,
    onEvent: (MapEvent) -> Unit,
    onOpenObject: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.map_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            MapStatus.Content -> MapBody(state, onEvent, onOpenObject, Modifier.padding(padding))

            MapStatus.Loading -> LoadingBox(Modifier.padding(padding))

            is MapStatus.Error -> ErrorMessage(
                status.message,
                onRetry = { onEvent(MapEvent.Retry) },
                modifier = Modifier.padding(padding),
            )
        }
    }
    if (state.showUnlocated) UnlocatedSheet(state.unlocated, onEvent)
}

@Composable
private fun MapBody(
    state: MapState,
    onEvent: (MapEvent) -> Unit,
    onOpenObject: (objectId: String) -> Unit,
    modifier: Modifier,
) {
    if (state.located.isEmpty() && state.unlocated.isEmpty()) {
        EmptyMessage(UiText.Resource(Res.string.map_empty), modifier = modifier)
        return
    }
    val viewport = rememberSaveable(saver = MapViewport.Saver) { MapViewport() }
    val markers = state.located.mapNotNull { item -> item.point?.let { MapMarker(item.id, item.title, it) } }
        .toImmutableList()
    Box(modifier.fillMaxSize()) {
        TileMap(
            markers = markers,
            selectedId = state.selectedId,
            viewport = viewport,
            onMarkerClick = { onEvent(MapEvent.MarkerTapped(it)) },
            modifier = Modifier.fillMaxSize(),
        )
        Controls(viewport, Modifier.align(Alignment.CenterEnd))
        if (state.unlocated.isNotEmpty()) {
            AssistChip(
                onClick = { onEvent(MapEvent.UnlocatedOpened) },
                label = { Text(stringResource(Res.string.map_unlocated, state.unlocated.size)) },
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.s),
            )
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            state.selected?.let { SelectedCard(it, onEvent, onOpenObject) }
            Attribution()
        }
    }
}

@Composable
private fun Controls(viewport: MapViewport, modifier: Modifier) {
    Column(modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilledIconButton(onClick = { viewport.zoomBy(1f) }) {
            Icon(Icons.Default.Add, stringResource(Res.string.map_zoom_in))
        }
        val zoomOut = stringResource(Res.string.map_zoom_out)
        FilledIconButton(onClick = { viewport.zoomBy(-1f) }) {
            // The core icon set has a plus but no minus.
            Text(
                "−",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    contentDescription = zoomOut
                },
            )
        }
        FilledIconButton(onClick = viewport::requestFit) {
            Icon(Icons.Default.Refresh, stringResource(Res.string.map_fit))
        }
    }
}

@Composable
private fun SelectedCard(
    item: MapObjectUi,
    onEvent: (MapEvent) -> Unit,
    onOpenObject: (objectId: String) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(Spacing.s)) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                item.address?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { onOpenObject(item.id) }) { Text(stringResource(Res.string.map_open_object)) }
            IconButton(onClick = { onEvent(MapEvent.SelectionCleared) }) {
                Icon(Icons.Default.Close, stringResource(Res.string.map_close_card))
            }
        }
    }
}

/** The licence of the map data asks to be shown wherever the map is. */
@Composable
private fun Attribution() {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA)) {
        Text(
            stringResource(Res.string.map_attribution),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlocatedSheet(items: ImmutableList<MapObjectUi>, onEvent: (MapEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(MapEvent.UnlocatedClosed) }) {
        Column(Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.l)) {
            Text(stringResource(Res.string.map_unlocated_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.map_unlocated_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                items(items, key = { it.id }) { item ->
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = item.address?.let { { Text(it) } },
                        trailingContent = {
                            TextButton(onClick = { onEvent(MapEvent.FindClicked(item.id)) }) {
                                Text(stringResource(Res.string.map_find))
                            }
                        },
                    )
                }
            }
        }
    }
}

private const val ATTRIBUTION_ALPHA = 0.8f
