package ru.prorabprime.feature.objects.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ru.prorabprime.designsystem.components.ErrorMessage
import ru.prorabprime.designsystem.components.LoadingBox
import ru.prorabprime.designsystem.theme.ProrabTheme
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.feature.objects.components.label
import ru.prorabprime.feature.objects.components.message
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectedit_address
import ru.prorabprime.feature.objects.resources.objectedit_back
import ru.prorabprime.feature.objects.resources.objectedit_client_name
import ru.prorabprime.feature.objects.resources.objectedit_client_phone
import ru.prorabprime.feature.objects.resources.objectedit_notes
import ru.prorabprime.feature.objects.resources.objectedit_save
import ru.prorabprime.feature.objects.resources.objectedit_status
import ru.prorabprime.feature.objects.resources.objectedit_title
import ru.prorabprime.feature.objects.resources.objectedit_title_edit
import ru.prorabprime.feature.objects.resources.objectedit_title_new
import ru.prorabprime.ui.resolve

/**
 * [onCreated] gets the new object's id; [onBack] is called both for leaving and after an edit
 * is saved.
 */
@Composable
fun ObjectEditScreen(
    objectId: String?,
    onCreated: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ObjectEditViewModel = koinViewModel(key = "edit-$objectId") {
        parametersOf(ObjectEditArgs(objectId?.let(::ObjectId)))
    }
    ObjectEditScreen(onCreated, onBack, modifier, viewModel)
}

@Composable
private fun ObjectEditScreen(
    onCreated: (objectId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    viewModel: ObjectEditViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        when (val saved = state.saved) {
            is SaveResult.Created -> onCreated(saved.objectId)
            SaveResult.Updated -> onBack()
            null -> Unit
        }
    }
    ObjectEditContent(state, viewModel::onEvent, onBack, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ObjectEditContent(
    state: ObjectEditState,
    onEvent: (ObjectEditEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) Res.string.objectedit_title_new else Res.string.objectedit_title_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.objectedit_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val status = state.status) {
            ObjectEditStatus.Content -> Form(state, onEvent, Modifier.padding(padding))

            ObjectEditStatus.Loading -> LoadingBox(Modifier.padding(padding))

            is ObjectEditStatus.Error -> ErrorMessage(
                status.message,
                onRetry = { onEvent(ObjectEditEvent.Retry) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun Form(
    state: ObjectEditState,
    onEvent: (ObjectEditEvent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        FormField(state, onEvent, ObjectField.TITLE, Res.string.objectedit_title)
        FormField(state, onEvent, ObjectField.ADDRESS, Res.string.objectedit_address)
        StatusPicker(state.form.status, onSelect = { onEvent(ObjectEditEvent.StatusChanged(it)) })
        FormField(state, onEvent, ObjectField.CLIENT_NAME, Res.string.objectedit_client_name)
        FormField(
            state,
            onEvent,
            ObjectField.CLIENT_PHONE,
            Res.string.objectedit_client_phone,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        FormField(state, onEvent, ObjectField.NOTES, Res.string.objectedit_notes, singleLine = false)
        Button(
            onClick = { onEvent(ObjectEditEvent.SaveClicked) },
            enabled = !state.isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        ) { Text(stringResource(Res.string.objectedit_save)) }
    }
}

@Composable
private fun FormField(
    state: ObjectEditState,
    onEvent: (ObjectEditEvent) -> Unit,
    field: ObjectField,
    label: StringResource,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
) {
    val problem = state.fieldErrors[field]
    OutlinedTextField(
        value = state.form.valueOf(field),
        onValueChange = { onEvent(ObjectEditEvent.FieldChanged(field, it)) },
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.message)) } },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else NOTES_MIN_LINES,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusPicker(selected: ObjectStatus, onSelect: (ObjectStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            stringResource(Res.string.objectedit_status),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ObjectStatus.entries.forEach { status ->
                FilterChip(
                    selected = status == selected,
                    onClick = { onSelect(status) },
                    label = { Text(stringResource(status.label)) },
                )
            }
        }
    }
}

private const val NOTES_MIN_LINES = 3

@Preview
@Composable
private fun ObjectEditContentPreview() {
    ProrabTheme {
        ObjectEditContent(
            state = ObjectEditState(
                status = ObjectEditStatus.Content,
                form = ObjectForm(address = "ул. Ленина, 1", clientName = "Иван"),
            ),
            onEvent = {},
            onBack = {},
        )
    }
}
