package ru.prorabprime.feature.objects.photos

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import ru.prorabprime.domain.model.LocalImageRef

@Composable
internal actual fun rememberPhotoSources(onPicked: (List<LocalImageRef>) -> Unit): PhotoSources {
    val context = LocalContext.current
    val deliver by rememberUpdatedState(onPicked)
    // Saveable: while the camera app is in front the system may kill this process; the file it
    // is writing to must still be known when the result comes back.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val capture = pendingCapture
        pendingCapture = null
        if (saved && capture != null) deliver(listOf(LocalImageRef(capture)))
    }
    val gallery =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED)) { uris ->
            if (uris.isNotEmpty()) deliver(uris.map { LocalImageRef(it.toString()) })
        }

    return remember(camera, gallery) {
        object : PhotoSources {
            override fun takePhoto() {
                val uri = newCaptureUri(context)
                pendingCapture = uri.toString()
                camera.launch(uri)
            }

            override fun pickFromGallery() {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

/** A new file in the app's cache for the camera to write to, shared through the FileProvider. */
private fun newCaptureUri(context: Context): Uri {
    val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
    val file = File.createTempFile("capture-", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${context.packageName}.captures", file)
}

private const val CAPTURE_DIRECTORY = "captures"
private const val MAX_PICKED = 20
