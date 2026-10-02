package ru.prorabprime.server.storage

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

/** Images built in memory, so the tests carry no binary fixtures. */
object TestImages {

    fun jpeg(
        width: Int,
        height: Int,
        orientation: Int? = null,
    ): ByteArray {
        val bytes = encode(solid(width, height, BufferedImage.TYPE_INT_RGB), "jpeg")
        return if (orientation == null) bytes else withExifOrientation(bytes, orientation)
    }

    fun png(width: Int, height: Int): ByteArray = encode(solid(width, height, BufferedImage.TYPE_INT_ARGB), "png")

    /** A 1×1 lossless WebP; nothing in the JDK can write one. */
    val webp: ByteArray = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==")

    private fun solid(
        width: Int,
        height: Int,
        type: Int,
    ) = BufferedImage(width, height, type).apply {
        createGraphics().apply {
            color = Color.ORANGE
            fillRect(0, 0, width, height)
            dispose()
        }
    }

    private fun encode(image: BufferedImage, format: String): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()

    /**
     * Inserts an APP1 segment right after the JPEG's SOI marker, holding a minimal big-endian EXIF
     * block whose only entry is the orientation tag.
     */
    private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
        val tiff = byteArrayOf(
            0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08, // "MM", 42, IFD0 at offset 8
            0x00, 0x01, // one entry
            0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, // tag 0x0112 (orientation), SHORT, count 1
            0x00, orientation.toByte(), 0x00, 0x00, // the value, left-aligned
            0x00, 0x00, 0x00, 0x00, // no next IFD
        )
        val payload = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
        val length = payload.size + 2
        val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (length shr 8).toByte(), length.toByte()) + payload
        return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
    }
}
