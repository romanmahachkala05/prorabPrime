package ru.prorabprime.feature.objects.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.components.StatusChip
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectslist_add
import ru.prorabprime.feature.objects.resources.objectslist_clear_search
import ru.prorabprime.feature.objects.resources.objectslist_empty
import ru.prorabprime.feature.objects.resources.objectslist_empty_action
import ru.prorabprime.feature.objects.resources.objectslist_no_photos
import ru.prorabprime.feature.objects.resources.objectslist_nothing_found
import ru.prorabprime.feature.objects.resources.objectslist_photo_count
import ru.prorabprime.feature.objects.resources.objectslist_search
import ru.prorabprime.feature.objects.resources.objectslist_settings
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_asc
import ru.prorabprime.feature.objects.resources.objectslist_sort_address_desc
import ru.prorabprime.feature.objects.resources.objectslist_sort_created
import ru.prorabprime.feature.objects.resources.objectslist_sort_updated
import ru.prorabprime.feature.objects.resources.objectslist_title
import ru.prorabprime.ui.UiText

@Composable
fun ObjectsListScreen(
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ObjectsListScreen(onOpenObject, onCreateObject, onOpenSettings, modifier, koinViewModel())
}

@Composable
private fun ObjectsListScreen(
    onOpenObject: (id: String) -> Unit,
    onCreateObject: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
    viewModel: ObjectsListViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObjectsListContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenObject = onOpenObject,
        onCreateObject = onCreateObject,
        onOpenSettings = onOpenSettings,
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
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.objectslist_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(Res.string.objectslist_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateObject,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(Res.string.objectslist_add)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(state.search, onEvent)
            SortMenu(state.sort, onSelect = { onEvent(ObjectsListEvent.SortSelected(it)) })
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
    }
}

@Composable
private fun SearchField(text: String, onEvent: (ObjectsListEvent) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { onEvent(ObjectsListEvent.SearchChanged(it)) },
        placeholder = { Text(stringResource(Res.string.objectslist_search)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onEvent(ObjectsListEvent.SearchChanged("")) }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(Res.string.objectslist_clear_search))
                }
            }
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
    )
}

internal val ObjectSort.label: StringResource
    get() = when (this) {
        ObjectSort.ADDRESS_ASC -> Res.string.objectslist_sort_address_asc
        ObjectSort.ADDRESS_DESC -> Res.string.objectslist_sort_address_desc
        ObjectSort.CREATED_NEWEST -> Res.string.objectslist_sort_created
        ObjectSort.UPDATED_NEWEST -> Res.string.objectslist_sort_updated
    }

@Composable
private fun SortMenu(selected: ObjectSort, onSelect: (ObjectSort) -> Unit) {
    // Whether the menu is open is view state with no meaning beyond this composable.
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = Spacing.s)) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(selected.label))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ObjectSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label)) },
                    leadingIcon = { if (sort == selected) Icon(Icons.Default.Check, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun ObjectCards(state: ObjectsListState, onOpenObject: (id: String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Room at the bottom so the FAB never covers the last card.
        contentPadding = PaddingValues(start = Spacing.m, end = Spacing.m, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(state.items, key = { it.id }) { item ->
            ObjectCard(item, onClick = { onOpenObject(item.id) })
        }
    }
}

@Composable
private fun ObjectCard(item: ObjectCardUi, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        ServerImage(
            path = item.cover,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT_RATIO),
        )
        Column(Modifier.padding(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.address?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                StatusChip(item.status)
                Text(
                    if (item.photoCount == 0) {
                        stringResource(Res.string.objectslist_no_photos)
                    } else {
                        pluralStringResource(Res.plurals.objectslist_photo_count, item.photoCount, item.photoCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val COVER_ASPECT_RATIO = 16f / 9f

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
