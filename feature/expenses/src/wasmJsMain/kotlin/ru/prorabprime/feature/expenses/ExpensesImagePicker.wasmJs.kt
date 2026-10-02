package ru.prorabprime.feature.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.prorabprime.domain.model.LocalImageRef

/** Opens the browser's file chooser for one picture; the file comes back as an object URL, valid while the page is open. */
@JsFun(
    """(onPicked) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = 'image/*';
        input.onchange = () => {
            const file = (input.files || [])[0];
            if (file) onPicked(URL.createObjectURL(file));
        };
        input.click();
    }""",
)
private external fun chooseImage(onPicked: (JsString) -> Unit)

@Composable
internal actual fun rememberImagePicker(onPicked: (LocalImageRef) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onPicked)
    return remember { { chooseImage { url -> deliver(LocalImageRef(url.toString())) } } }
}
