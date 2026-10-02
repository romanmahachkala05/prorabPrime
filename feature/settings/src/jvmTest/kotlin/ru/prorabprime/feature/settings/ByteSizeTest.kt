package ru.prorabprime.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ByteSizeTest {

    private val units = ByteUnits(bytes = "Б", kilobytes = "КБ", megabytes = "МБ", gigabytes = "ГБ")

    private fun format(bytes: Long) = formatBytes(bytes, units)

    @Test
    fun `small sizes are in bytes`() {
        assertThat(format(0)).isEqualTo("0 Б")
        assertThat(format(1023)).isEqualTo("1023 Б")
    }

    @Test
    fun `a size takes the largest unit it fills`() {
        assertThat(format(1024)).isEqualTo("1 КБ")
        assertThat(format(1024L * 1024)).isEqualTo("1 МБ")
        assertThat(format(1024L * 1024 * 1024)).isEqualTo("1 ГБ")
    }

    @Test
    fun `one decimal below ten and none above, with a comma`() {
        assertThat(format(1536L * 1024)).isEqualTo("1,5 МБ")
        assertThat(format(120L * 1024 * 1024)).isEqualTo("120 МБ")
        assertThat(format(9_900_000)).isEqualTo("9,4 МБ")
        assertThat(format(15L * 1024 * 1024 + 300_000)).isEqualTo("15 МБ")
    }

    @Test
    fun `a size just under the next unit rounds up into it, not past it`() {
        assertThat(format(1024L * 1024 - 1)).isEqualTo("1024 КБ")
    }

    @Test
    fun `an amount of no use is shown as zero bytes`() {
        assertThat(format(-5)).isEqualTo("0 Б")
    }
}
