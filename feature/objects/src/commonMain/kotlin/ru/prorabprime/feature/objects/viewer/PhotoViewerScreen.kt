package ru.prorabprime.feature.objects.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.photoviewer_back
import ru.prorabprime.feature.objects.resources.photoviewer_position

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
    PhotoViewerContent(state, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoViewerContent(
    state: PhotoViewerState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (val status = state.status) {
            PhotoViewerStatus.Content -> Pages(state, onBack)
            PhotoViewerStatus.Loading -> LoadingBox()
            is PhotoViewerStatus.Error -> ErrorMessage(status.message, onRetry = onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pages(state: PhotoViewerState, onBack: () -> Unit) {
    val pager = rememberPagerState(initialPage = state.initialPage) { state.photos.size }
    // A zoomed photo takes the drag for panning; the pager only swipes at normal size.
    var zoomed by remember { mutableStateOf(false) }
    HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
        ZoomableImage(
            path = state.photos[page],
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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = BAR_SCRIM),
    )
}

/** Pinch to zoom, drag to pan while zoomed, double tap to go back to fit. */
@Composable
private fun ZoomableImage(path: ServerFilePath, onZoomChanged: (Boolean) -> Unit) {
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
            path = path,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding()
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
private val BAR_SCRIM = Color(0x66000000)
