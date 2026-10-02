package ru.prorabprime.feature.objects.photos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.prorabprime.domain.model.LocalImageRef

/**
 * Opens the browser's file chooser for pictures. With [capture] a phone's browser goes straight to
 * the camera; a computer's shows the same chooser. The files come back as object URLs, one per
 * line, which stay valid for as long as the page is open.
 */
@JsFun(
    """(capture, multiple, onPicked) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.multiple = multiple;
        if (capture) input.setAttribute('capture', 'environment');
        input.onchange = () => {
            const urls = Array.from(input.files || []).map((file) => URL.createObjectURL(file));
            if (urls.length > 0) onPicked(urls.join('\n'));
        };
        input.click();
    }""",
)
private external fun chooseImages(
    capture: Boolean,
    multiple: Boolean,
    onPicked: (JsString) -> Unit,
)

@Composable
internal actual fun rememberPhotoSources(onPicked: (List<LocalImageRef>) -> Unit): PhotoSources {
    val deliver by rememberUpdatedState(onPicked)
    return remember {
        object : PhotoSources {
            override fun takePhoto() = choose(capture = true, multiple = false)

            override fun pickFromGallery() = choose(capture = false, multiple = true)

            private fun choose(capture: Boolean, multiple: Boolean) {
                chooseImages(capture, multiple) { urls ->
                    deliver(urls.toString().lines().filter { it.isNotBlank() }.map(::LocalImageRef))
                }
            }
        }
    }
}
