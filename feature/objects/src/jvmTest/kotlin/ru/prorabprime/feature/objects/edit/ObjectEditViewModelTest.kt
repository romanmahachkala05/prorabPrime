package ru.prorabprime.feature.objects.edit

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.usecase.CreateObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.UpdateObjectUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectDetails
import ru.prorabprime.ui.toUiText

class ObjectEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository()
    private val notifier = FakeSnackbarNotifier()
    private val savedState = SavedStateHandle()

    private fun viewModel(objectId: String? = null): ObjectEditViewModel {
        val args = ObjectEditArgs(objectId?.let(::ObjectId))
        val holder = ObjectEditStateHolder(isNew = args.objectId == null)
        return ObjectEditViewModel(
            args = args,
            savedState = savedState,
            stateHolder = holder,
            errorHandler = ObjectEditErrorHandler(holder, notifier),
            observeObject = ObserveObjectUseCase(objects),
            createObject = CreateObjectUseCase(objects),
            updateObject = UpdateObjectUseCase(objects),
        )
    }

    private fun ObjectEditViewModel.type(field: ObjectField, value: String) =
        onEvent(ObjectEditEvent.FieldChanged(field, value))

    @Test
    fun `a new object starts with an empty form`() {
        val state = viewModel().state.value

        assertThat(state.isNew).isTrue()
        assertThat(state.status).isEqualTo(ObjectEditStatus.Content)
        assertThat(state.form).isEqualTo(ObjectForm())
    }

    @Test
    fun `creating saves the form and reports the new id`() {
        val vm = viewModel()
        vm.type(ObjectField.ADDRESS, " Тверская, 5 ")
        vm.onEvent(ObjectEditEvent.StatusChanged(ObjectStatus.PLANNED))

        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(objects.created).containsExactly(ObjectDraft(address = "Тверская, 5", status = ObjectStatus.PLANNED))
        assertThat(vm.state.value.saved).isEqualTo(SaveResult.Created("created-1"))
    }

    @Test
    fun `a missing address is marked on the field and nothing is sent`() {
        val vm = viewModel()

        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(vm.state.value.fieldErrors).isEqualTo(persistentMapOf(ObjectField.ADDRESS to FieldProblem.REQUIRED))
        assertThat(vm.state.value.isSaving).isFalse()
        assertThat(objects.created).isEmpty()
    }

    @Test
    fun `typing in a field clears its error`() {
        val vm = viewModel()
        vm.onEvent(ObjectEditEvent.SaveClicked)

        vm.type(ObjectField.ADDRESS, "Т")

        assertThat(vm.state.value.fieldErrors).isEmpty()
    }

    @Test
    fun `the server's field errors are marked too`() {
        objects.writeError = AppError.Validation(persistentMapOf(ObjectField.CLIENT_PHONE to FieldProblem.INVALID))
        val vm = viewModel()
        vm.type(ObjectField.ADDRESS, "Тверская, 5")

        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(
            vm.state.value.fieldErrors,
        ).isEqualTo(persistentMapOf(ObjectField.CLIENT_PHONE to FieldProblem.INVALID))
    }

    @Test
    fun `other save failures keep the form and say why`() {
        objects.writeError = AppError.Network
        val vm = viewModel()
        vm.type(ObjectField.ADDRESS, "Тверская, 5")

        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(vm.state.value.form.address).isEqualTo("Тверская, 5")
        assertThat(vm.state.value.saved).isNull()
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }

    @Test
    fun `editing fills the form from the object and saves an update`() {
        objects.details.value =
            mapOf(ObjectId("o1") to anObjectDetails(id = "o1", title = "Кухня", address = "Тверская, 5"))
        val vm = viewModel(objectId = "o1")
        assertThat(vm.state.value.isNew).isFalse()
        assertThat(vm.state.value.form.title).isEqualTo("Кухня")

        vm.type(ObjectField.NOTES, "сдать в пятницу")
        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(objects.updated.single().first).isEqualTo(ObjectId("o1"))
        assertThat(objects.updated.single().second.notes).isEqualTo("сдать в пятницу")
        assertThat(vm.state.value.saved).isEqualTo(SaveResult.Updated)
    }

    @Test
    fun `a draft survives the process and wins over the server's copy`() {
        objects.details.value = mapOf(ObjectId("o1") to anObjectDetails(id = "o1", address = "Тверская, 5"))
        viewModel(objectId = "o1").type(ObjectField.ADDRESS, "Тверская, 7")

        // A new ViewModel over the same SavedStateHandle: what the system restores after process death.
        val restored = viewModel(objectId = "o1")

        assertThat(restored.state.value.form.address).isEqualTo("Тверская, 7")
    }

    @Test
    fun `a saved draft is forgotten`() {
        val vm = viewModel()
        vm.type(ObjectField.ADDRESS, "Тверская, 5")
        vm.onEvent(ObjectEditEvent.SaveClicked)

        assertThat(viewModel().state.value.form).isEqualTo(ObjectForm())
    }
}
