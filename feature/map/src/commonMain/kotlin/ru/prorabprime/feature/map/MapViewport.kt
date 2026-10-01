package ru.prorabprime.feature.map

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import ru.prorabprime.domain.model.GeoPoint

/**
 * Where the map is looking: the centre in the unit-square world and how far in. View state, like
 * a scroll position, so it lives in the composition and survives rotation, not in the ViewModel.
 */
@Stable
internal class MapViewport(
    centerX: Double = WORLD_CENTRE,
    centerY: Double = WORLD_CENTRE,
    zoom: Float = DEFAULT_ZOOM,
) {
    var centerX by mutableStateOf(centerX)
        private set
    var centerY by mutableStateOf(centerY)
        private set
    var zoom by mutableStateOf(zoom.coerceIn(WebMercator.MIN_ZOOM, WebMercator.MAX_ZOOM))
        private set

    /** Whether the map has already been fitted to the pins, which happens once, when they first arrive. */
    var isFitted by mutableStateOf(false)
        private set

    /** A pinch and drag: [zoomFactor] scales around the fingers' [centroid], then everything moves by [pan]. */
    fun transform(
        centroid: Pair<Float, Float>,
        pan: Pair<Float, Float>,
        zoomFactor: Float,
        view: ViewSize,
    ) {
        val oldWorld = WebMercator.worldPx(zoom, view.tilePx)
        val underFingersX = centerX + (centroid.first - view.width / 2) / oldWorld
        val underFingersY = centerY + (centroid.second - view.height / 2) / oldWorld
        val newZoom = (zoom + log2(zoomFactor)).coerceIn(WebMercator.MIN_ZOOM, WebMercator.MAX_ZOOM)
        val newWorld = WebMercator.worldPx(newZoom, view.tilePx)
        centerX = (underFingersX - (centroid.first + pan.first - view.width / 2) / newWorld).coerceIn(0.0, 1.0)
        centerY = (underFingersY - (centroid.second + pan.second - view.height / 2) / newWorld).coerceIn(0.0, 1.0)
        zoom = newZoom
    }

    /** Asks for the first look again; the map does it as soon as it next composes. */
    fun requestFit() {
        isFitted = false
    }

    /** The zoom buttons: one level in or out around the middle. */
    fun zoomBy(levels: Float) {
        zoom = (zoom + levels).coerceIn(WebMercator.MIN_ZOOM, WebMercator.MAX_ZOOM)
    }

    /** Looks at all of [points]; one point is looked at closely. Does nothing without points. */
    fun fit(points: List<GeoPoint>, view: ViewSize) {
        if (points.isEmpty() || view.width <= 0f || view.height <= 0f) return
        val xs = points.map { WebMercator.x(it.longitude) }
        val ys = points.map { WebMercator.y(it.latitude) }
        centerX = (xs.min() + xs.max()) / 2
        centerY = (ys.min() + ys.max()) / 2
        val spanX = max(xs.max() - xs.min(), MIN_SPAN)
        val spanY = max(ys.max() - ys.min(), MIN_SPAN)
        val zoomX = log2((view.width * FIT_FILL / (spanX * view.tilePx)).toFloat())
        val zoomY = log2((view.height * FIT_FILL / (spanY * view.tilePx)).toFloat())
        zoom = min(zoomX, zoomY).coerceIn(WebMercator.MIN_ZOOM, MAX_FIT_ZOOM)
        isFitted = true
    }

    /** The view's size in pixels and the pixel size of a tile at zoom 0. */
    data class ViewSize(
        val width: Float,
        val height: Float,
        val tilePx: Float,
    )

    companion object {
        private const val WORLD_CENTRE = 0.5
        private const val DEFAULT_ZOOM = 4f
        private const val MIN_SPAN = 1e-6
        private const val FIT_FILL = 0.7
        private const val MAX_FIT_ZOOM = 16f

        val Saver = listSaver<MapViewport, Any>(
            save = { listOf(it.centerX, it.centerY, it.zoom, it.isFitted) },
            restore = {
                MapViewport(it[0] as Double, it[1] as Double, it[2] as Float).also { viewport ->
                    viewport.isFitted = it[3] as Boolean
                }
            },
        )

        private fun log2(value: Float): Float = (ln(value.toDouble()) / ln(2.0)).toFloat()
    }
}
