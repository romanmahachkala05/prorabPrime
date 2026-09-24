package ru.prorabprime.domain

import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.LocalImageRef

/**
 * Prepares a picture from the device for upload: applies its EXIF orientation, scales the long
 * side down to 2048 px and encodes it as JPEG at quality 85. Platform-specific by nature.
 */
interface ImageCompressor {
    suspend fun compress(image: LocalImageRef): Result<CompressedImage>
}
