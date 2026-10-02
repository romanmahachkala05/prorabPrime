package ru.prorabprime.feature.trash

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.usecase.LoadTrashUseCase
import ru.prorabprime.domain.usecase.PurgeFromTrashUseCase
import ru.prorabprime.domain.usecase.RestoreFromTrashUseCase
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.FakeTrashRepository
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aDeletedObject
import ru.prorabprime.testing.aDeletedPhoto
import ru.prorabprime.ui.toUiText

class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeTrashRepository().apply {
        objects += aDeletedObject("o1", title = "Кухня", daysLeft = 29)
        objects += aDeletedObject("o2", title = null)
        photos += aDeletedPhoto("p1", "o3", AttachmentKind.RECEIPT, daysLeft = 5)
    }
    private val notifier = FakeSnackbarNotifier()

    private fun viewModel() = TrashViewModel(
        LoadTrashUseCase(repository),
        RestoreFromTrashUseCase(repository),
        PurgeFromTrashUseCase(repository),
        notifier,
    )

    @Test
    fun `the trash is read and shown, an object without a title by its address`() {
        val state = viewModel().state.value

        assertThat(state.status).isEqualTo(TrashStatus.Content)
        assertThat(state.objects.map { it.title }).containsExactly("Кухня", "ул. Ленина, 1").inOrder()
        assertThat(state.objects.first().address).isEqualTo("ул. Ленина, 1")
        assertThat(state.objects.last().address).isNull()
        assertThat(state.objects.first().daysLeft).isEqualTo(29)
        assertThat(state.photos.single().daysLeft).isEqualTo(5)
        assertThat(state.photos.single().kind).isEqualTo(AttachmentKind.RECEIPT)
        assertThat(state.isEmpty).isFalse()
    }

    @Test
    fun `a failed first read is an error, and retrying reads again`() {
        repository.error = AppError.Network
        val viewModel = viewModel()
        assertThat(viewModel.state.value.status).isEqualTo(TrashStatus.Error(AppError.Network.toUiText()))

        repository.error = null
        viewModel.onEvent(TrashEvent.Retry)

        assertThat(viewModel.state.value.status).isEqualTo(TrashStatus.Content)
    }

    @Test
    fun `restoring takes the item out of the trash, says so, and reads the trash again`() {
        val viewModel = viewModel()

        viewModel.onEvent(TrashEvent.RestoreObject("o1"))
        viewModel.onEvent(TrashEvent.RestorePhoto("p1"))

        assertThat(repository.restoredObjects).containsExactly(ObjectId("o1"))
        assertThat(repository.restoredPhotos).containsExactly(PhotoId("p1"))
        assertThat(viewModel.state.value.objects.map { it.id }).containsExactly("o2")
        assertThat(viewModel.state.value.photos).isEmpty()
        assertThat(notifier.shown).hasSize(2)
        assertThat(viewModel.state.value.isBusy).isFalse()
    }

    @Test
    fun `removing for good asks first, and does it once confirmed`() {
        val viewModel = viewModel()

        viewModel.onEvent(TrashEvent.PurgeObjectClicked("o1"))
        assertThat(viewModel.state.value.dialog).isNotNull()
        assertThat(repository.purgedObjects).isEmpty()
        viewModel.onEvent(TrashEvent.DialogDismissed)
        assertThat(viewModel.state.value.dialog).isNull()
        viewModel.onEvent(TrashEvent.DialogConfirmed)
        assertThat(repository.purgedObjects).isEmpty()

        viewModel.onEvent(TrashEvent.PurgeObjectClicked("o1"))
        viewModel.onEvent(TrashEvent.DialogConfirmed)
        viewModel.onEvent(TrashEvent.PurgePhotoClicked("p1"))
        viewModel.onEvent(TrashEvent.DialogConfirmed)

        assertThat(repository.purgedObjects).containsExactly(ObjectId("o1"))
        assertThat(repository.purgedPhotos).containsExactly(PhotoId("p1"))
        assertThat(viewModel.state.value.dialog).isNull()
        assertThat(viewModel.state.value.pendingAction).isNull()
    }

    @Test
    fun `emptying asks first and then clears everything`() {
        val viewModel = viewModel()

        viewModel.onEvent(TrashEvent.EmptyClicked)
        assertThat(repository.emptied).isEqualTo(0)
        viewModel.onEvent(TrashEvent.DialogConfirmed)

        assertThat(repository.emptied).isEqualTo(1)
        assertThat(viewModel.state.value.isEmpty).isTrue()
        assertThat(viewModel.state.value.status).isEqualTo(TrashStatus.Content)
    }

    @Test
    fun `a failed change says so and keeps what is on screen`() {
        val viewModel = viewModel()
        repository.error = AppError.Network

        viewModel.onEvent(TrashEvent.RestoreObject("o1"))

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
        assertThat(viewModel.state.value.objects).hasSize(2)
        assertThat(viewModel.state.value.status).isEqualTo(TrashStatus.Content)
        assertThat(viewModel.state.value.isBusy).isFalse()
    }
}
