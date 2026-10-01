package ru.prorabprime.feature.objects.gallery

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ReceiptInfo
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aPhoto
import ru.prorabprime.testing.anObjectDetails
import ru.prorabprime.ui.toUiText

class GalleryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photos = FakePhotosRepository()
    private val notifier = FakeSnackbarNotifier()
    private val objects = FakeObjectsRepository().apply {
        details.value = mapOf(
            ObjectId("o1") to anObjectDetails(
                id = "o1",
                coverPhotoId = "p2",
                photos = persistentListOf(
                    aPhoto("p1", "o1"),
                    aPhoto("p2", "o1"),
                    aPhoto("p3", "o1"),
                    aPhoto("r1", "o1").copy(kind = AttachmentKind.RECEIPT, receipt = ReceiptInfo(79_000)),
                ),
            ),
        )
    }

    private fun viewModel(receipts: Boolean = false) = GalleryViewModel(
        GalleryArgs("o1", receipts),
        ObserveObjectUseCase(objects),
        DeletePhotoUseCase(photos),
        notifier,
    )

    @Test
    fun `the grid holds the photos of the object, or its receipts with their sums`() {
        val photosState = viewModel().state.value
        val receiptsState = viewModel(receipts = true).state.value

        assertThat(photosState.status).isEqualTo(GalleryStatus.Content)
        assertThat(photosState.photos.map { it.id }).containsExactly("p1", "p2", "p3").inOrder()
        assertThat(photosState.photos.single { it.isCover }.id).isEqualTo("p2")
        assertThat(receiptsState.photos.map { it.id }).containsExactly("r1")
        assertThat(receiptsState.photos.single().amount).isNotNull()
    }

    @Test
    fun `a long press starts selecting, taps pick and unpick, and the cross leaves it`() {
        val viewModel = viewModel()

        viewModel.onEvent(GalleryEvent.PhotoToggled("p1"))
        viewModel.onEvent(GalleryEvent.PhotoToggled("p3"))
        assertThat(viewModel.state.value.selecting).isTrue()
        assertThat(viewModel.state.value.selected).containsExactly("p1", "p3")

        viewModel.onEvent(GalleryEvent.PhotoToggled("p1"))
        assertThat(viewModel.state.value.selected).containsExactly("p3")

        viewModel.onEvent(GalleryEvent.SelectingToggled)
        assertThat(viewModel.state.value.selecting).isFalse()
        assertThat(viewModel.state.value.selected).isEmpty()
    }

    @Test
    fun `select all picks every photo, and a second time none`() {
        val viewModel = viewModel()
        viewModel.onEvent(GalleryEvent.SelectingToggled)

        viewModel.onEvent(GalleryEvent.SelectAllToggled)
        assertThat(viewModel.state.value.selected).containsExactly("p1", "p2", "p3")

        viewModel.onEvent(GalleryEvent.SelectAllToggled)
        assertThat(viewModel.state.value.selected).isEmpty()
    }

    @Test
    fun `deleting asks first, and deletes the picked photos once confirmed`() {
        val viewModel = viewModel()
        viewModel.onEvent(GalleryEvent.PhotoToggled("p1"))
        viewModel.onEvent(GalleryEvent.PhotoToggled("p3"))

        viewModel.onEvent(GalleryEvent.DeleteClicked)
        assertThat(viewModel.state.value.dialog).isNotNull()
        assertThat(photos.deleted).isEmpty()
        viewModel.onEvent(GalleryEvent.DialogDismissed)
        assertThat(viewModel.state.value.dialog).isNull()

        viewModel.onEvent(GalleryEvent.DeleteClicked)
        viewModel.onEvent(GalleryEvent.DialogConfirmed)

        assertThat(photos.deleted).containsExactly(PhotoId("p1"), PhotoId("p3"))
        assertThat(viewModel.state.value.selecting).isFalse()
        assertThat(viewModel.state.value.selected).isEmpty()
    }

    @Test
    fun `nothing picked asks nothing`() {
        val viewModel = viewModel()
        viewModel.onEvent(GalleryEvent.SelectingToggled)

        viewModel.onEvent(GalleryEvent.DeleteClicked)

        assertThat(viewModel.state.value.dialog).isNull()
    }

    @Test
    fun `a failed delete says so and leaves the rest picked`() {
        val viewModel = viewModel()
        viewModel.onEvent(GalleryEvent.PhotoToggled("p1"))
        viewModel.onEvent(GalleryEvent.PhotoToggled("p3"))
        photos.error = AppError.Network

        viewModel.onEvent(GalleryEvent.DeleteClicked)
        viewModel.onEvent(GalleryEvent.DialogConfirmed)

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
        assertThat(viewModel.state.value.selecting).isTrue()
        assertThat(viewModel.state.value.selected).containsExactly("p1", "p3")
    }

    @Test
    fun `a failure to read the object is an error`() {
        objects.loadError.value = AppError.Network

        assertThat(viewModel().state.value.status).isEqualTo(GalleryStatus.Error(AppError.Network.toUiText()))
    }
}
