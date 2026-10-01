package ru.prorabprime.feature.objects.gallery

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Every photo of an object, or every receipt of it when [receipts], in one grid. */
@Serializable
data class GalleryNavKey(
    val objectId: String,
    val receipts: Boolean,
) : NavKey
