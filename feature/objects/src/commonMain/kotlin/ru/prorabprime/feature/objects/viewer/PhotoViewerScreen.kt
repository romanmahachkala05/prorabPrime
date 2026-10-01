package ru.prorabprime.feature.objects.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.components.quarterTurns
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.feature.objects.photos.PHOTO_NOTE_LIMIT
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.photoviewer_back
import ru.prorabprime.feature.objects.resources.photoviewer_note_add
import ru.prorabprime.feature.objects.resources.photoviewer_note_cancel
import ru.prorabprime.feature.objects.resources.photoviewer_note_edit
import ru.prorabprime.feature.objects.resources.photoviewer_note_save
import ru.prorabprime.feature.objects.resources.photoviewer_note_title
import ru.prorabprime.feature.objects.resources.photoviewer_position
import ru.prorabprime.feature.objects.resources.photoviewer_rotate

@Composable
fun PhotoViewerScreen(
    objectId: String,
    photoId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: PhotoViewerViewModel = koinViewModel(key = "viewer-$objectId") {
        parametersOf(PhotoViewerArgs(objectId, photoId))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PhotoViewerContent(
        state,
        onBack,
        onRotate = viewModel::rotate,
        onSaveNote = viewModel::saveNote,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoViewerContent(
    state: PhotoViewerState,
    onBack: () -> Unit,
    onRotate: (String) -> Unit,
    onSaveNote: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val status = state.status) {
            PhotoViewerStatus.Content -> Pages(state, onBack, onRotate, onSaveNote)
            PhotoViewerStatus.Loading -> LoadingBox()
            is PhotoViewerStatus.Error -> ErrorMessage(status.message, onRetry = onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pages(
    state: PhotoViewerState,
    onBack: () -> Unit,
    onRotate: (String) -> Unit,
    onSaveNote: (String, String) -> Unit,
) {
    val pager = rememberPagerState(initialPage = state.initialPage) { state.photos.size }
    // A zoomed photo takes the drag for panning; the pager only swipes at normal size.
    var zoomed by remember { mutableStateOf(false) }
    val current = state.photos.getOrNull(pager.currentPage)
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
            ZoomableImage(
                photo = state.photos[page],
                onZoomChanged = { if (page == pager.currentPage) zoomed = it },
            )
        }
        TopAppBar(
            title = {
                Text(
                    stringResource(Res.string.photoviewer_position, pager.currentPage + 1, state.photos.size),
                    color = Color.White,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(Res.string.photoviewer_back),
                        tint = Color.White,
                    )
                }
            },
            actions = {
                IconButton(onClick = { current?.let { onRotate(it.id) } }) {
                    Icon(Icons.Default.Refresh, stringResource(Res.string.photoviewer_rotate), tint = Color.White)
                }
            },
            containerColor = BAR_SCRIM,
            contentColor = Color.White,
        )
        // Keyed by the photo, so swiping to another one shows its own note.
        current?.let { photo ->
            key(photo.id) {
                NoteBar(
                    note = photo.note,
                    receiptLine = photo.receiptLine,
                    onSave = { onSaveNote(photo.id, it) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/** The photo's note over its bottom edge; a tap writes or changes it. */
@Composable
private fun NoteBar(
    note: String?,
    receiptLine: String?,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Whether the editor is open means nothing beyond this bar.
    var editing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BAR_SCRIM)
            .clickable { editing = true }
            .navigationBarsPadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            receiptLine?.let {
                Text(it, color = Color.White, style = MaterialTheme.typography.titleSmall)
            }
            Text(
                text = note ?: stringResource(Res.string.photoviewer_note_add),
                color = if (note == null) Color.White.copy(alpha = HINT_ALPHA) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = NOTE_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Default.Edit,
            stringResource(Res.string.photoviewer_note_edit),
            tint = Color.White,
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
    if (editing) {
        NoteDialog(note.orEmpty(), onDismiss = { editing = false }, onSave = {
            editing = false
            onSave(it)
        })
    }
}

@Composable
private fun NoteDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.photoviewer_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(PHOTO_NOTE_LIMIT) },
                maxLines = DIALOG_NOTE_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text(stringResource(Res.string.photoviewer_note_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.photoviewer_note_cancel)) }
        },
    )
}

/** Pinch to zoom, drag to pan while zoomed, double tap to go back to fit. */
@Composable
private fun ZoomableImage(photo: ViewerPhoto, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                    onZoomChanged(false)
                })
            }.pointerInput(Unit) {
                // Not detectTransformGestures: it takes one-finger drags too, and the pager would
                // never see a swipe. Here a drag is taken only for a pinch or to pan a zoomed photo.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pinching = event.changes.count { it.pressed } > 1
                        if (pinching || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            onZoomChanged(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        ServerImage(
            path = photo.path,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .quarterTurns(photo.quarterTurns)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

private const val MAX_ZOOM = 5f
private const val HINT_ALPHA = 0.7f
private const val NOTE_PREVIEW_LINES = 3
private const val DIALOG_NOTE_LINES = 6
private val BAR_SCRIM = Color(0x66000000)
