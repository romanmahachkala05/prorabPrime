package ru.prorabprime.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import ru.prorabprime.domain.model.GeoPoint

/**
 * OpenStreetMap's tiles, laid out and moved by hand: drag and pinch to look around, pins on top.
 * It needs nothing from the platform, so the web client will draw the same map.
 */
@Composable
internal fun TileMap(
    markers: ImmutableList<MapMarker>,
    selectedId: String?,
    viewport: MapViewport,
    onMarkerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tilePx = WebMercator.TILE_SIZE * density.density
    BoxWithConstraints(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
        val view = MapViewport.ViewSize(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), tilePx)
        FitOnce(markers, viewport, view)
        Box(
            Modifier.matchParentSize().pointerInput(view) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    viewport.transform(centroid.x to centroid.y, pan.x to pan.y, zoom, view)
                }
            },
        ) {
            Tiles(viewport, view)
            markers.forEach { marker ->
                Pin(marker, selected = marker.id == selectedId, viewport, view, onMarkerClick)
            }
        }
    }
}

/** The first time pins arrive, the map looks at all of them; after that it stays where the user left it. */
@Composable
private fun FitOnce(
    markers: ImmutableList<MapMarker>,
    viewport: MapViewport,
    view: MapViewport.ViewSize,
) {
    LaunchedEffect(markers.isNotEmpty(), view.width, view.height, viewport.isFitted) {
        if (!viewport.isFitted && markers.isNotEmpty()) viewport.fit(markers.map { it.point }, view)
    }
}

@Composable
private fun Tiles(viewport: MapViewport, view: MapViewport.ViewSize) {
    val tileZoom = floor(viewport.zoom).toInt()
    val perSide = WebMercator.tilesPerSide(tileZoom)
    val world = WebMercator.worldPx(viewport.zoom, view.tilePx)
    val tileSide = world / perSide
    val halfW = view.width / 2 / world
    val halfH = view.height / 2 / world
    val firstX = floor((viewport.centerX - halfW) * perSide).toInt().coerceAtLeast(0)
    val lastX = floor((viewport.centerX + halfW) * perSide).toInt().coerceAtMost(perSide - 1)
    val firstY = floor((viewport.centerY - halfH) * perSide).toInt().coerceAtLeast(0)
    val lastY = floor((viewport.centerY + halfH) * perSide).toInt().coerceAtMost(perSide - 1)
    // A pixel more than the tile is wide, so rounding never leaves a seam between two tiles.
    val sizeDp = with(LocalDensity.current) { (ceil(tileSide) + 1f).toDp() }
    for (tileX in firstX..lastX) {
        for (tileY in firstY..lastY) {
            val left = ((tileX.toDouble() / perSide - viewport.centerX) * world + view.width / 2).roundToInt()
            val top = ((tileY.toDouble() / perSide - viewport.centerY) * world + view.height / 2).roundToInt()
            AsyncImage(
                model = WebMercator.tileUrl(tileZoom, tileX, tileY),
                contentDescription = null,
                modifier = Modifier.offset { IntOffset(left, top) }.requiredSize(sizeDp),
            )
        }
    }
}

@Composable
private fun Pin(
    marker: MapMarker,
    selected: Boolean,
    viewport: MapViewport,
    view: MapViewport.ViewSize,
    onClick: (String) -> Unit,
) {
    val world = WebMercator.worldPx(viewport.zoom, view.tilePx)
    val x = ((WebMercator.x(marker.point.longitude) - viewport.centerX) * world + view.width / 2).roundToInt()
    val y = ((WebMercator.y(marker.point.latitude) - viewport.centerY) * world + view.height / 2).roundToInt()
    if (isFarOffScreen(x, y, view)) return
    val pinSize = if (selected) SELECTED_PIN else PIN
    val half = with(LocalDensity.current) { (pinSize / 2).roundToPx() }
    val full = with(LocalDensity.current) { pinSize.roundToPx() }
    // The icon's tip, not its middle, is the point on the map.
    Column(
        modifier = Modifier.offset { IntOffset(x - half, y - full) }.clickable { onClick(marker.id) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = marker.title,
            tint = if (selected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(pinSize),
        )
    }
    if (selected || viewport.zoom >= LABEL_ZOOM) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = LABEL_ALPHA),
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.offset { IntOffset(x - LABEL_HALF_WIDTH_PX, y + LABEL_GAP_PX) },
        ) {
            Text(
                marker.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.size(width = LABEL_WIDTH, height = LABEL_HEIGHT).clickable { onClick(marker.id) },
            )
        }
    }
}

/** A pin well outside the view is not worth composing. */
private fun isFarOffScreen(
    x: Int,
    y: Int,
    view: MapViewport.ViewSize,
): Boolean = x !in -OFF_SCREEN_PX..(view.width + OFF_SCREEN_PX).toInt() ||
    y !in -OFF_SCREEN_PX..(view.height + OFF_SCREEN_PX).toInt()

private val PIN = 36.dp
private val SELECTED_PIN = 48.dp
private val LABEL_WIDTH = 96.dp
private val LABEL_HEIGHT = 18.dp
private const val OFF_SCREEN_PX = 200
private const val LABEL_ZOOM = 13f
private const val LABEL_ALPHA = 0.85f
private const val LABEL_HALF_WIDTH_PX = 120
private const val LABEL_GAP_PX = 4
