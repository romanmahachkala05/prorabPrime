package ru.prorabprime.server.storage

import com.google.common.truth.Truth.assertThat
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException

class JavaImageProcessorTest {

    private val processor = JavaImageProcessor(Dispatchers.Unconfined)

    private fun decode(bytes: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(bytes))

    @Test
    fun `a large JPEG keeps its size and gets a 400 px thumbnail`() = runTest {
        val image = processor.process(TestImages.jpeg(1600, 1200)).getOrThrow()

        assertThat(image.format).isEqualTo(ImageFormat.JPEG)
        assertThat(image.width to image.height).isEqualTo(1600 to 1200)
        val thumbnail = decode(image.thumbnail)
        assertThat(thumbnail.width to thumbnail.height).isEqualTo(400 to 300)
    }

    @Test
    fun `a small image is not scaled up`() = runTest {
        val thumbnail = decode(processor.process(TestImages.jpeg(100, 50)).getOrThrow().thumbnail)

        assertThat(thumbnail.width to thumbnail.height).isEqualTo(100 to 50)
    }

    @Test
    fun `a sideways EXIF orientation swaps the reported sides and the thumbnail`() = runTest {
        val image = processor.process(TestImages.jpeg(40, 20, orientation = 6)).getOrThrow()

        assertThat(image.width to image.height).isEqualTo(20 to 40)
        val thumbnail = decode(image.thumbnail)
        assertThat(thumbnail.width to thumbnail.height).isEqualTo(20 to 40)
    }

    @Test
    fun `a transparent PNG becomes a JPEG thumbnail`() = runTest {
        val image = processor.process(TestImages.png(60, 30)).getOrThrow()

        assertThat(image.format).isEqualTo(ImageFormat.PNG)
        assertThat(image.thumbnail.take(3)).containsExactly(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()).inOrder()
    }

    @Test
    fun `WebP is read`() = runTest {
        val image = processor.process(TestImages.webp).getOrThrow()

        assertThat(image.format).isEqualTo(ImageFormat.WEBP)
        assertThat(image.width to image.height).isEqualTo(1 to 1)
    }

    @Test
    fun `anything else is unsupported media`() = runTest {
        val result = processor.process("GIF89a, or a text file".toByteArray())

        assertThat((result.exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.UnsupportedMedia::class.java)
    }

    @Test
    fun `a file with an image signature that does not decode is unsupported`() = runTest {
        val truncated = TestImages.jpeg(100, 100).copyOf(10)

        assertThat((processor.process(truncated).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.UnsupportedMedia::class.java)
    }

    /** Red on the left, blue on the right; each orientation must move them as a viewer would. */
    @Test
    fun `each EXIF orientation turns the pixels upright`() {
        val source = BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, Color.RED.rgb)
            setRGB(1, 0, Color.BLUE.rgb)
        }
        fun colorsOf(image: BufferedImage) =
            (0 until image.height).flatMap { y -> (0 until image.width).map { x -> Color(image.getRGB(x, y)) } }

        val red = Color.RED
        val blue = Color.BLUE
        assertThat(colorsOf(orient(source, 1))).containsExactly(red, blue).inOrder()
        assertThat(colorsOf(orient(source, 2))).containsExactly(blue, red).inOrder()
        assertThat(colorsOf(orient(source, 3))).containsExactly(blue, red).inOrder()
        // Sideways orientations come out one pixel wide: listed top to bottom.
        assertThat(colorsOf(orient(source, 5))).containsExactly(red, blue).inOrder()
        assertThat(colorsOf(orient(source, 6))).containsExactly(red, blue).inOrder()
        assertThat(colorsOf(orient(source, 7))).containsExactly(blue, red).inOrder()
        assertThat(colorsOf(orient(source, 8))).containsExactly(blue, red).inOrder()

        val tall = BufferedImage(1, 2, BufferedImage.TYPE_INT_RGB).apply {
            setRGB(0, 0, Color.RED.rgb)
            setRGB(0, 1, Color.BLUE.rgb)
        }
        assertThat(colorsOf(orient(tall, 4))).containsExactly(blue, red).inOrder()
    }

    @Test
    fun `formats are recognized by signature`() {
        assertThat(detectFormat(TestImages.jpeg(2, 2))).isEqualTo(ImageFormat.JPEG)
        assertThat(detectFormat(TestImages.png(2, 2))).isEqualTo(ImageFormat.PNG)
        assertThat(detectFormat(TestImages.webp)).isEqualTo(ImageFormat.WEBP)
        assertThat(detectFormat(byteArrayOf())).isNull()
    }
}
