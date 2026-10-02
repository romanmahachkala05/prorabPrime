package ru.prorabprime.testing

import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.asFailure

/** Returns the reference's own text as the "compressed" bytes, so a test can tell images apart. */
class FakeImageCompressor : ImageCompressor {

    var error: AppError? = null

    val compressed = mutableListOf<LocalImageRef>()

    override suspend fun compress(image: LocalImageRef): Result<CompressedImage> {
        error?.let { return it.asFailure() }
        compressed += image
        return Result.success(CompressedImage(bytes = image.value.encodeToByteArray(), mimeType = "image/jpeg"))
    }
}
