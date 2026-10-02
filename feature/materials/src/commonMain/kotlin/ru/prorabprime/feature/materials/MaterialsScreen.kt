package ru.prorabprime.feature.materials

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.DialogHost
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.components.OutlinedButton
import ru.prorabprime.designsystem.components.PendingTitle
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.components.TopAppBar
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.feature.materials.resources.Res
import ru.prorabprime.feature.materials.resources.materials_add
import ru.prorabprime.feature.materials.resources.materials_add_defaults
import ru.prorabprime.feature.materials.resources.materials_back
import ru.prorabprime.feature.materials.resources.materials_cancel
import ru.prorabprime.feature.materials.resources.materials_defaults_hint
import ru.prorabprime.feature.materials.resources.materials_delete
import ru.prorabprime.feature.materials.resources.materials_edit
import ru.prorabprime.feature.materials.resources.materials_empty
import ru.prorabprime.feature.materials.resources.materials_next_status
import ru.prorabprime.feature.materials.resources.materials_progress
import ru.prorabprime.feature.materials.resources.materials_save
import ru.prorabprime.feature.materials.resources.materials_status_field
import ru.prorabprime.feature.materials.resources.materials_title
import ru.prorabprime.feature.materials.resources.materials_title_field

@Composable
fun MaterialsScreen(
    objectId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MaterialsViewModel = koinViewModel(key = "materials-$objectId") { parametersOf(ObjectId(objectId)) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    MaterialsContent(state, viewModel::onEvent, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaterialsContent(
    state: MaterialsState,
    onEvent: (MaterialsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.materials_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.materials_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(MaterialsEvent.AddClicked) }) {
                        Icon(Icons.Default.Add, stringResource(Res.string.materials_add))
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            MaterialsStatus.Content -> state.materials?.let { Checklist(it, onEvent, Modifier.padding(padding)) }

            MaterialsStatus.Loading -> LoadingBox(Modifier.padding(padding))

            is MaterialsStatus.Error -> ErrorMessage(
                status.message,
                onRetry = { onEvent(MaterialsEvent.Retry) },
                modifier = Modifier.padding(padding),
            )
        }
    }
    state.editor?.let { MaterialEditor(it, onEvent) }
    DialogHost(
        dialog = state.dialog,
        onConfirm = { onEvent(MaterialsEvent.DialogConfirmed) },
        onDismiss = { onEvent(MaterialsEvent.DialogDismissed) },
    )
}

@Composable
private fun Checklist(
    materials: MaterialsUi,
    onEvent: (MaterialsEvent) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (materials.items.isEmpty()) {
            EmptyChecklist(onAddDefaults = { onEvent(MaterialsEvent.AddDefaultsClicked) })
            return
        }
        Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
            Text(
                stringResource(Res.string.materials_progress, materials.inApartment, materials.total),
                style = MaterialTheme.typography.titleSmall,
            )
            LinearProgressIndicator(
                progress = { materials.inApartment.toFloat() / materials.total },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )
        }
        LazyColumn {
            items(materials.items, key = { it.id }) { material ->
                MaterialRow(material, onEvent)
            }
        }
    }
}

@Composable
private fun EmptyChecklist(onAddDefaults: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.materials_empty), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(Res.string.materials_defaults_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAddDefaults) { Text(stringResource(Res.string.materials_add_defaults)) }
    }
}

/** The title opens the form; the status chip is one tap away from the next of the three states. */
@Composable
private fun MaterialRow(material: MaterialUi, onEvent: (MaterialsEvent) -> Unit) {
    ListItem(
        headlineContent = { PendingTitle(material.title, material.isPending) },
        trailingContent = {
            AssistChip(
                onClick = { onEvent(MaterialsEvent.StatusTapped(material.id)) },
                label = { Text(stringResource(material.status.label)) },
                leadingIcon = {
                    if (material.status == MaterialStatus.IN_APARTMENT) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(Res.string.materials_next_status))
                    }
                },
            )
        },
        modifier = Modifier.clickable { onEvent(MaterialsEvent.EditClicked(material.id)) },
    )
}

@Composable
private fun MaterialEditor(editor: MaterialEditorUi, onEvent: (MaterialsEvent) -> Unit) {
    val title = if (editor.materialId == null) Res.string.materials_add else Res.string.materials_edit
    AlertDialog(
        onDismissRequest = { if (!editor.isSaving) onEvent(MaterialsEvent.EditorDismissed) },
        title = { Text(stringResource(title)) },
        text = { MaterialForm(editor, onEvent) },
        confirmButton = {
            TextButton(onClick = { onEvent(MaterialsEvent.SaveClicked) }, loading = editor.isSaving) {
                Text(stringResource(Res.string.materials_save))
            }
        },
        dismissButton = {
            Row {
                editor.materialId?.let { id ->
                    TextButton(onClick = { onEvent(MaterialsEvent.DeleteClicked(id)) }, enabled = !editor.isSaving) {
                        Text(stringResource(Res.string.materials_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { onEvent(MaterialsEvent.EditorDismissed) }, enabled = !editor.isSaving) {
                    Text(stringResource(Res.string.materials_cancel))
                }
            }
        },
    )
}

@Composable
private fun MaterialForm(editor: MaterialEditorUi, onEvent: (MaterialsEvent) -> Unit) {
    val problem = editor.errors[ObjectField.MATERIAL_TITLE]
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        OutlinedTextField(
            value = editor.title,
            onValueChange = { onEvent(MaterialsEvent.TitleChanged(it)) },
            label = { Text(stringResource(Res.string.materials_title_field)) },
            isError = problem != null,
            supportingText = problem?.let { { Text(stringResource(it.message)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(Res.string.materials_status_field),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MaterialStatus.entries.forEach { status ->
                FilterChip(
                    selected = status == editor.status,
                    onClick = { onEvent(MaterialsEvent.StatusPicked(status)) },
                    label = { Text(stringResource(status.label)) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MaterialsContentPreview() {
    ProrabTheme {
        MaterialsContent(
            state = MaterialsState(
                status = MaterialsStatus.Content,
                materials = MaterialsUi(
                    persistentListOf(
                        MaterialUi("1", "Плитка", MaterialStatus.IN_APARTMENT),
                        MaterialUi("2", "Двери", MaterialStatus.CHOSEN),
                        MaterialUi("3", "Ламинат", MaterialStatus.NOT_CHOSEN),
                    ),
                ),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
