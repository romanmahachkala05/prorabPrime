package ru.prorabprime.feature.objects.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.CreateObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.UpdateObjectUseCase
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.launchCatching

/** What the form edits: nothing yet ([objectId] null) or an existing object. */
internal data class ObjectEditArgs(
    val objectId: ObjectId?,
)

internal class ObjectEditViewModel(
    args: ObjectEditArgs,
    private val savedState: SavedStateHandle,
    private val stateHolder: IObjectEditStateHolder,
    private val errorHandler: IObjectEditErrorHandler,
    private val observeObject: ObserveObjectUseCase,
    private val createObject: CreateObjectUseCase,
    private val updateObject: UpdateObjectUseCase,
) : ViewModel(),
    StateOwner<ObjectEditState> by stateHolder {

    private val objectId = args.objectId

    init {
        val draft = savedState.restoreDraft()
        when {
            // Back from process death: the user's unsaved typing wins over the server's copy.
            draft != null -> stateHolder.showForm(draft, isNew = objectId == null)

            objectId == null -> stateHolder.showForm(ObjectForm(), isNew = true)

            else -> load(objectId)
        }
        // Every change to the form is kept, so process death loses nothing — until it is saved,
        // after which the draft must not come back.
        stateHolder.state
            .onEach { if (it.status == ObjectEditStatus.Content && it.saved == null) savedState.saveDraft(it.form) }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ObjectEditEvent) {
        when (event) {
            is ObjectEditEvent.FieldChanged -> stateHolder.setField(event.field, event.value)
            is ObjectEditEvent.StatusChanged -> stateHolder.setStatus(event.status)
            ObjectEditEvent.SaveClicked -> save()
            ObjectEditEvent.Retry -> objectId?.let(::load)
        }
    }

    private fun load(id: ObjectId) {
        stateHolder.showLoading()
        launchCatching(onFailure = { errorHandler.onLoadFailure(it.asAppError()) }) {
            observeObject(id).first()
                .onSuccess { stateHolder.showForm(it.toForm(), isNew = false) }
                .onFailure { errorHandler.onLoadFailure(it.asAppError()) }
        }
    }

    private fun save() {
        if (state.value.isSaving) return
        stateHolder.setSaving(true)
        val draft = state.value.form.toDraft()
        launchCatching(onFailure = { errorHandler.onSaveFailure(it.asAppError()) }) {
            val result = if (objectId == null) {
                createObject(draft).map { SaveResult.Created(it.value) }
            } else {
                updateObject(objectId, draft).map { SaveResult.Updated }
            }
            result
                .onSuccess {
                    savedState.clearDraft()
                    stateHolder.markSaved(it)
                }.onFailure { errorHandler.onSaveFailure(it.asAppError()) }
        }
    }
}

internal fun ObjectDetails.toForm() = ObjectForm(
    title = title.orEmpty(),
    address = address,
    status = status,
    clientName = clientName.orEmpty(),
    clientPhone = clientPhone.orEmpty(),
    notes = notes.orEmpty(),
)

internal fun ObjectForm.toDraft() = ObjectDraft(
    title = title,
    address = address,
    status = status,
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
)

private const val DRAFT_STATUS = "draft_status"

private fun SavedStateHandle.saveDraft(form: ObjectForm) {
    ObjectField.entries.forEach { this[draftKey(it)] = form.valueOf(it) }
    this[DRAFT_STATUS] = form.status.name
}

private fun SavedStateHandle.restoreDraft(): ObjectForm? {
    val status = get<String>(DRAFT_STATUS)?.let { name -> ObjectStatus.entries.find { it.name == name } } ?: return null
    fun value(field: ObjectField) = get<String>(draftKey(field)).orEmpty()
    return ObjectForm(
        title = value(ObjectField.TITLE),
        address = value(ObjectField.ADDRESS),
        status = status,
        clientName = value(ObjectField.CLIENT_NAME),
        clientPhone = value(ObjectField.CLIENT_PHONE),
        notes = value(ObjectField.NOTES),
    )
}

private fun SavedStateHandle.clearDraft() {
    ObjectField.entries.forEach { remove<String>(draftKey(it)) }
    remove<String>(DRAFT_STATUS)
}

private fun draftKey(field: ObjectField) = "draft_${field.name}"

internal fun ObjectForm.valueOf(field: ObjectField): String = when (field) {
    ObjectField.TITLE -> title
    ObjectField.ADDRESS -> address
    ObjectField.CLIENT_NAME -> clientName
    ObjectField.CLIENT_PHONE -> clientPhone
    ObjectField.NOTES -> notes
}
