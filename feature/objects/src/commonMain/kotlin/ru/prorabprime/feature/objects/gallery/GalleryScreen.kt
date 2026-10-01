package ru.prorabprime.feature.objects.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.PendingBadge
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.components.quarterTurns
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.gallery_back
import ru.prorabprime.feature.objects.resources.gallery_cancel_select
import ru.prorabprime.feature.objects.resources.gallery_delete
import ru.prorabprime.feature.objects.resources.gallery_empty
import ru.prorabprime.feature.objects.resources.gallery_select
import ru.prorabprime.feature.objects.resources.gallery_select_all
import ru.prorabprime.feature.objects.resources.gallery_selected
import ru.prorabprime.feature.objects.resources.gallery_title_photos
import ru.prorabprime.feature.objects.resources.gallery_title_receipts
import ru.prorabprime.feature.objects.resources.objectdetails_cover
import ru.prorabprime.ui.UiText

/** All the photos, or all the receipts, of an object in a grid, with a way to pick some and delete them. */
@Composable
fun GalleryScreen(
    objectId: String,
    receipts: Boolean,
    onOpenPhoto: (photoId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GalleryViewModel = koinViewModel(key = "gallery-$objectId-$receipts") {
        parametersOf(GalleryArgs(objectId, receipts))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    GalleryContent(state, viewModel::onEvent, onOpenPhoto, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryContent(
    state: GalleryState,
    onEvent: (GalleryEvent) -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(state, onEvent, onBack, modifier) {
        when (val status = state.status) {
            GalleryStatus.Content ->
                if (state.photos.isEmpty()) {
                    EmptyMessage(UiText.Resource(Res.string.gallery_empty))
                } else {
                    Grid(state, onEvent, onOpenPhoto)
                }

            GalleryStatus.Loading -> LoadingBox()

            is GalleryStatus.Error -> ErrorMessage(status.message, onRetry = onBack)
        }
    }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(GalleryEvent.DialogConfirmed) },
        onDismiss = { onEvent(GalleryEvent.DialogDismissed) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Scaffold(
    state: GalleryState,
    onEvent: (GalleryEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    androidx.compose.material3.Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (state.selecting) {
                        Text(stringResource(Res.string.gallery_selected, state.selected.size))
                    } else {
                        Text(stringResource(titleOf(state)))
                    }
                },
                navigationIcon = {
                    if (state.selecting) {
                        IconButton(onClick = { onEvent(GalleryEvent.SelectingToggled) }) {
                            Icon(Icons.Default.Close, stringResource(Res.string.gallery_cancel_select))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.gallery_back))
                        }
                    }
                },
                actions = { Actions(state, onEvent) },
            )
        },
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) { content() } }
}

@Composable
private fun Actions(state: GalleryState, onEvent: (GalleryEvent) -> Unit) {
    if (state.selecting) {
        TextButton(onClick = { onEvent(GalleryEvent.SelectAllToggled) }) {
            Text(stringResource(Res.string.gallery_select_all))
        }
        IconButton(onClick = { onEvent(GalleryEvent.DeleteClicked) }, enabled = state.selected.isNotEmpty()) {
            Icon(Icons.Default.Delete, stringResource(Res.string.gallery_delete))
        }
    } else if (state.photos.isNotEmpty()) {
        TextButton(onClick = { onEvent(GalleryEvent.SelectingToggled) }) {
            Text(stringResource(Res.string.gallery_select))
        }
    }
}

/** Three tiles to a row, as many rows as there are photos; the grid only draws what is on screen. */
@Composable
private fun Grid(
    state: GalleryState,
    onEvent: (GalleryEvent) -> Unit,
    onOpenPhoto: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMNS),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(GAP),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        items(state.photos, key = { it.id }) { photo ->
            Tile(
                photo = photo,
                selecting = state.selecting,
                selected = photo.id in state.selected,
                onClick = {
                    if (state.selecting) onEvent(GalleryEvent.PhotoToggled(photo.id)) else onOpenPhoto(photo.id)
                },
                onLongClick = { onEvent(GalleryEvent.PhotoToggled(photo.id)) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tile(
    photo: GalleryPhotoUi,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        ServerImage(
            path = photo.thumb,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().quarterTurns(photo.quarterTurns),
        )
        if (selected) Box(Modifier.fillMaxSize().background(colors.primary.copy(alpha = SELECTED_VEIL)))
        if (photo.isCover && !selecting) {
            Surface(
                color = colors.primary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs),
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = stringResource(Res.string.objectdetails_cover),
                    tint = colors.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).size(14.dp),
                )
            }
        }
        if (photo.isPending && !selecting) PendingBadge(Modifier.align(Alignment.TopEnd).padding(Spacing.xs))
        photo.amount?.let {
            Surface(
                color = colors.primary,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.xs),
            ) {
                Text(
                    it,
                    color = colors.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (selecting) {
            Icon(
                ProrabIcons.CheckCircle,
                contentDescription = null,
                tint = if (selected) colors.onPrimary else colors.surface,
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xs).size(CHECK_SIZE),
            )
        }
    }
}

private const val COLUMNS = 3
private val GAP = 2.dp
private val CHECK_SIZE = 24.dp
private const val SELECTED_VEIL = 0.45f

private fun titleOf(state: GalleryState) =
    if (state.receipts) Res.string.gallery_title_receipts else Res.string.gallery_title_photos
