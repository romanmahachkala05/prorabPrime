package ru.prorabprime.feature.objects.list

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectSummary
import ru.prorabprime.ui.toUiText

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectsListViewModelTest {

    // Standard, not unconfined: the debounce is measured in virtual time.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val objects = FakeObjectsRepository()
    private val settings = FakeSettingsRepository()
    private val notifier = FakeSnackbarNotifier()
    private val photos = FakePhotosRepository()
    private val compressor = FakeImageCompressor()

    private val viewModel by lazy {
        val holder = ObjectsListStateHolder()
        ObjectsListViewModel(
            stateHolder = holder,
            errorHandler = ObjectsListErrorHandler(holder, notifier),
            observeObjects = ObserveObjectsUseCase(objects),
            refreshObjects = RefreshObjectsUseCase(objects),
            observeObjectSort = ObserveObjectSortUseCase(settings),
            saveObjectSort = SaveObjectSortUseCase(settings),
            uploadPhoto = UploadPhotoUseCase(compressor, photos),
            notifier = notifier,
        )
    }

    private val state get() = viewModel.state.value

    private fun runVmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel
            block()
        }

    @Test
    fun `the objects are shown after the initial debounce`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a"), anObjectSummary(id = "b"))

        advanceTimeBy(301)
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Content)
        assertThat(state.items.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `typing queries the server once, after the pause`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.SearchChanged("л"))
        advanceTimeBy(100)
        viewModel.onEvent(ObjectsListEvent.SearchChanged("ле"))
        advanceTimeBy(100)
        viewModel.onEvent(ObjectsListEvent.SearchChanged("лен "))
        advanceTimeBy(301)
        runCurrent()

        assertThat(
            objects.queries,
        ).containsExactly(ObjectQuery("", ObjectSort.DEFAULT), ObjectQuery("лен", ObjectSort.DEFAULT))
            .inOrder()
    }

    @Test
    fun `a chosen sort is saved and queried`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.SortSelected(ObjectSort.ADDRESS_ASC))
        runCurrent()

        assertThat(settings.objectSort.value).isEqualTo(ObjectSort.ADDRESS_ASC)
        assertThat(state.sort).isEqualTo(ObjectSort.ADDRESS_ASC)
        assertThat(objects.queries.last()).isEqualTo(ObjectQuery("", ObjectSort.ADDRESS_ASC))
    }

    @Test
    fun `a failed first load takes over the screen`() = runVmTest {
        objects.loadError.value = AppError.Network

        advanceTimeBy(301)
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Error(AppError.Network.toUiText()))
    }

    @Test
    fun `a failed reload keeps the list and says so`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary())
        advanceTimeBy(301)
        runCurrent()

        objects.loadError.value = AppError.Network
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Content)
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }

    @Test
    fun `pulling to refresh reloads and ends refreshing when the list arrives`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.Refresh)
        assertThat(state.isRefreshing).isTrue()
        runCurrent()
        objects.objects.value = listOf(anObjectSummary())
        runCurrent()

        assertThat(objects.refreshCount).isEqualTo(1)
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun `retry shows loading and reloads`() = runVmTest {
        objects.loadError.value = AppError.Network
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.Retry)

        assertThat(state.status).isEqualTo(ObjectsListStatus.Loading)
        runCurrent()
        assertThat(objects.refreshCount).isEqualTo(1)
    }

    @Test
    fun `a captured picture waits for a target and is uploaded to the chosen object as a photo`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a", title = "Кухня"), anObjectSummary(id = "b"))
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(
            ObjectsListEvent.PhotosCaptured(listOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.PHOTO),
        )
        assertThat(state.capture?.kind).isEqualTo(AttachmentKind.PHOTO)
        assertThat(photos.uploaded).isEmpty()

        viewModel.onEvent(ObjectsListEvent.CaptureTargetChosen("a"))
        runCurrent()

        assertThat(state.capture).isNull()
        assertThat(photos.uploaded.map { it.first }).containsExactly(ObjectId("a"))
        assertThat(photos.uploadedKinds).containsExactly(AttachmentKind.PHOTO)
        assertThat(notifier.shown).hasSize(1)
    }

    @Test
    fun `a note written before choosing goes with the picture`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a"))
        advanceTimeBy(301)
        runCurrent()
        viewModel.onEvent(
            ObjectsListEvent.PhotosCaptured(listOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.PHOTO),
        )

        viewModel.onEvent(ObjectsListEvent.CaptureNoteChanged("Розетка не по плану"))
        assertThat(state.capture?.note).isEqualTo("Розетка не по плану")
        viewModel.onEvent(ObjectsListEvent.CaptureTargetChosen("a"))
        runCurrent()

        assertThat(photos.uploadedNotes).containsExactly("Розетка не по плану")
    }

    @Test
    fun `a picture from the receipt button goes to the receipts`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a"))
        advanceTimeBy(301)
        runCurrent()
        viewModel.onEvent(
            ObjectsListEvent.PhotosCaptured(listOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.RECEIPT),
        )
        viewModel.onEvent(ObjectsListEvent.CaptureTargetChosen("a"))
        runCurrent()

        assertThat(photos.uploadedKinds).containsExactly(AttachmentKind.RECEIPT)
    }

    @Test
    fun `dismissing the sheet drops the picture, and a failed upload says so`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a"))
        advanceTimeBy(301)
        runCurrent()
        viewModel.onEvent(
            ObjectsListEvent.PhotosCaptured(listOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.PHOTO),
        )
        viewModel.onEvent(ObjectsListEvent.CaptureDismissed)
        assertThat(state.capture).isNull()

        photos.error = AppError.Network
        viewModel.onEvent(
            ObjectsListEvent.PhotosCaptured(listOf(LocalImageRef("file:///shot.jpg")), AttachmentKind.PHOTO),
        )
        viewModel.onEvent(ObjectsListEvent.CaptureTargetChosen("a"))
        runCurrent()

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }
}
