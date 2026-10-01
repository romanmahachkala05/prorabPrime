package ru.prorabprime.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.EmptyMessage
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.ServerImage
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.feature.trash.resources.Res
import ru.prorabprime.feature.trash.resources.trash_back
import ru.prorabprime.feature.trash.resources.trash_days_left
import ru.prorabprime.feature.trash.resources.trash_empty_all
import ru.prorabprime.feature.trash.resources.trash_empty_hint
import ru.prorabprime.feature.trash.resources.trash_kind_photo
import ru.prorabprime.feature.trash.resources.trash_kind_receipt
import ru.prorabprime.feature.trash.resources.trash_photo_count
import ru.prorabprime.feature.trash.resources.trash_purge
import ru.prorabprime.feature.trash.resources.trash_restore
import ru.prorabprime.feature.trash.resources.trash_section_objects
import ru.prorabprime.feature.trash.resources.trash_section_photos
import ru.prorabprime.feature.trash.resources.trash_title
import ru.prorabprime.ui.UiText

/** What was deleted and is still kept: restore it, or remove it for good. */
@Composable
fun TrashScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: TrashViewModel = koinViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrashContent(state, viewModel::onEvent, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrashContent(
    state: TrashState,
    onEvent: (TrashEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.trash_back))
                    }
                },
                actions = {
                    if (state.status == TrashStatus.Content && !state.isEmpty) {
                        TextButton(onClick = { onEvent(TrashEvent.EmptyClicked) }, enabled = !state.isBusy) {
                            Text(stringResource(Res.string.trash_empty_all))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val status = state.status) {
                TrashStatus.Content ->
                    if (state.isEmpty) {
                        EmptyMessage(UiText.Resource(Res.string.trash_empty_hint))
                    } else {
                        Items(state, onEvent)
                    }

                TrashStatus.Loading -> LoadingBox()

                is TrashStatus.Error -> ErrorMessage(status.message, onRetry = { onEvent(TrashEvent.Retry) })
            }
        }
    }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(TrashEvent.DialogConfirmed) },
        onDismiss = { onEvent(TrashEvent.DialogDismissed) },
    )
}

@Composable
private fun Items(state: TrashState, onEvent: (TrashEvent) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (state.objects.isNotEmpty()) {
            item(key = "objects-title") { SectionTitle(Res.string.trash_section_objects) }
            items(state.objects, key = { "object:" + it.id }) { item ->
                Entry(
                    headline = item.title,
                    support = listOfNotNull(
                        item.address,
                        stringResource(Res.string.trash_photo_count, item.photoCount),
                    ).joinToString(" · "),
                    daysLeft = item.daysLeft,
                    thumb = { ServerImage(item.cover, contentDescription = null, modifier = Modifier.fillMaxSize()) },
                    enabled = !state.isBusy,
                    onRestore = { onEvent(TrashEvent.RestoreObject(item.id)) },
                    onPurge = { onEvent(TrashEvent.PurgeObjectClicked(item.id)) },
                )
                HorizontalDivider()
            }
        }
        if (state.photos.isNotEmpty()) {
            item(key = "photos-title") { SectionTitle(Res.string.trash_section_photos) }
            items(state.photos, key = { "photo:" + it.id }) { item ->
                val kind = if (item.kind == AttachmentKind.RECEIPT) {
                    Res.string.trash_kind_receipt
                } else {
                    Res.string.trash_kind_photo
                }
                Entry(
                    headline = item.objectTitle,
                    support = stringResource(kind),
                    daysLeft = item.daysLeft,
                    thumb = { ServerImage(item.thumb, contentDescription = null, modifier = Modifier.fillMaxSize()) },
                    enabled = !state.isBusy,
                    onRestore = { onEvent(TrashEvent.RestorePhoto(item.id)) },
                    onPurge = { onEvent(TrashEvent.PurgePhotoClicked(item.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionTitle(title: org.jetbrains.compose.resources.StringResource) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = Spacing.m, end = Spacing.m, top = Spacing.m, bottom = Spacing.xs),
    )
}

@Composable
private fun Entry(
    headline: String,
    support: String,
    daysLeft: Int,
    thumb: @Composable () -> Unit,
    enabled: Boolean,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(headline, maxLines = 1) },
        supportingContent = {
            Text("$support · " + pluralStringResource(Res.plurals.trash_days_left, daysLeft, daysLeft))
        },
        leadingContent = {
            Box(Modifier.size(THUMB_SIZE).clip(RoundedCornerShape(THUMB_RADIUS))) { thumb() }
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(onClick = onRestore, enabled = enabled) {
                    Icon(Icons.Default.Refresh, stringResource(Res.string.trash_restore))
                }
                IconButton(onClick = onPurge, enabled = enabled) {
                    Icon(
                        Icons.Default.Delete,
                        stringResource(Res.string.trash_purge),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

private val THUMB_SIZE = 56.dp
private val THUMB_RADIUS = 8.dp
