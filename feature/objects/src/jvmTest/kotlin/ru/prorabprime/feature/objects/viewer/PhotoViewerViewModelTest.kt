package ru.prorabprime.feature.objects.viewer

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ReceiptInfo
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RotatePhotoUseCase
import ru.prorabprime.domain.usecase.SetPhotoNoteUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aPhoto
import ru.prorabprime.testing.anObjectDetails

class PhotoViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository().apply {
        details.value = mapOf(
            ObjectId("o1") to
                anObjectDetails(
                    id = "o1",
                    photos = persistentListOf(aPhoto("p1", "o1"), aPhoto("p2", "o1"), aPhoto("p3", "o1")),
                ),
        )
    }

    @Test
    fun `opens at the tapped photo, showing full-size pictures`() {
        val viewModel = viewModel("p2")

        val state = viewModel.state.value
        assertThat(state.status).isEqualTo(PhotoViewerStatus.Content)
        assertThat(state.initialPage).isEqualTo(1)
        assertThat(state.photos.first().path).isEqualTo(ServerFilePath("/files/o1/p1.jpg"))
    }

    @Test
    fun `a photo that is gone opens at the first one`() {
        val viewModel = viewModel("missing")

        assertThat(viewModel.state.value.initialPage).isEqualTo(0)
    }

    @Test
    fun `rotating asks the repository to turn that photo`() {
        val viewModel = viewModel("p2")

        viewModel.rotate("p2")

        assertThat(photos.rotated).containsExactly(PhotoId("p2"))
    }

    @Test
    fun `a rotation that fails says so`() {
        photos.error = AppError.Network
        val viewModel = viewModel("p2")

        viewModel.rotate("p2")

        assertThat(notifier.errors).hasSize(1)
    }

    @Test
    fun `a photo waiting for a turn is shown with it`() {
        objects.details.value = mapOf(
            ObjectId("o1") to anObjectDetails(
                id = "o1",
                photos = persistentListOf(aPhoto("p1", "o1").copy(quarterTurns = 1)),
            ),
        )

        assertThat(viewModel("p1").state.value.photos.single().quarterTurns).isEqualTo(1)
    }

    @Test
    fun `saving a note passes it on, and a failure says so`() {
        val viewModel = viewModel("p2")

        viewModel.saveNote("p2", "Скол на плитке")
        assertThat(photos.notes).containsExactly(PhotoId("p2") to "Скол на плитке")

        photos.error = AppError.Network
        viewModel.saveNote("p2", "ещё")
        assertThat(notifier.errors).hasSize(1)
    }

    @Test
    fun `a receipt is shown with its sum and time`() {
        objects.details.value = mapOf(
            ObjectId("o1") to anObjectDetails(
                id = "o1",
                photos = persistentListOf(aPhoto("p1", "o1").copy(receipt = ReceiptInfo(79_000, "2026-10-01T15:26"))),
            ),
        )

        assertThat(viewModel("p1").state.value.photos.single().receiptLine).isEqualTo("790 ₽ · 01.10.2026 15:26")
    }

    @Test
    fun `a photo with a note is shown with it`() {
        objects.details.value = mapOf(
            ObjectId("o1") to anObjectDetails(
                id = "o1",
                photos = persistentListOf(aPhoto("p1", "o1").copy(note = "Трещина")),
            ),
        )

        assertThat(viewModel("p1").state.value.photos.single().note).isEqualTo("Трещина")
    }

    private val photos = FakePhotosRepository()
    private val notifier = FakeSnackbarNotifier()

    private fun viewModel(photoId: String) = PhotoViewerViewModel(
        PhotoViewerArgs("o1", photoId),
        ObserveObjectUseCase(objects),
        RotatePhotoUseCase(photos),
        SetPhotoNoteUseCase(photos),
        notifier,
    )
}
