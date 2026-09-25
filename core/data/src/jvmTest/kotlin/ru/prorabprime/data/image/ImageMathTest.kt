package ru.prorabprime.data.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageMathTest {

    @Test
    fun `a 48 MP photo is decoded at a quarter, still above the target`() {
        assertThat(sampleSizeFor(8000, 6000)).isEqualTo(2)
        assertThat(sampleSizeFor(9000, 6000)).isEqualTo(4)
    }

    @Test
    fun `a small photo is decoded at full size`() {
        assertThat(sampleSizeFor(1000, 800)).isEqualTo(1)
    }

    @Test
    fun `the long side is scaled to 2048 keeping the aspect ratio`() {
        assertThat(fitWithin(4000, 3000)).isEqualTo(2048 to 1536)
        assertThat(fitWithin(3000, 4000)).isEqualTo(1536 to 2048)
    }

    @Test
    fun `nothing is scaled up`() {
        assertThat(fitWithin(1024, 768)).isEqualTo(1024 to 768)
    }
}
