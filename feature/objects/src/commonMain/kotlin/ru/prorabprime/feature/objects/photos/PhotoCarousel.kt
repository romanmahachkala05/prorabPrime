package ru.prorabprime.feature.objects.photos

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.objects.details.PhotoUi
import ru.prorabprime.feature.objects.details.UploadUi
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_add_photo
import ru.prorabprime.feature.objects.resources.objectdetails_cover
import ru.prorabprime.feature.objects.resources.objectdetails_delete_photo
import ru.prorabprime.feature.objects.resources.objectdetails_make_cover
import ru.prorabprime.feature.objects.resources.objectdetails_upload_dismiss
import ru.prorabprime.feature.objects.resources.objectdetails_upload_retry
import ru.prorabprime.ui.resolve

/** The camera tile first, then uploads in progress, then the object's photos. */
@Composable
internal fun PhotoCarousel(
    photos: ImmutableList<PhotoUi>,
    uploads: ImmutableList<UploadUi>,
    onAddClick: () -> Unit,
    onPhotoClick: (PhotoUi) -> Unit,
    onMakeCover: (PhotoUi) -> Unit,
    onDelete: (PhotoUi) -> Unit,
    onRetryUpload: (UploadUi) -> Unit,
    onDismissUpload: (UploadUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item(key = "add") { AddPhotoTile(onAddClick) }
        items(uploads, key = { "upload:" + it.image.value }) { upload ->
            UploadTile(upload, onRetry = { onRetryUpload(upload) }, onDismiss = { onDismissUpload(upload) })
        }
        items(photos, key = { it.id }) { photo ->
            PhotoTile(
                photo = photo,
                onClick = { onPhotoClick(photo) },
                onMakeCover = { onMakeCover(photo) },
                onDelete = { onDelete(photo) },
            )
        }
    }
}

@Composable
private fun AddPhotoTile(onClick: () -> Unit) {
    val label = stringResource(Res.string.objectdetails_add_photo)
    Surface(
        onClick = onClick,
        shape = TILE_SHAPE,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(TILE_SIZE),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(36.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    photo: PhotoUi,
    onClick: () -> Unit,
    onMakeCover: () -> Unit,
    onDelete: () -> Unit,
) {
    // Whether the long-press menu is open is view state with no meaning beyond this tile.
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ServerImage(
            path = photo.thumb,
            contentDescription = null,
            modifier = Modifier
                .size(TILE_SIZE)
                .clip(TILE_SHAPE)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true }),
        )
        if (photo.isCover) CoverBadge(Modifier.align(Alignment.BottomStart).padding(Spacing.xs))
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (!photo.isCover) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.objectdetails_make_cover)) },
                    onClick = {
                        menuOpen = false
                        onMakeCover()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.objectdetails_delete_photo)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun CoverBadge(modifier: Modifier) {
    Surface(color = MaterialTheme.colorScheme.primary, shape = PILL, modifier = modifier) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(Res.string.objectdetails_cover),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun UploadTile(
    upload: UploadUi,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.size(TILE_SIZE).clip(TILE_SHAPE)) {
        AsyncImage(
            model = upload.image.value,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Box(Modifier.fillMaxSize().background(SCRIM), contentAlignment = Alignment.Center) {
            val failure = upload.failure
            if (failure == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        failure.resolve(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                    Row2(onRetry, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Row2(onRetry: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.foundation.layout.Row {
        IconButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, stringResource(Res.string.objectdetails_upload_retry), tint = Color.White)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, stringResource(Res.string.objectdetails_upload_dismiss), tint = Color.White)
        }
    }
}

private val TILE_SIZE = 120.dp
private val TILE_SHAPE = RoundedCornerShape(12.dp)
private val SCRIM = Color(0x99000000)
private val PILL = RoundedCornerShape(percent = 50)
