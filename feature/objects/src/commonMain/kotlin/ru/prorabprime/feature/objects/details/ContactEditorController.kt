package ru.prorabprime.feature.objects.details

import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.DeleteContactUseCase
import ru.prorabprime.domain.usecase.SaveContactUseCase
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_contact_saved
import ru.prorabprime.feature.objects.resources.objectdetails_delete_confirm
import ru.prorabprime.feature.objects.resources.objectdetails_delete_contact_message
import ru.prorabprime.feature.objects.resources.objectdetails_delete_contact_title
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

/** The contact form of the details screen: opening, typing, saving, and asking before a delete. */
internal class ContactEditorController(
    private val objectId: ObjectId,
    private val scope: CoroutineScope,
    private val stateHolder: IObjectDetailsStateHolder,
    private val errorHandler: IObjectDetailsErrorHandler,
    private val saveContact: SaveContactUseCase,
    private val deleteContact: DeleteContactUseCase,
) {
    fun onEvent(event: ContactEvent) {
        when (event) {
            ObjectDetailsEvent.AddContactClicked -> stateHolder.openContactEditor(ContactEditorUi())

            is ObjectDetailsEvent.EditContactClicked -> open(event.contactId)

            is ObjectDetailsEvent.DeleteContactClicked -> {
                stateHolder.closeContactEditor()
                stateHolder.askToConfirm(DELETE_DIALOG, ObjectDetailsAction.DeleteContact(event.contactId))
            }

            is ObjectDetailsEvent.ContactNameChanged -> stateHolder.editContact {
                it.copy(name = event.value, errors = (it.errors - ObjectField.CONTACT_NAME).toImmutableMap())
            }

            is ObjectDetailsEvent.ContactPhoneChanged -> stateHolder.editContact {
                it.copy(phone = event.value, errors = (it.errors - ObjectField.CONTACT_PHONE).toImmutableMap())
            }

            is ObjectDetailsEvent.ContactRoleChanged -> stateHolder.editContact { it.copy(role = event.value) }

            ObjectDetailsEvent.ContactSaveClicked -> save()

            ObjectDetailsEvent.ContactEditorDismissed -> stateHolder.closeContactEditor()
        }
    }

    /** Runs once the user confirmed [ObjectDetailsAction.DeleteContact]. */
    fun delete(contactId: String) {
        scope.launchCatching(TAG, onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            deleteContact(ContactId(contactId)).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun open(contactId: String) {
        val contact = stateHolder.state.value.details?.contacts?.find { it.id == contactId } ?: return
        stateHolder.openContactEditor(
            ContactEditorUi(
                contactId = contact.id,
                name = contact.name,
                phone = contact.phone.orEmpty(),
                role = contact.role,
            ),
        )
    }

    private fun save() {
        val editor = stateHolder.state.value.contactEditor ?: return
        if (editor.isSaving) return
        val draft = ContactDraft(editor.name, editor.phone, editor.role)
        stateHolder.editContact { it.copy(isSaving = true) }
        scope.launchCatching(TAG, onFailure = { onSaveFailure(it.asAppError()) }) {
            val result = if (editor.contactId == null) {
                saveContact.create(objectId, draft).map { }
            } else {
                saveContact.update(ContactId(editor.contactId), draft)
            }
            result
                .onSuccess {
                    stateHolder.closeContactEditor()
                    errorHandler.onSaved(UiText.Resource(Res.string.objectdetails_contact_saved))
                }
                .onFailure { onSaveFailure(it.asAppError()) }
        }
    }

    private suspend fun onSaveFailure(error: AppError) {
        stateHolder.editContact { it.copy(isSaving = false) }
        if (error is AppError.Validation) {
            stateHolder.editContact { it.copy(errors = error.fieldErrors) }
        } else {
            errorHandler.onActionFailure(error)
        }
    }

    private companion object {
        const val TAG = "ContactEditorController"
        val DELETE_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.objectdetails_delete_contact_title),
            message = UiText.Resource(Res.string.objectdetails_delete_contact_message),
            confirmLabel = UiText.Resource(Res.string.objectdetails_delete_confirm),
            destructive = true,
        )
    }
}
