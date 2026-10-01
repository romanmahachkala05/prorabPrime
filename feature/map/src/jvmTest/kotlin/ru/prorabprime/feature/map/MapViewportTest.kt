package ru.prorabprime.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.domain.model.GeoPoint

class MapViewportTest {

    private val view = MapViewport.ViewSize(width = 1000f, height = 800f, tilePx = 256f)

    @Test
    fun `a drag moves the centre against the finger`() {
        val viewport = MapViewport(0.5, 0.5, 5f)
        val world = WebMercator.worldPx(5f, view.tilePx)

        viewport.transform(500f to 400f, 100f to 0f, 1f, view)

        assertThat(viewport.centerX).isWithin(1e-9).of(0.5 - 100.0 / world)
        assertThat(viewport.centerY).isWithin(1e-9).of(0.5)
        assertThat(viewport.zoom).isEqualTo(5f)
    }

    @Test
    fun `a pinch keeps the point under the fingers where it was`() {
        val viewport = MapViewport(0.3, 0.4, 6f)
        val finger = 700f to 200f
        val before = worldUnder(viewport, finger)

        viewport.transform(finger, 0f to 0f, 2f, view)

        assertThat(viewport.zoom).isWithin(1e-5f).of(7f)
        val after = worldUnder(viewport, finger)
        assertThat(after.first).isWithin(1e-9).of(before.first)
        assertThat(after.second).isWithin(1e-9).of(before.second)
    }

    @Test
    fun `zoom and centre stay inside their limits`() {
        val viewport = MapViewport(0.99, 0.01, 18.5f)

        viewport.transform(500f to 400f, -100_000f to 100_000f, 100f, view)
        assertThat(viewport.zoom).isEqualTo(WebMercator.MAX_ZOOM)
        assertThat(viewport.centerX).isAtMost(1.0)
        assertThat(viewport.centerY).isAtLeast(0.0)

        viewport.zoomBy(-100f)
        assertThat(viewport.zoom).isEqualTo(WebMercator.MIN_ZOOM)
        viewport.zoomBy(100f)
        assertThat(viewport.zoom).isEqualTo(WebMercator.MAX_ZOOM)
    }

    @Test
    fun `fitting centres on the pins and zooms out until they all show`() {
        val viewport = MapViewport()
        val moscow = GeoPoint(55.75, 37.62)
        val tver = GeoPoint(56.86, 35.9)

        viewport.fit(listOf(moscow, tver), view)

        assertThat(viewport.isFitted).isTrue()
        val world = WebMercator.worldPx(viewport.zoom, view.tilePx)
        for (point in listOf(moscow, tver)) {
            val x = (WebMercator.x(point.longitude) - viewport.centerX) * world + view.width / 2
            val y = (WebMercator.y(point.latitude) - viewport.centerY) * world + view.height / 2
            assertThat(x).isAtLeast(0.0)
            assertThat(x).isAtMost(view.width.toDouble())
            assertThat(y).isAtLeast(0.0)
            assertThat(y).isAtMost(view.height.toDouble())
        }
    }

    @Test
    fun `one pin is looked at closely, and no pins leave the map where it is`() {
        val viewport = MapViewport(0.4, 0.4, 5f)
        viewport.fit(emptyList(), view)
        assertThat(viewport.isFitted).isFalse()
        assertThat(viewport.zoom).isEqualTo(5f)

        viewport.fit(listOf(GeoPoint(55.75, 37.62)), view)
        assertThat(viewport.zoom).isEqualTo(16f)
        assertThat(viewport.centerX).isWithin(1e-9).of(WebMercator.x(37.62))
    }

    @Test
    fun `asking for a fit again lets the map fit once more`() {
        val viewport = MapViewport()
        viewport.fit(listOf(GeoPoint(55.75, 37.62)), view)

        viewport.requestFit()

        assertThat(viewport.isFitted).isFalse()
    }

    @Test
    fun `before any pin the map looks at Moscow`() {
        val viewport = MapViewport()

        assertThat(viewport.centerX).isWithin(1e-9).of(WebMercator.x(37.62))
        assertThat(viewport.centerY).isWithin(1e-9).of(WebMercator.y(55.75))
        assertThat(viewport.zoom).isEqualTo(5f)
        assertThat(viewport.isFitted).isFalse()
    }

    private fun worldUnder(viewport: MapViewport, finger: Pair<Float, Float>): Pair<Double, Double> {
        val world = WebMercator.worldPx(viewport.zoom, view.tilePx)
        return (viewport.centerX + (finger.first - view.width / 2) / world) to
            (viewport.centerY + (finger.second - view.height / 2) / world)
    }
}
