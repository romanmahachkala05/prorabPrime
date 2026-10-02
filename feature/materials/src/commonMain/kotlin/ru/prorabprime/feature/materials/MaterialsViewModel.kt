package ru.prorabprime.feature.materials

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.feature.materials.resources.Res
import ru.prorabprime.feature.materials.resources.materials_delete
import ru.prorabprime.feature.materials.resources.materials_delete_message
import ru.prorabprime.feature.materials.resources.materials_delete_title
import ru.prorabprime.feature.materials.resources.materials_saved
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

internal class MaterialsViewModel(
    private val objectId: ObjectId,
    private val stateHolder: IMaterialsStateHolder,
    private val errorHandler: IMaterialsErrorHandler,
    private val actions: MaterialsActions,
) : ViewModel(),
    StateOwner<MaterialsState> by stateHolder {

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onEvent(event: MaterialsEvent) {
        when (event) {
            MaterialsEvent.Retry -> retry()

            MaterialsEvent.AddClicked -> stateHolder.openEditor(MaterialEditorUi())

            MaterialsEvent.AddDefaultsClicked -> addDefaults()

            is MaterialsEvent.EditClicked -> edit(event.materialId)

            is MaterialsEvent.StatusTapped -> advance(event.materialId)

            is MaterialsEvent.TitleChanged -> stateHolder.editForm {
                it.copy(title = event.text, errors = (it.errors - ObjectField.MATERIAL_TITLE).toImmutableMap())
            }

            is MaterialsEvent.StatusPicked -> stateHolder.editForm { it.copy(status = event.status) }

            MaterialsEvent.SaveClicked -> save()

            is MaterialsEvent.DeleteClicked -> {
                stateHolder.closeEditor()
                stateHolder.askToDelete(DELETE_DIALOG, event.materialId)
            }

            MaterialsEvent.EditorDismissed -> stateHolder.closeEditor()

            MaterialsEvent.DialogConfirmed -> deletePending()

            MaterialsEvent.DialogDismissed -> stateHolder.dismissDialog()
        }
    }

    /** The repository reloads this after any write, so the screen never asks for a refresh. */
    private fun load() {
        loadJob?.cancel()
        loadJob = actions.observeMaterials(objectId)
            .onEach(::render)
            .launchIn(viewModelScope)
    }

    private fun retry() {
        stateHolder.showLoading()
        load()
    }

    private fun render(result: Result<ImmutableList<Material>>) {
        result
            .onSuccess { materials -> stateHolder.showMaterials(materials.toUi()) }
            .onFailure { failure -> viewModelScope.launch { errorHandler.onLoadFailure(failure.asAppError()) } }
    }

    private fun edit(materialId: String) {
        val material = state.value.materials?.items?.find { it.id == materialId } ?: return
        stateHolder.openEditor(MaterialEditorUi(material.id, material.title, material.status))
    }

    /** A tap on a row's status saves the next one right away; the list reloads with it. */
    private fun advance(materialId: String) {
        val material = state.value.materials?.items?.find { it.id == materialId } ?: return
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.saveMaterial.update(MaterialId(materialId), MaterialDraft(material.title, material.status.next()))
                .onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun save() {
        val editor = state.value.editor ?: return
        if (editor.isSaving) return
        val draft = MaterialDraft(editor.title, editor.status)
        stateHolder.editForm { it.copy(isSaving = true) }
        launchCatching(onFailure = { onSaveFailure(it.asAppError()) }) {
            val result = if (editor.materialId == null) {
                actions.saveMaterial.create(objectId, draft)
            } else {
                actions.saveMaterial.update(MaterialId(editor.materialId), draft)
            }
            result
                .onSuccess {
                    stateHolder.closeEditor()
                    errorHandler.onSaved(UiText.Resource(Res.string.materials_saved))
                }
                .onFailure { onSaveFailure(it.asAppError()) }
        }
    }

    private suspend fun onSaveFailure(error: AppError) {
        stateHolder.editForm { it.copy(isSaving = false) }
        if (error is AppError.Validation) {
            stateHolder.editForm { it.copy(errors = error.fieldErrors) }
        } else {
            errorHandler.onActionFailure(error)
        }
    }

    private fun deletePending() {
        val id = state.value.pendingDeleteId
        stateHolder.dismissDialog()
        if (id == null) return
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.deleteMaterial(MaterialId(id)).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun addDefaults() {
        launchCatching(onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            actions.addDefaults(objectId).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private companion object {
        val DELETE_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.materials_delete_title),
            message = UiText.Resource(Res.string.materials_delete_message),
            confirmLabel = UiText.Resource(Res.string.materials_delete),
            destructive = true,
        )
    }
}

private fun ImmutableList<Material>.toUi() =
    MaterialsUi(map { MaterialUi(it.id.value, it.title, it.status, it.isPending) }.toImmutableList())
