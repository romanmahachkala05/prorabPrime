package ru.prorabprime.data.image

import kotlin.math.max
import kotlin.math.roundToInt

/** The long side an uploaded photo is scaled down to. */
const val MAX_UPLOAD_SIDE = 2048

const val UPLOAD_JPEG_QUALITY = 85

/**
 * The largest power of two to decode at that still leaves the long side at least [maxSide], so
 * a 48 MP camera photo is never decoded at full size just to be shrunk.
 */
fun sampleSizeFor(
    width: Int,
    height: Int,
    maxSide: Int = MAX_UPLOAD_SIDE,
): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
    return sample
}

/** [width]×[height] scaled so the long side is at most [maxSide]; never scaled up. */
fun fitWithin(
    width: Int,
    height: Int,
    maxSide: Int = MAX_UPLOAD_SIDE,
): Pair<Int, Int> {
    val longSide = max(width, height)
    if (longSide <= maxSide) return width to height
    val scale = maxSide.toDouble() / longSide
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}
