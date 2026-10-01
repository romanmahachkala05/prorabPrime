package ru.prorabprime.feature.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import ru.prorabprime.domain.model.GeoPoint

/**
 * The slippy-map projection OpenStreetMap's tiles use, with the world as the unit square: x runs
 * west to east and y north to south, both from 0 to 1. A tile at zoom `z` is `1 / 2^z` of a side.
 */
internal object WebMercator {
    /** North and south of this the projection stops being a square. */
    private const val MAX_LATITUDE = 85.05112878
    private const val DEGREES_IN_CIRCLE = 360.0
    private const val HALF_CIRCLE = 180.0
    private const val WORLD_MIDDLE = 0.5
    private const val MERCATOR_DIVISOR = 4

    /** The tile's side in logical pixels at a whole zoom level. */
    const val TILE_SIZE = 256

    const val MIN_ZOOM = 3f
    const val MAX_ZOOM = 19f

    fun x(longitude: Double): Double = (longitude + HALF_CIRCLE) / DEGREES_IN_CIRCLE

    fun y(latitude: Double): Double {
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val sinLat = sin(clamped * PI / HALF_CIRCLE)
        return (WORLD_MIDDLE - ln((1 + sinLat) / (1 - sinLat)) / (MERCATOR_DIVISOR * PI)).coerceIn(0.0, 1.0)
    }

    fun point(x: Double, y: Double): GeoPoint {
        val longitude = x * DEGREES_IN_CIRCLE - HALF_CIRCLE
        val radians = 2 * atan(exp((WORLD_MIDDLE - y) * 2 * PI)) - PI / 2
        return GeoPoint(radians * HALF_CIRCLE / PI, longitude)
    }

    /** Tiles along one side at a whole zoom level. */
    fun tilesPerSide(zoom: Int): Int = 1 shl zoom

    /** The world's side in pixels at a (fractional) zoom, for a tile of [tilePx] pixels. */
    fun worldPx(zoom: Float, tilePx: Float): Float = tilePx * 2.0.pow(zoom.toDouble()).toFloat()

    fun tileUrl(
        zoom: Int,
        x: Int,
        y: Int,
    ): String = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
}
