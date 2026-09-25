package ru.prorabprime.feature.objects.details

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.PhotoRejection
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SetCoverPhotoUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_cover_set
import ru.prorabprime.feature.objects.resources.objectdetails_deleted
import ru.prorabprime.feature.objects.resources.objectdetails_error_gone
import ru.prorabprime.feature.objects.resources.objectdetails_upload_unreadable
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aPhoto
import ru.prorabprime.testing.anObjectDetails
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

class ObjectDetailsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository()
    private val photos = FakePhotosRepository()
    private val compressor = FakeImageCompressor()
    private val notifier = FakeSnackbarNotifier()
    private val id = ObjectId("o1")

    private val viewModel by lazy {
        val holder = ObjectDetailsStateHolder()
        ObjectDetailsViewModel(
            objectId = id,
            stateHolder = holder,
            errorHandler = ObjectDetailsErrorHandler(holder, notifier),
            actions = ObjectDetailsActions(
                observeObject = ObserveObjectUseCase(objects),
                refreshObjects = RefreshObjectsUseCase(objects),
                deleteObject = DeleteObjectUseCase(objects),
                uploadPhoto = UploadPhotoUseCase(compressor, photos),
                deletePhoto = DeletePhotoUseCase(photos),
                setCoverPhoto = SetCoverPhotoUseCase(photos),
            ),
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
    fun `photos show in carousel order with the cover marked`() {
        objects.details.value = mapOf(
            id to anObjectDetails(
                id = "o1",
                coverPhotoId = "p2",
                photos = persistentListOf(aPhoto(id = "p1"), aPhoto(id = "p2")),
            ),
        )

        assertThat(
            state.details?.photos?.map {
                it.id to it.isCover
            },
        ).containsExactly("p1" to false, "p2" to true).inOrder()
    }

    @Test
    fun `picked photos are uploaded and leave the carousel once done`() {
        withObject()

        viewModel.onEvent(
            ObjectDetailsEvent.PhotosPicked(listOf(LocalImageRef("content://a"), LocalImageRef("content://b"))),
        )

        assertThat(photos.uploaded.map { it.first }).containsExactly(id, id)
        assertThat(state.uploads).isEmpty()
    }

    @Test
    fun `a failed upload stays with its reason, and retrying sends it again`() {
        withObject()
        photos.error = AppError.PhotoRejected(PhotoRejection.TOO_LARGE)

        viewModel.onEvent(ObjectDetailsEvent.PhotosPicked(listOf(LocalImageRef("content://a"))))
        assertThat(
            state.uploads.single().failure,
        ).isEqualTo(AppError.PhotoRejected(PhotoRejection.TOO_LARGE).toUiText())

        photos.error = null
        viewModel.onEvent(ObjectDetailsEvent.RetryUpload(LocalImageRef("content://a")))
        assertThat(state.uploads).isEmpty()
        assertThat(photos.uploaded).hasSize(1)
    }

    @Test
    fun `a picture the device cannot read says so`() {
        withObject()
        compressor.error = AppError.Unknown

        viewModel.onEvent(ObjectDetailsEvent.PhotosPicked(listOf(LocalImageRef("content://gone"))))

        assertThat(
            state.uploads.single().failure,
        ).isEqualTo(UiText.Resource(Res.string.objectdetails_upload_unreadable))
    }

    @Test
    fun `a failed upload can be dismissed`() {
        withObject()
        photos.error = AppError.Network
        viewModel.onEvent(ObjectDetailsEvent.PhotosPicked(listOf(LocalImageRef("content://a"))))

        viewModel.onEvent(ObjectDetailsEvent.DismissUpload(LocalImageRef("content://a")))

        assertThat(state.uploads).isEmpty()
    }

    @Test
    fun `a photo can be made the cover`() {
        withObject()

        viewModel.onEvent(ObjectDetailsEvent.MakeCoverClicked("p2"))

        assertThat(photos.covers).containsExactly(id to PhotoId("p2"))
        assertThat(notifier.shown).containsExactly(UiText.Resource(Res.string.objectdetails_cover_set))
    }

    @Test
    fun `deleting a photo asks first`() {
        withObject()

        viewModel.onEvent(ObjectDetailsEvent.DeletePhotoClicked("p1"))
        assertThat(photos.deleted).isEmpty()
        assertThat(state.pendingAction).isEqualTo(ObjectDetailsAction.DeletePhoto("p1"))

        viewModel.onEvent(ObjectDetailsEvent.DialogConfirmed)
        assertThat(photos.deleted).containsExactly(PhotoId("p1"))
    }

    @Test
    fun `retry reloads`() {
        objects.loadError.value = AppError.Network
        viewModel

        viewModel.onEvent(ObjectDetailsEvent.Retry)

        assertThat(objects.refreshCount).isEqualTo(1)
    }
}
