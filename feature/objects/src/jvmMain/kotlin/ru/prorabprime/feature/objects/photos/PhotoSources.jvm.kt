package ru.prorabprime.feature.objects.photos

import androidx.compose.runtime.Composable
import ru.prorabprime.domain.model.LocalImageRef

/** The JVM target has no camera or gallery; it exists for tests. */
@Composable
internal actual fun rememberPhotoSources(onPicked: (List<LocalImageRef>) -> Unit): PhotoSources = NoPhotoSources

private object NoPhotoSources : PhotoSources {
    override fun takePhoto() = Unit

    override fun pickFromGallery() = Unit
}
