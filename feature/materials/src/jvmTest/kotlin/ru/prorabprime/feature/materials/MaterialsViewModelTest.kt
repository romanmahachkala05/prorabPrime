package ru.prorabprime.feature.materials

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.usecase.AddDefaultMaterialsUseCase
import ru.prorabprime.domain.usecase.DeleteMaterialUseCase
import ru.prorabprime.domain.usecase.ObserveMaterialsUseCase
import ru.prorabprime.domain.usecase.SaveMaterialUseCase
import ru.prorabprime.testing.FakeMaterialsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.toUiText

class MaterialsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMaterialsRepository()
    private val notifier = FakeSnackbarNotifier()
    private val objectId = ObjectId("o1")

    private val viewModel by lazy {
        val holder = MaterialsStateHolder()
        MaterialsViewModel(
            objectId = objectId,
            stateHolder = holder,
            errorHandler = MaterialsErrorHandler(holder, notifier),
            actions = MaterialsActions(
                observeMaterials = ObserveMaterialsUseCase(repository),
                saveMaterial = SaveMaterialUseCase(repository),
                deleteMaterial = DeleteMaterialUseCase(repository),
                addDefaults = AddDefaultMaterialsUseCase(repository),
            ),
        )
    }

    private val state get() = viewModel.state.value

    private fun withMaterials() {
        repository.materials.value = persistentListOf(
            Material(MaterialId("m1"), "Плитка", MaterialStatus.IN_APARTMENT),
            Material(MaterialId("m2"), "Двери", MaterialStatus.CHOSEN),
            Material(MaterialId("m3"), "Ламинат", MaterialStatus.NOT_CHOSEN),
        )
    }

    @Test
    fun `the checklist is shown with its progress`() {
        withMaterials()

        assertThat(state.status).isEqualTo(MaterialsStatus.Content)
        assertThat(state.materials?.items?.map { it.title }).containsExactly("Плитка", "Двери", "Ламинат").inOrder()
        assertThat(state.materials?.total).isEqualTo(3)
        assertThat(state.materials?.inApartment).isEqualTo(1)
    }

    @Test
    fun `a failed first load takes over the screen and retry loads again`() {
        repository.loadError.value = AppError.Network

        assertThat(state.status).isEqualTo(MaterialsStatus.Error(AppError.Network.toUiText()))

        repository.loadError.value = null
        viewModel.onEvent(MaterialsEvent.Retry)

        assertThat(state.status).isEqualTo(MaterialsStatus.Content)
    }

    @Test
    fun `a tap on the status saves the next one`() {
        withMaterials()

        viewModel.onEvent(MaterialsEvent.StatusTapped("m3"))
        viewModel.onEvent(MaterialsEvent.StatusTapped("m1"))

        assertThat(repository.updated).containsExactly(
            MaterialId("m3") to MaterialDraft("Ламинат", MaterialStatus.CHOSEN),
            MaterialId("m1") to MaterialDraft("Плитка", MaterialStatus.NOT_CHOSEN),
        ).inOrder()
    }

    @Test
    fun `a new material is typed, saved and closes the form`() {
        withMaterials()

        viewModel.onEvent(MaterialsEvent.AddClicked)
        assertThat(state.editor).isEqualTo(MaterialEditorUi())
        viewModel.onEvent(MaterialsEvent.TitleChanged(" Обои "))
        viewModel.onEvent(MaterialsEvent.StatusPicked(MaterialStatus.CHOSEN))
        viewModel.onEvent(MaterialsEvent.SaveClicked)

        assertThat(repository.added).containsExactly(objectId to MaterialDraft("Обои", MaterialStatus.CHOSEN))
        assertThat(state.editor).isNull()
    }

    @Test
    fun `a blank title stays open with the field marked, until it is typed`() {
        withMaterials()
        viewModel.onEvent(MaterialsEvent.AddClicked)

        viewModel.onEvent(MaterialsEvent.SaveClicked)

        assertThat(repository.added).isEmpty()
        assertThat(state.editor?.errors).containsExactly(ObjectField.MATERIAL_TITLE, FieldProblem.REQUIRED)
        assertThat(state.editor?.isSaving).isFalse()

        viewModel.onEvent(MaterialsEvent.TitleChanged("О"))
        assertThat(state.editor?.errors).isEmpty()
    }

    @Test
    fun `editing fills the form and saving updates the material`() {
        withMaterials()

        viewModel.onEvent(MaterialsEvent.EditClicked("m2"))
        assertThat(state.editor).isEqualTo(MaterialEditorUi("m2", "Двери", MaterialStatus.CHOSEN))
        viewModel.onEvent(MaterialsEvent.TitleChanged("Межкомнатные двери"))
        viewModel.onEvent(MaterialsEvent.SaveClicked)

        assertThat(repository.updated)
            .containsExactly(MaterialId("m2") to MaterialDraft("Межкомнатные двери", MaterialStatus.CHOSEN))
    }

    @Test
    fun `deleting asks first and then deletes, and a dismissed dialog forgets it`() {
        withMaterials()
        viewModel.onEvent(MaterialsEvent.EditClicked("m2"))

        viewModel.onEvent(MaterialsEvent.DeleteClicked("m2"))
        assertThat(state.editor).isNull()
        assertThat(state.pendingDeleteId).isEqualTo("m2")
        viewModel.onEvent(MaterialsEvent.DialogDismissed)
        viewModel.onEvent(MaterialsEvent.DialogConfirmed)
        assertThat(repository.deleted).isEmpty()

        viewModel.onEvent(MaterialsEvent.DeleteClicked("m2"))
        viewModel.onEvent(MaterialsEvent.DialogConfirmed)

        assertThat(repository.deleted).containsExactly(MaterialId("m2"))
        assertThat(state.dialog).isNull()
    }

    @Test
    fun `the standard set is requested, and a failed write is shown`() {
        withMaterials()

        viewModel.onEvent(MaterialsEvent.AddDefaultsClicked)
        assertThat(repository.defaultsAdded).isEqualTo(1)

        repository.writeError = AppError.Network
        viewModel.onEvent(MaterialsEvent.StatusTapped("m3"))

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }
}
