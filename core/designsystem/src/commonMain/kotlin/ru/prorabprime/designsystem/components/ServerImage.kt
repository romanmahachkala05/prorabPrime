package ru.prorabprime.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import ru.prorabprime.domain.model.ServerFilePath

/**
 * A picture from our server, loaded through the app's image loader (which knows the server's
 * address and token). Shows [placeholderIcon] when there is no picture or it fails to load.
 */
@Composable
fun ServerImage(
    path: ServerFilePath?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: ImageVector = Icons.Default.Home,
) {
    if (path == null) {
        ImagePlaceholder(placeholderIcon, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = path,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { ImagePlaceholder(placeholderIcon, Modifier.fillMaxSize()) },
        error = { ImagePlaceholder(placeholderIcon, Modifier.fillMaxSize()) },
    )
}

@Composable
private fun ImagePlaceholder(icon: ImageVector, modifier: Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}
