package ru.prorabprime.data.image

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.asFailure

/**
 * Resolves with the picture as base64 JPEG: the browser decodes it upright (`imageOrientation`),
 * a canvas scales it, and re-encoding drops the EXIF block and the GPS position with it.
 */
@JsFun(
    """async (url, maxSide, quality) => {
        const blob = await (await fetch(url)).blob();
        const bitmap = await createImageBitmap(blob, { imageOrientation: 'from-image' });
        const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(bitmap.width * scale));
        canvas.height = Math.max(1, Math.round(bitmap.height * scale));
        canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height);
        const jpeg = await new Promise((resolve) => canvas.toBlob(resolve, 'image/jpeg', quality));
        const bytes = new Uint8Array(await jpeg.arrayBuffer());
        let text = '';
        for (let i = 0; i < bytes.length; i += 0x8000) {
            text += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
        }
        return btoa(text);
    }""",
)
private external fun compressToBase64(
    url: String,
    maxSide: Int,
    quality: Double,
): Promise<JsString>

/** The web's [ImageCompressor]: the same long side and quality as the phone's. */
class WebImageCompressor : ImageCompressor {

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun compress(image: LocalImageRef): Result<CompressedImage> = try {
        val base64 = compressToBase64(image.value, MAX_UPLOAD_SIDE, UPLOAD_JPEG_QUALITY / PERCENT).await().toString()
        Result.success(CompressedImage(Base64.decode(base64), "image/jpeg"))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
        // A vanished file, an undecodable picture: the browser's own errors are not worth telling apart.
        println("W/ProrabData: Could not prepare ${image.value} for upload: $failure")
        AppError.Unknown.asFailure()
    }

    private companion object {
        const val PERCENT = 100.0
    }
}
