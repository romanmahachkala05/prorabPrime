package ru.prorabprime.feature.objects.viewer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The object's photos full screen, starting at [photoId]. */
@Serializable
data class PhotoViewerNavKey(
    val objectId: String,
    val photoId: String,
) : NavKey
