package ru.prorabprime.feature.objects.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.CompactTextField
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.PendingMark
import ru.prorabprime.designsystem.components.SearchField
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.icons.ProrabIcons
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.components.label
import ru.prorabprime.feature.objects.photos.PHOTO_NOTE_LIMIT
import ru.prorabprime.feature.objects.photos.rememberPhotoSources
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectslist_add
import ru.prorabprime.feature.objects.resources.objectslist_camera_photo
import ru.prorabprime.feature.objects.resources.objectslist_camera_receipt
import ru.prorabprime.feature.objects.resources.objectslist_capture_note
import ru.prorabprime.feature.objects.resources.objectslist_capture_title_photo
import ru.prorabprime.feature.objects.resources.objectslist_capture_title_receipt
import ru.prorabprime.feature.objects.resources.objectslist_clear_search
import ru.prorabprime.feature.objects.resources.objectslist_empty
import ru.prorabprime.feature.objects.resources.objectslist_empty_action
import ru.prorabprime.feature.objects.resources.objectslist_expenses
import ru.prorabprime.feature.objects.resources.objectslist_filter_all
import ru.prorabprime.feature.objects.resources.objectslist_map
import ru.prorabprime.feature.objects.resources.objectslist_nothing_found
import ru.prorabprime.feature.objects.resources.objectslist_search
import ru.prorabprime.feature.objects.resources.objectslist_search_close
import ru.prorabprime.feature.objects.resources.objectslist_search_open
import ru.prorabprime.feature.objects.resources.objectslist_settings
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_asc
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_desc
import ru.prorabprime.feature.objects.resources.objectslist_sort_created
import ru.prorabprime.feature.objects.resources.objectslist_sort_updated
import ru.prorabprime.feature.objects.resources.objectslist_tasks
import ru.prorabprime.feature.objects.resources.objectslist_title
import ru.prorabprime.ui.UiText

@Composable
fun ObjectsListScreen(
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null where the report is not offered. */
    onOpenExpenses: (() -> Unit)? = null,
) {
    ObjectsListScreen(
        onOpenObject,
        onCreateObject,
        onOpenSettings,
        onOpenMap,
        onOpenTasks,
        onOpenExpenses,
        modifier,
        koinViewModel(),
    )
}

