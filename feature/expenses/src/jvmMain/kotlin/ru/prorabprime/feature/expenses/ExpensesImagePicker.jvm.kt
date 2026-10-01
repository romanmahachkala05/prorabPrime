package ru.prorabprime.feature.expenses

import androidx.compose.runtime.Composable
import ru.prorabprime.domain.model.LocalImageRef

/** The JVM target has no file chooser; it exists for tests. */
@Composable
internal actual fun rememberImagePicker(onPicked: (LocalImageRef) -> Unit): () -> Unit = {}
