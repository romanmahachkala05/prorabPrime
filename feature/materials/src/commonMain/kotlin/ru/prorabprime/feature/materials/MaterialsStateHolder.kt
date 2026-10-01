package ru.prorabprime.feature.materials

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface IMaterialsStateHolder : StateOwner<MaterialsState> {
    fun showMaterials(materials: MaterialsUi)

    fun showLoading()

    fun showError(message: UiText)

    fun openEditor(editor: MaterialEditorUi)

    /** Applies [transform] to the open form; does nothing when none is open. */
    fun editForm(transform: (MaterialEditorUi) -> MaterialEditorUi)

    fun closeEditor()

    fun askToDelete(dialog: DialogModel, materialId: String)

    /** Closes the dialog and forgets what it would have deleted. */
    fun dismissDialog()
}

internal class MaterialsStateHolder : IMaterialsStateHolder {

    private val _state = MutableStateFlow(MaterialsState())
    override val state: StateFlow<MaterialsState> = _state.asStateFlow()

    override fun showMaterials(materials: MaterialsUi) = _state.update {
        it.copy(status = MaterialsStatus.Content, materials = materials)
    }

    override fun showLoading() = _state.update { it.copy(status = MaterialsStatus.Loading) }

    override fun showError(message: UiText) = _state.update { it.copy(status = MaterialsStatus.Error(message)) }

    override fun openEditor(editor: MaterialEditorUi) = _state.update { it.copy(editor = editor) }

    override fun editForm(transform: (MaterialEditorUi) -> MaterialEditorUi) = _state.update { state ->
        state.copy(editor = state.editor?.let(transform))
    }

    override fun closeEditor() = _state.update { it.copy(editor = null) }

    override fun askToDelete(dialog: DialogModel, materialId: String) = _state.update {
        it.copy(dialog = dialog, pendingDeleteId = materialId)
    }

    override fun dismissDialog() = _state.update { it.copy(dialog = null, pendingDeleteId = null) }
}
