package ru.prorabprime.feature.objects.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.designsystem.components.FilterChip
import ru.prorabprime.designsystem.components.TextButton
import ru.prorabprime.designsystem.theme.Spacing
import ru.prorabprime.domain.model.ContactRole
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.feature.objects.components.message
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.contact_cancel
import ru.prorabprime.feature.objects.resources.contact_delete
import ru.prorabprime.feature.objects.resources.contact_name
import ru.prorabprime.feature.objects.resources.contact_phone
import ru.prorabprime.feature.objects.resources.contact_role
import ru.prorabprime.feature.objects.resources.contact_role_client
import ru.prorabprime.feature.objects.resources.contact_role_executor
import ru.prorabprime.feature.objects.resources.contact_role_other
import ru.prorabprime.feature.objects.resources.contact_save
import ru.prorabprime.feature.objects.resources.objectdetails_add_contact
import ru.prorabprime.feature.objects.resources.objectdetails_call
import ru.prorabprime.feature.objects.resources.objectdetails_contacts
import ru.prorabprime.feature.objects.resources.objectdetails_contacts_empty
import ru.prorabprime.feature.objects.resources.objectdetails_edit_contact

internal val ContactRole.label: StringResource
    get() = when (this) {
        ContactRole.CLIENT -> Res.string.contact_role_client
        ContactRole.EXECUTOR -> Res.string.contact_role_executor
        ContactRole.OTHER -> Res.string.contact_role_other
    }

/** Opens the dialer with the number filled in; nothing is called without the user. */
internal fun String.asTelUri(): String = "tel:${filter { it.isDigit() || it == '+' }}"

@Composable
internal fun ContactsSection(
    contacts: ImmutableList<ContactUi>,
    onAdd: () -> Unit,
    onEdit: (ContactUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.padding(horizontal = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.objectdetails_contacts),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, stringResource(Res.string.objectdetails_add_contact))
            }
        }
        if (contacts.isEmpty()) {
            Text(
                stringResource(Res.string.objectdetails_contacts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.m),
            )
        }
        contacts.forEach { contact ->
            ListItem(
                headlineContent = { Text(contact.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(stringResource(contact.role.label), contact.phone).joinToString(" · "),
                    )
                },
                trailingContent = {
                    Row {
                        contact.phone?.let { phone ->
                            IconButton(onClick = { uriHandler.openUri(phone.asTelUri()) }) {
                                Icon(Icons.Default.Call, stringResource(Res.string.objectdetails_call))
                            }
                        }
                        IconButton(onClick = { onEdit(contact) }) {
                            Icon(Icons.Default.Edit, stringResource(Res.string.objectdetails_edit_contact))
                        }
                    }
                },
                modifier = Modifier.clickable(enabled = contact.phone != null) {
                    contact.phone?.let { uriHandler.openUri(it.asTelUri()) }
                },
            )
        }
    }
}

@Composable
internal fun ContactEditorDialog(editor: ContactEditorUi, onEvent: (ObjectDetailsEvent) -> Unit) {
    val title = if (editor.contactId == null) {
        Res.string.objectdetails_add_contact
    } else {
        Res.string.objectdetails_edit_contact
    }
    AlertDialog(
        onDismissRequest = { onEvent(ObjectDetailsEvent.ContactEditorDismissed) },
        title = { Text(stringResource(title)) },
        text = { ContactForm(editor, onEvent) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(ObjectDetailsEvent.ContactSaveClicked) },
                enabled = !editor.isSaving,
            ) { Text(stringResource(Res.string.contact_save)) }
        },
        dismissButton = {
            Row {
                editor.contactId?.let { id ->
                    TextButton(onClick = { onEvent(ObjectDetailsEvent.DeleteContactClicked(id)) }) {
                        Text(stringResource(Res.string.contact_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = { onEvent(ObjectDetailsEvent.ContactEditorDismissed) }) {
                    Text(stringResource(Res.string.contact_cancel))
                }
            }
        },
    )
}

@Composable
private fun ContactForm(editor: ContactEditorUi, onEvent: (ObjectDetailsEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        ContactTextField(
            value = editor.name,
            label = Res.string.contact_name,
            problem = editor.errors[ObjectField.CONTACT_NAME],
            onValueChange = { onEvent(ObjectDetailsEvent.ContactNameChanged(it)) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )
        ContactTextField(
            value = editor.phone,
            label = Res.string.contact_phone,
            problem = editor.errors[ObjectField.CONTACT_PHONE],
            onValueChange = { onEvent(ObjectDetailsEvent.ContactPhoneChanged(it)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        Text(
            stringResource(Res.string.contact_role),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            ContactRole.entries.forEach { role ->
                FilterChip(
                    selected = role == editor.role,
                    onClick = { onEvent(ObjectDetailsEvent.ContactRoleChanged(role)) },
                    label = { Text(stringResource(role.label)) },
                )
            }
        }
    }
}

@Composable
private fun ContactTextField(
    value: String,
    label: StringResource,
    problem: FieldProblem?,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        isError = problem != null,
        supportingText = problem?.let { { Text(stringResource(it.message)) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        modifier = Modifier.fillMaxWidth(),
    )
}
