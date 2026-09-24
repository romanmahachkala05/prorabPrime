package ru.prorabprime.domain.model

import kotlin.jvm.JvmInline

/** A picture on the device, as the platform names it (a content URI on Android). Opaque here. */
@JvmInline
value class LocalImageRef(
    val value: String,
)

/** An image ready to upload. Not a data class: array equality would be by identity. */
class CompressedImage(
    val bytes: ByteArray,
    val mimeType: String,
)
