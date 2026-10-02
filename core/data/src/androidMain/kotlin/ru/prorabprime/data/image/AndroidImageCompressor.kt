package ru.prorabprime.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.asFailure

/**
 * Reads a content URI, decodes it at a reduced sample size, scales the long side to at most
 * [MAX_UPLOAD_SIDE], turns it upright by its EXIF orientation and re-encodes it as JPEG.
 * The EXIF block does not survive re-encoding, which also drops the photo's GPS position.
 */
class AndroidImageCompressor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
) : ImageCompressor {

    override suspend fun compress(image: LocalImageRef): Result<CompressedImage> = withContext(dispatcher) {
        try {
            val uri = Uri.parse(image.value)
            val bitmap = decode(uri) ?: return@withContext AppError.Unknown.asFailure()
            val upright = rotate(scale(bitmap), orientationOf(uri))
            val output = ByteArrayOutputStream()
            upright.compress(Bitmap.CompressFormat.JPEG, UPLOAD_JPEG_QUALITY, output)
            Result.success(CompressedImage(output.toByteArray(), "image/jpeg"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
            // A vanished file, a revoked permission, an undecodable picture.
            Log.w(TAG, "Could not prepare ${image.value} for upload", failure)
            AppError.Unknown.asFailure()
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        return open(uri).use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun scale(bitmap: Bitmap): Bitmap {
        val (width, height) = fitWithin(bitmap.width, bitmap.height)
        return if (width == bitmap.width && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
    }

    private fun orientationOf(uri: Uri): Int = open(uri).use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }

    private fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(HALF_TURN)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)

            ExifInterface.ORIENTATION_TRANSPOSE -> matrix.apply {
                setRotate(QUARTER_TURN)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(QUARTER_TURN)

            ExifInterface.ORIENTATION_TRANSVERSE -> matrix.apply {
                setRotate(-QUARTER_TURN)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-QUARTER_TURN)

            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun open(uri: Uri): InputStream = context.contentResolver.openInputStream(uri) ?: error("Cannot open $uri")

    private companion object {
        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
        const val TAG = "ImageCompressor"
    }
}
