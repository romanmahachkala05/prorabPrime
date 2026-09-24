package ru.prorabprime.server.storage

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifIFD0Directory
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure

/** What the server keeps about an accepted upload, plus the thumbnail it made from it. */
class ProcessedImage(
    val format: ImageFormat,
    /** After the EXIF orientation is applied. */
    val width: Int,
    val height: Int,
    /** JPEG, long side at most [THUMBNAIL_SIZE]. */
    val thumbnail: ByteArray,
)

enum class ImageFormat(
    val contentType: String,
    val extension: String,
) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
}

const val THUMBNAIL_SIZE = 400

interface ImageProcessor {
    /** Fails with [ServiceError.UnsupportedMedia] for anything that is not a readable JPEG, PNG or WebP. */
    suspend fun process(bytes: ByteArray): Result<ProcessedImage>
}

class JavaImageProcessor(
    private val dispatcher: CoroutineDispatcher,
) : ImageProcessor {

    override suspend fun process(bytes: ByteArray): Result<ProcessedImage> = withContext(dispatcher) {
        val format = detectFormat(bytes) ?: return@withContext unsupported("Not a JPEG, PNG or WebP image")
        val decoded = decode(bytes) ?: return@withContext unsupported("The image could not be read")
        val oriented = orient(decoded, readOrientation(bytes))
        Result.success(ProcessedImage(format, oriented.width, oriented.height, thumbnailOf(oriented)))
    }

    /** Reads the size first, so a small file that decodes to a huge bitmap is refused before decoding. */
    private fun decode(bytes: ByteArray): BufferedImage? = runCatching {
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                if (reader.getWidth(0).toLong() * reader.getHeight(0) > MAX_PIXELS) return null
                reader.read(0)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

    private fun thumbnailOf(image: BufferedImage): ByteArray {
        val scale = minOf(1.0, THUMBNAIL_SIZE.toDouble() / max(image.width, image.height))
        val width = max(1, (image.width * scale).roundToInt())
        val height = max(1, (image.height * scale).roundToInt())
        val thumbnail = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        thumbnail.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            // JPEG has no alpha; transparent PNG areas become white rather than black.
            color = Color.WHITE
            fillRect(0, 0, width, height)
            drawImage(image, 0, 0, width, height, null)
            dispose()
        }
        return encodeJpeg(thumbnail)
    }

    private fun encodeJpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val params = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = THUMBNAIL_QUALITY
                }
                writer.write(null, IIOImage(image, null, null), params)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    private fun <T> unsupported(message: String): Result<T> = ServiceError.UnsupportedMedia(message).asFailure()

    private companion object {
        const val THUMBNAIL_QUALITY = 0.85f

        /** 50 megapixels: well above any phone camera, far below what would exhaust the heap. */
        const val MAX_PIXELS = 50_000_000L
    }
}

/** By signature, not by the declared content type, which the client controls. */
internal fun detectFormat(bytes: ByteArray): ImageFormat? {
    fun startsWith(signature: IntArray, offset: Int = 0) = bytes.size >= offset + signature.size &&
        signature.indices.all { bytes[offset + it] == signature[it].toByte() }

    return when {
        startsWith(JPEG_SIGNATURE) -> ImageFormat.JPEG
        startsWith(PNG_SIGNATURE) -> ImageFormat.PNG
        startsWith(RIFF_SIGNATURE) && startsWith(WEBP_SIGNATURE, offset = WEBP_SIGNATURE_OFFSET) -> ImageFormat.WEBP
        else -> null
    }
}

private val JPEG_SIGNATURE = intArrayOf(0xFF, 0xD8, 0xFF)
private val PNG_SIGNATURE = intArrayOf(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

// A WebP file is "RIFF", four bytes of size, then "WEBP".
private val RIFF_SIGNATURE = intArrayOf(0x52, 0x49, 0x46, 0x46)
private val WEBP_SIGNATURE = intArrayOf(0x57, 0x45, 0x42, 0x50)
private const val WEBP_SIGNATURE_OFFSET = 8

/** The EXIF orientation (1–8), or 1 when there is none. */
internal fun readOrientation(bytes: ByteArray): Int = runCatching {
    ImageMetadataReader.readMetadata(ByteArrayInputStream(bytes))
        .getFirstDirectoryOfType(ExifIFD0Directory::class.java)
        ?.takeIf { it.containsTag(ExifIFD0Directory.TAG_ORIENTATION) }
        ?.getInt(ExifIFD0Directory.TAG_ORIENTATION)
}.getOrNull() ?: 1

/** Turns the stored pixels upright, as a viewer honoring the EXIF orientation would show them. */
internal fun orient(image: BufferedImage, orientation: Int): BufferedImage {
    val transformFor = ORIENTATION_TRANSFORMS[orientation] ?: return image
    val swapsSides = orientation >= FIRST_SIDEWAYS_ORIENTATION
    val result = BufferedImage(
        if (swapsSides) image.height else image.width,
        if (swapsSides) image.width else image.height,
        BufferedImage.TYPE_INT_ARGB,
    )
    result.createGraphics().apply {
        drawImage(image, transformFor(image.width.toDouble(), image.height.toDouble()), null)
        dispose()
    }
    return result
}

/** Orientations 5–8 turn the picture on its side. */
private const val FIRST_SIDEWAYS_ORIENTATION = 5

/** EXIF orientation → the transform from stored to upright, given the stored width and height. */
private val ORIENTATION_TRANSFORMS: Map<Int, (w: Double, h: Double) -> AffineTransform> = mapOf(
    // Mirrored horizontally.
    2 to { w, _ ->
        AffineTransform().apply {
            translate(w, 0.0)
            scale(-1.0, 1.0)
        }
    },
    // Upside down.
    3 to { w, h ->
        AffineTransform().apply {
            translate(w, h)
            rotate(Math.PI)
        }
    },
    // Mirrored vertically.
    4 to { _, h ->
        AffineTransform().apply {
            translate(0.0, h)
            scale(1.0, -1.0)
        }
    },
    // Transposed.
    5 to { _, _ ->
        AffineTransform().apply {
            rotate(Math.PI / 2)
            scale(1.0, -1.0)
        }
    },
    // Needs a quarter turn clockwise.
    6 to { _, h ->
        AffineTransform().apply {
            translate(h, 0.0)
            rotate(Math.PI / 2)
        }
    },
    // Transversed.
    7 to { w, h ->
        AffineTransform().apply {
            translate(h, w)
            scale(1.0, -1.0)
            rotate(Math.PI / 2)
        }
    },
    // Needs a quarter turn counterclockwise.
    8 to { w, _ ->
        AffineTransform().apply {
            translate(0.0, w)
            rotate(-Math.PI / 2)
        }
    },
)
