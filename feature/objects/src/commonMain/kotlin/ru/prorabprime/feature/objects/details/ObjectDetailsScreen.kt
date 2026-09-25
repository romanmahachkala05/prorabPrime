package ru.prorabprime.feature.objects.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.components.StatusChip
import ru.prorabprime.feature.objects.photos.PhotoCarousel
import ru.prorabprime.feature.objects.photos.rememberPhotoSources
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_address
import ru.prorabprime.feature.objects.resources.objectdetails_back
import ru.prorabprime.feature.objects.resources.objectdetails_client
import ru.prorabprime.feature.objects.resources.objectdetails_delete
import ru.prorabprime.feature.objects.resources.objectdetails_edit
import ru.prorabprime.feature.objects.resources.objectdetails_notes
import ru.prorabprime.feature.objects.resources.objectdetails_phone
import ru.prorabprime.feature.objects.resources.objectdetails_pick_photos
import ru.prorabprime.feature.objects.resources.objectdetails_take_photo
import ru.prorabprime.ui.resolve

/** [onOpenPhoto] gets the photo's id, for the full-screen viewer. */
@Composable
fun ObjectDetailsScreen(
    objectId: String,
    onEdit: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ObjectDetailsViewModel = koinViewModel(key = objectId) { parametersOf(ObjectId(objectId)) }
    ObjectDetailsScreen(onEdit, onOpenPhoto, onClose, modifier, viewModel)
}

@Composable
private fun ObjectDetailsScreen(
    onEdit: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    viewModel: ObjectDetailsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isClosed) { if (state.isClosed) onClose() }
    val sources = rememberPhotoSources(onPicked = { viewModel.onEvent(ObjectDetailsEvent.PhotosPicked(it)) })
    ObjectDetailsContent(
        state = state,
        onEvent = viewModel::onEvent,
        onEdit = onEdit,
        onBack = onClose,
        onOpenPhoto = onOpenPhoto,
        onTakePhoto = sources::takePhoto,
        onPickPhotos = sources::pickFromGallery,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObjectDetailsContent(
    state: ObjectDetailsState,
    onEvent: (ObjectDetailsEvent) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPhoto: (photoId: String) -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onPickPhotos: () -> Unit = {},
) {
    // Whether the "camera or gallery" sheet is open is view state, like a menu.
    var choosingSource by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = { DetailsTopBar(state, onEvent, onEdit, onBack) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (state.isDeleting) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (val status = state.status) {
                ObjectDetailsStatus.Content -> state.details?.let { details ->
                    DetailsBody(details, state, onEvent, onOpenPhoto, onAddPhoto = { choosingSource = true })
                }

                ObjectDetailsStatus.Loading -> LoadingBox()

                is ObjectDetailsStatus.Error -> ErrorMessage(status.message, onRetry = {
                    onEvent(ObjectDetailsEvent.Retry)
                })
            }
        }
    }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(ObjectDetailsEvent.DialogConfirmed) },
        onDismiss = { onEvent(ObjectDetailsEvent.DialogDismissed) },
    )
    if (choosingSource) {
        PhotoSourceSheet(
            onTakePhoto = {
                choosingSource = false
                onTakePhoto()
            },
            onPickPhotos = {
                choosingSource = false
                onPickPhotos()
            },
            onDismiss = { choosingSource = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoSourceSheet(
    onTakePhoto: () -> Unit,
    onPickPhotos: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(stringResource(Res.string.objectdetails_take_photo)) },
            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onTakePhoto),
        )
        ListItem(
            headlineContent = { Text(stringResource(Res.string.objectdetails_pick_photos)) },
            leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onPickPhotos).padding(bottom = Spacing.l),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailsTopBar(
    state: ObjectDetailsState,
    onEvent: (ObjectDetailsEvent) -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(state.details?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.objectdetails_back))
            }
        },
        actions = {
            if (state.status == ObjectDetailsStatus.Content) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, stringResource(Res.string.objectdetails_edit))
                }
                IconButton(onClick = {
                    onEvent(ObjectDetailsEvent.DeleteClicked)
                }, enabled = !state.isDeleting) {
                    Icon(Icons.Default.Delete, stringResource(Res.string.objectdetails_delete))
                }
            }
        },
    )
}

@Composable
private fun DetailsBody(
    details: ObjectDetailsUi,
    state: ObjectDetailsState,
    onEvent: (ObjectDetailsEvent) -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
    onAddPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        PhotoCarousel(
            photos = details.photos,
            uploads = state.uploads,
            onAddClick = onAddPhoto,
            onPhotoClick = { onOpenPhoto(it.id) },
            onMakeCover = { onEvent(ObjectDetailsEvent.MakeCoverClicked(it.id)) },
            onDelete = { onEvent(ObjectDetailsEvent.DeletePhotoClicked(it.id)) },
            onRetryUpload = { onEvent(ObjectDetailsEvent.RetryUpload(it.image)) },
            onDismissUpload = { onEvent(ObjectDetailsEvent.DismissUpload(it.image)) },
        )
        DetailsFields(details)
    }
}

@Composable
private fun DetailsFields(details: ObjectDetailsUi) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        StatusChip(details.status)
        details.address?.let { Field(stringResource(Res.string.objectdetails_address), it) }
        details.clientName?.let { Field(stringResource(Res.string.objectdetails_client), it) }
        details.clientPhone?.let { phone ->
            // Opens the dialer with the number filled in; nothing is called without the user.
            Field(
                label = stringResource(Res.string.objectdetails_phone),
                value = phone,
                valueColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    uriHandler.openUri("tel:${phone.filter { it.isDigit() || it == '+' }}")
                },
            )
        }
        details.notes?.let { Field(stringResource(Res.string.objectdetails_notes), it) }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor)
    }
}

@Preview
@Composable
private fun ObjectDetailsContentPreview() {
    ProrabTheme {
        ObjectDetailsContent(
            state = ObjectDetailsState(
                status = ObjectDetailsStatus.Content,
                details = ObjectDetailsUi(
                    title = "Кухня у Ивановых",
                    address = "ул. Ленина, 1, кв. 5",
                    status = ObjectStatus.IN_PROGRESS,
                    clientName = "Иван",
                    clientPhone = "+7 900 123-45-67",
                    notes = "Ключи у консьержа",
                ),
            ),
            onEvent = {},
            onEdit = {},
            onBack = {},
        )
    }
}
