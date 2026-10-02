package ru.prorabprime.feature.settings

/** The words for a size, from the screen's resources, so the arithmetic stays plain and testable. */
internal data class ByteUnits(
    val bytes: String,
    val kilobytes: String,
    val megabytes: String,
    val gigabytes: String,
)

private const val STEP = 1024L
private const val TENTHS = 10L

/**
 * "120 МБ", "1 ГБ", "1,5 ГБ": a size in the largest unit it fills, with one decimal below ten of it and none
 * when the decimal is zero. Done in whole numbers: a format call differs between platforms, and this is shown
 * in the browser too.
 */
internal fun formatBytes(bytes: Long, units: ByteUnits): String {
    val ladder = listOf(units.bytes, units.kilobytes, units.megabytes, units.gigabytes)
    var scale = 1L
    var rung = 0
    while (rung < ladder.lastIndex && bytes >= scale * STEP) {
        scale *= STEP
        rung++
    }
    if (rung == 0) return "${bytes.coerceAtLeast(0)} ${ladder[0]}"
    val tenths = (bytes * TENTHS + scale / 2) / scale
    val whole = tenths / TENTHS
    val decimal = tenths % TENTHS
    val number = if (whole >= TENTHS || decimal == 0L) "$whole" else "$whole,$decimal"
    return "$number ${ladder[rung]}"
}
