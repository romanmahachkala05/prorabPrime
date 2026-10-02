package ru.prorabprime.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebMercatorTest {

    @Test
    fun `the centre of the world is the equator on the prime meridian`() {
        assertThat(WebMercator.x(0.0)).isWithin(EPS).of(0.5)
        assertThat(WebMercator.y(0.0)).isWithin(EPS).of(0.5)
        assertThat(WebMercator.x(-180.0)).isWithin(EPS).of(0.0)
        assertThat(WebMercator.x(180.0)).isWithin(EPS).of(1.0)
    }

    @Test
    fun `north is up, so a higher latitude has a smaller y`() {
        assertThat(WebMercator.y(60.0)).isLessThan(WebMercator.y(30.0))
        assertThat(WebMercator.y(-30.0)).isGreaterThan(0.5)
    }

    @Test
    fun `a known place lands on its known tile`() {
        // Red Square, Moscow: at zoom 10 OpenStreetMap shows it on tile 619/320.
        val x = WebMercator.x(37.6203)
        val y = WebMercator.y(55.7539)
        val tiles = WebMercator.tilesPerSide(10)

        assertThat((x * tiles).toInt()).isEqualTo(619)
        assertThat((y * tiles).toInt()).isEqualTo(320)
    }

    @Test
    fun `the poles are cut off where the projection stops`() {
        assertThat(WebMercator.y(90.0)).isWithin(EPS).of(0.0)
        assertThat(WebMercator.y(-90.0)).isWithin(EPS).of(1.0)
    }

    @Test
    fun `a point survives the round trip`() {
        val point = WebMercator.point(WebMercator.x(37.6203), WebMercator.y(55.7539))

        assertThat(point.latitude).isWithin(1e-6).of(55.7539)
        assertThat(point.longitude).isWithin(1e-6).of(37.6203)
    }

    @Test
    fun `the world doubles with every zoom level, and tiles are addressed on osm`() {
        assertThat(WebMercator.worldPx(3f, 256f)).isEqualTo(2048f)
        assertThat(WebMercator.worldPx(4f, 256f)).isEqualTo(4096f)
        assertThat(WebMercator.tilesPerSide(3)).isEqualTo(8)
        assertThat(WebMercator.tileUrl(10, 619, 320)).isEqualTo("https://tile.openstreetmap.org/10/619/320.png")
    }

    private companion object {
        const val EPS = 1e-9
    }
}
