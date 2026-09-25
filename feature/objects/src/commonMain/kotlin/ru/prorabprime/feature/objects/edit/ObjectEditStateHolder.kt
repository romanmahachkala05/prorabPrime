package ru.prorabprime.feature.objects.edit

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IObjectEditStateHolder : StateOwner<ObjectEditState> {
    fun showForm(form: ObjectForm, isNew: Boolean)

    fun showLoading()

    fun showError(message: UiText)

    /** Clears that field's error: the user is fixing it. */
    fun setField(field: ObjectField, value: String)

    fun setStatus(status: ObjectStatus)

    fun setSaving(saving: Boolean)

    fun showFieldErrors(errors: ImmutableMap<ObjectField, FieldProblem>)

    fun markSaved(result: SaveResult)
}

internal class ObjectEditStateHolder(
    isNew: Boolean,
) : IObjectEditStateHolder {

    private val _state = MutableStateFlow(ObjectEditState(isNew = isNew))
    override val state: StateFlow<ObjectEditState> = _state.asStateFlow()

    override fun showForm(form: ObjectForm, isNew: Boolean) = _state.update {
        it.copy(status = ObjectEditStatus.Content, form = form, isNew = isNew)
    }

    override fun showLoading() = _state.update { it.copy(status = ObjectEditStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = ObjectEditStatus.Error(message)) }

    override fun setField(field: ObjectField, value: String) = _state.update {
        val form = when (field) {
            ObjectField.TITLE -> it.form.copy(title = value)
            ObjectField.ADDRESS -> it.form.copy(address = value)
            ObjectField.CLIENT_NAME -> it.form.copy(clientName = value)
            ObjectField.CLIENT_PHONE -> it.form.copy(clientPhone = value)
            ObjectField.NOTES -> it.form.copy(notes = value)
        }
        it.copy(form = form, fieldErrors = (it.fieldErrors - field).toImmutableMap())
    }

    override fun setStatus(status: ObjectStatus) = _state.update { it.copy(form = it.form.copy(status = status)) }

    override fun setSaving(saving: Boolean) = _state.update { it.copy(isSaving = saving) }

    override fun showFieldErrors(errors: ImmutableMap<ObjectField, FieldProblem>) = _state.update {
        it.copy(fieldErrors = errors, isSaving = false)
    }

    override fun markSaved(result: SaveResult) = _state.update {
        it.copy(saved = result, isSaving = false, fieldErrors = persistentMapOf())
    }
}
