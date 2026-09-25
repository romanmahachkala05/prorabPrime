package ru.prorabprime.feature.objects.photos

import androidx.compose.runtime.Composable
import ru.prorabprime.domain.model.LocalImageRef

/** Where new photos come from. Platform-specific by nature: the camera and the gallery. */
internal interface PhotoSources {
    fun takePhoto()

    fun pickFromGallery()
}

/** [onPicked] gets the pictures; a cancelled camera or an empty choice calls nothing. */
@Composable
internal expect fun rememberPhotoSources(onPicked: (List<LocalImageRef>) -> Unit): PhotoSources
