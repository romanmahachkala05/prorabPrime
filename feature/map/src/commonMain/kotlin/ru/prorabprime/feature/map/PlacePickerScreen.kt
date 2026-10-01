package ru.prorabprime.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.Button
import ru.prorabprime.designsystem.components.FilledIconButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.GeoPoint
import ru.prorabprime.feature.map.resources.Res
import ru.prorabprime.feature.map.resources.map_attribution
import ru.prorabprime.feature.map.resources.map_zoom_in
import ru.prorabprime.feature.map.resources.map_zoom_out
import ru.prorabprime.feature.map.resources.picker_back
import ru.prorabprime.feature.map.resources.picker_confirm
import ru.prorabprime.feature.map.resources.picker_hint
import ru.prorabprime.feature.map.resources.picker_pin
import ru.prorabprime.feature.map.resources.picker_title

/**
 * Pick a place by moving the map under a pin that stays in the middle. [latitude] and [longitude]
 * are where the object already is, if it is anywhere; [onDone] is called once the choice is handed over.
 */
@Composable
fun PlacePickerScreen(
    latitude: Double?,
    longitude: Double?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PlacePickerViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    val start = if (latitude != null && longitude != null) GeoPoint(latitude, longitude) else null
    PlacePickerContent(state, start, viewModel::confirm, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacePickerContent(
    state: PlacePickerState,
    start: GeoPoint?,
    onConfirm: (GeoPoint) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.picker_back))
                    }
                },
            )
        },
    ) { padding ->
        val viewport = rememberSaveable(saver = MapViewport.Saver) { startingViewport(start) }
        Box(Modifier.padding(padding).fillMaxSize()) {
            TileMap(
                markers = persistentListOf(),
                selectedId = null,
                viewport = viewport,
                onMarkerClick = {},
                modifier = Modifier.fillMaxSize(),
            )
            CenterPin(Modifier.align(Alignment.Center))
            ZoomButtons(viewport, Modifier.align(Alignment.CenterEnd))
            Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = HINT_ALPHA),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(Res.string.picker_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Spacing.m),
                    )
                }
                Button(
                    onClick = { onConfirm(WebMercator.point(viewport.centerX, viewport.centerY)) },
                    enabled = !state.isLooking,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
                ) { Text(stringResource(Res.string.picker_confirm)) }
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = HINT_ALPHA)) {
                    Text(
                        stringResource(Res.string.map_attribution),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
                    )
                }
            }
        }
    }
}

/** Opens on the object's own point, close in; with none, on the home city. */
private fun startingViewport(start: GeoPoint?): MapViewport = if (start != null) {
    MapViewport(WebMercator.x(start.longitude), WebMercator.y(start.latitude), CLOSE_ZOOM)
} else {
    MapViewport(zoom = CITY_ZOOM)
}

/** The tip of the pin, not its middle, is the chosen point, so the icon sits a half above the centre. */
@Composable
private fun CenterPin(modifier: Modifier) {
    val description = stringResource(Res.string.picker_pin)
    Icon(
        Icons.Default.LocationOn,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.error,
        modifier = modifier.size(PIN_SIZE).offset(y = -PIN_SIZE / 2),
    )
}

@Composable
private fun ZoomButtons(viewport: MapViewport, modifier: Modifier) {
    Column(modifier.padding(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        FilledIconButton(onClick = { viewport.zoomBy(1f) }) {
            Icon(Icons.Default.Add, stringResource(Res.string.map_zoom_in))
        }
        val zoomOut = stringResource(Res.string.map_zoom_out)
        FilledIconButton(onClick = { viewport.zoomBy(-1f) }) {
            Text("−", Modifier.semantics { contentDescription = zoomOut }, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private val PIN_SIZE = 48.dp
private const val HINT_ALPHA = 0.85f
private const val CLOSE_ZOOM = 17f
private const val CITY_ZOOM = 12f
