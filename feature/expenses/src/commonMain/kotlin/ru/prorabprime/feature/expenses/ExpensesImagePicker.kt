package ru.prorabprime.feature.expenses

import androidx.compose.runtime.Composable
import ru.prorabprime.domain.model.LocalImageRef

/**
 * Returns a function that opens the system's chooser for one picture; [onPicked] gets it, and a
 * cancelled choice calls nothing. Platform-specific by nature, like the camera of the objects screens.
 */
@Composable
internal expect fun rememberImagePicker(onPicked: (LocalImageRef) -> Unit): () -> Unit