@Composable
private fun ObjectsListScreen(
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenExpenses: (() -> Unit)?,
    modifier: Modifier,
    viewModel: ObjectsListViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val photoCamera = rememberPhotoSources(
        onPicked = { viewModel.onEvent(ObjectsListEvent.PhotosCaptured(it, AttachmentKind.PHOTO)) },
    )
    val receiptCamera = rememberPhotoSources(
        onPicked = { viewModel.onEvent(ObjectsListEvent.PhotosCaptured(it, AttachmentKind.RECEIPT)) },
    )
    ObjectsListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenObject = onOpenObject,
        onCreateObject = onCreateObject,
        onOpenSettings = onOpenSettings,
        onOpenMap = onOpenMap,
        onOpenTasks = onOpenTasks,
        onOpenExpenses = onOpenExpenses,
        onOpenPhotoCamera = photoCamera::takePhoto,
        onOpenReceiptCamera = receiptCamera::takePhoto,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObjectsListContent(
    state: ObjectsListState,
    onEvent: (ObjectsListEvent) -> Unit,
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPhotoCamera: () -> Unit = {},
    onOpenReceiptCamera: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenTasks: () -> Unit = {},
    onOpenExpenses: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            ListTopBar(state, onEvent, onCreateObject, onOpenSettings, onOpenMap, onOpenTasks, onOpenExpenses)
        },
        floatingActionButton = {
            // Side by side under the thumb: a photo of the object, then a receipt. The button sets the kind.
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CameraButton(onOpenPhotoCamera, ProrabIcons.Camera, Res.string.objectslist_camera_photo)
                CameraButton(onOpenReceiptCamera, ProrabIcons.Receipt, Res.string.objectslist_camera_receipt)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.searchOpen) {
                SearchField(
                    state.search,
                    onEvent,
                    Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                )
            }
            FilterRow(state, onEvent)
            ListBody(state, onEvent, onOpenObject, onCreateObject)
        }
    }
    state.capture?.let { CaptureSheet(it, state.items, onEvent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListTopBar(
    state: ObjectsListState,
    onEvent: (ObjectsListEvent) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenExpenses: (() -> Unit)?,
) {
    TopAppBar(
        title = { Text(stringResource(Res.string.objectslist_title)) },
        actions = {
            IconButton(onClick = { onEvent(ObjectsListEvent.SearchToggled) }) {
                if (state.searchOpen) {
                    Icon(Icons.Default.Clear, stringResource(Res.string.objectslist_search_close))
                } else {
                    Icon(Icons.Default.Search, stringResource(Res.string.objectslist_search_open))
                }
            }
            IconButton(onClick = onOpenTasks) {
                Icon(ProrabIcons.Tasks, contentDescription = stringResource(Res.string.objectslist_tasks))
            }
            onOpenExpenses?.let { open ->
                IconButton(onClick = open) {
                    Icon(
                        ProrabIcons.Wallet,
                        contentDescription = stringResource(Res.string.objectslist_expenses),
                    )
                }
            }
            IconButton(onClick = onOpenMap) {
                Icon(ProrabIcons.Map, contentDescription = stringResource(Res.string.objectslist_map))
            }
            IconButton(onClick = onCreateObject) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.objectslist_add))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(Res.string.objectslist_settings),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListBody(
    state: ObjectsListState,
    onEvent: (ObjectsListEvent) -> Unit,
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { onEvent(ObjectsListEvent.Refresh) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val status = state.status) {
            ObjectsListStatus.Content -> ObjectCards(state, onOpenObject)

            ObjectsListStatus.Empty -> EmptyMessage(
                message = UiText.Resource(Res.string.objectslist_empty),
                actionLabel = UiText.Resource(Res.string.objectslist_empty_action),
                onAction = onCreateObject,
            )

            ObjectsListStatus.NothingFound -> EmptyMessage(
                UiText.Resource(Res.string.objectslist_nothing_found),
            )

            ObjectsListStatus.Loading -> LoadingBox()

            is ObjectsListStatus.Error -> ErrorMessage(status.message, onRetry = {
                onEvent(ObjectsListEvent.Retry)
            })
        }
    }
}

@Composable
private fun ObjectCards(state: ObjectsListState, onOpenObject: (id: String) -> Unit) {
    // Two columns of small tiles: ten objects fit on one screen, so picking one needs no scrolling.
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the FAB never covers the last tiles.
        contentPadding = PaddingValues(start = Spacing.s, end = Spacing.s, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(state.items, key = { it.id }) { item ->
            ObjectTile(item, onClick = { onOpenObject(item.id) })
        }
    }
}

@Composable
private fun ObjectTile(item: ObjectCardUi, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ServerImage(
                path = item.cover,
                contentDescription = null,
                modifier = Modifier.size(TILE_THUMB_SIZE).clip(RoundedCornerShape(TILE_THUMB_RADIUS)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        stringResource(item.status.label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (item.isPending) PendingMark()
                }
            }
        }
    }
}

@Composable
private fun CameraButton(
    onClick: () -> Unit,
    icon: ImageVector,
    description: StringResource,
) {
    LargeFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Icon(icon, contentDescription = stringResource(description), modifier = Modifier.size(40.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSheet(
    capture: CaptureUi,
    items: ImmutableList<ObjectCardUi>,
    onEvent: (ObjectsListEvent) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { onEvent(ObjectsListEvent.CaptureDismissed) }) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.m).padding(bottom = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            val title = if (capture.kind == AttachmentKind.RECEIPT) {
                Res.string.objectslist_capture_title_receipt
            } else {
                Res.string.objectslist_capture_title_photo
            }
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
            // One line: the sheet is for choosing an object, the note is a side thing.
            CompactTextField(
                value = capture.note,
                onValueChange = { onEvent(ObjectsListEvent.CaptureNoteChanged(it.take(PHOTO_NOTE_LIMIT))) },
                placeholder = stringResource(Res.string.objectslist_capture_note),
            )
            // The same two-column tiles as the main screen, so choosing an object needs little scrolling.
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.heightIn(max = CAPTURE_LIST_MAX_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(items, key = { it.id }) { item ->
                    ObjectTile(item, onClick = { onEvent(ObjectsListEvent.CaptureTargetChosen(item.id)) })
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 2
private val TILE_THUMB_SIZE = 56.dp
private const val TILE_THUMB_RADIUS = 8
private val CAPTURE_LIST_MAX_HEIGHT = 360.dp

@Preview
@Composable
private fun ObjectsListContentPreview() {
    ProrabTheme {
        ObjectsListContent(
            state = ObjectsListState(
                status = ObjectsListStatus.Content,
                items = persistentListOf(
                    ObjectCardUi("1", "Кухня у Ивановых", "ул. Ленина, 1, кв. 5", ObjectStatus.IN_PROGRESS, 2, null),
                    ObjectCardUi("2", "Тверская, 5", null, ObjectStatus.DONE, 0, null),
                ),
            ),
            onEvent = {},
            onOpenObject = {},
            onCreateObject = {},
            onOpenSettings = {},
        )
    }
}
