package ru.prorabprime.feature.objects.details

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_deleted
import ru.prorabprime.feature.objects.resources.objectdetails_error_gone
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectDetails
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

class ObjectDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository()
    private val notifier = FakeSnackbarNotifier()
    private val id = ObjectId("o1")

    private val viewModel by lazy {
        val holder = ObjectDetailsStateHolder()
        ObjectDetailsViewModel(
            objectId = id,
            stateHolder = holder,
            errorHandler = ObjectDetailsErrorHandler(holder, notifier),
            observeObject = ObserveObjectUseCase(objects),
            refreshObjects = RefreshObjectsUseCase(objects),
            deleteObject = DeleteObjectUseCase(objects),
            notifier = notifier,
        )
    }

    private val state get() = viewModel.state.value

    private fun withObject() {
        objects.details.value = mapOf(id to anObjectDetails(id = "o1", title = "Кухня", address = "Тверская, 5"))
    }

    @Test
    fun `the object is shown`() {
        withObject()

        assertThat(state.status).isEqualTo(ObjectDetailsStatus.Content)
        assertThat(state.details?.title).isEqualTo("Кухня")
        assertThat(state.details?.address).isEqualTo("Тверская, 5")
    }

    @Test
    fun `an edit elsewhere shows up here`() {
        withObject()
        viewModel

        objects.details.value = mapOf(id to anObjectDetails(id = "o1", title = "Ванная"))

        assertThat(state.details?.title).isEqualTo("Ванная")
    }

    @Test
    fun `delete asks first, and dismissing deletes nothing`() {
        withObject()

        viewModel.onEvent(ObjectDetailsEvent.DeleteClicked)
        assertThat(state.dialog).isInstanceOf(DialogModel.Confirmation::class.java)
        assertThat(state.pendingAction).isEqualTo(ObjectDetailsAction.DeleteObject)

        viewModel.onEvent(ObjectDetailsEvent.DialogDismissed)
        assertThat(state.dialog).isNull()
        assertThat(state.pendingAction).isNull()
        assertThat(objects.deleted).isEmpty()
    }

    @Test
    fun `confirming deletes, says so and closes the screen`() {
        withObject()

        viewModel.onEvent(ObjectDetailsEvent.DeleteClicked)
        viewModel.onEvent(ObjectDetailsEvent.DialogConfirmed)

        assertThat(objects.deleted).containsExactly(id)
        assertThat(state.isClosed).isTrue()
        assertThat(notifier.shown).containsExactly(UiText.Resource(Res.string.objectdetails_deleted))
    }

    @Test
    fun `a failed delete keeps the object and says why`() {
        withObject()
        objects.writeError = AppError.Network

        viewModel.onEvent(ObjectDetailsEvent.DeleteClicked)
        viewModel.onEvent(ObjectDetailsEvent.DialogConfirmed)

        assertThat(state.isClosed).isFalse()
        assertThat(state.isDeleting).isFalse()
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }

    @Test
    fun `an object that is gone says so`() {
        assertThat(
            state.status,
        ).isEqualTo(ObjectDetailsStatus.Error(UiText.Resource(Res.string.objectdetails_error_gone)))
    }

    @Test
    fun `retry reloads`() {
        objects.loadError.value = AppError.Network
        viewModel

        viewModel.onEvent(ObjectDetailsEvent.Retry)

        assertThat(objects.refreshCount).isEqualTo(1)
    }
}
