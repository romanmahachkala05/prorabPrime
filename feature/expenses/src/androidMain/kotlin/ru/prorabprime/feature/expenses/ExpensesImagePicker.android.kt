package ru.prorabprime.feature.expenses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import ru.prorabprime.domain.model.LocalImageRef

@Composable
internal actual fun rememberImagePicker(onPicked: (LocalImageRef) -> Unit): () -> Unit {
    val deliver by rememberUpdatedState(onPicked)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) deliver(LocalImageRef(uri.toString()))
    }
    return remember(gallery) {
        { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}
