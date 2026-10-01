package ru.prorabprime.feature.objects.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RotatePhotoUseCase
import ru.prorabprime.domain.usecase.SetPhotoNoteUseCase
import ru.prorabprime.domain.usecase.SetReceiptUseCase
import ru.prorabprime.feature.objects.photos.ReceiptInput
import ru.prorabprime.feature.objects.photos.amountInput
import ru.prorabprime.feature.objects.photos.dateInput
import ru.prorabprime.feature.objects.photos.line
import ru.prorabprime.feature.objects.photos.readReceiptInput
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.photoviewer_receipt_bad_amount
import ru.prorabprime.feature.objects.resources.photoviewer_receipt_bad_date
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

/**
 * Small enough to own its state directly: two transitions and no collaborators to share them
 * with, so a separate StateHolder would only forward calls.
 */
internal class PhotoViewerViewModel(
    args: PhotoViewerArgs,
    observeObject: ObserveObjectUseCase,
    private val rotatePhoto: RotatePhotoUseCase,
    private val setPhotoNote: SetPhotoNoteUseCase,
    private val setReceipt: SetReceiptUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<PhotoViewerState> {

    private val _state = MutableStateFlow(PhotoViewerState())
    override val state: StateFlow<PhotoViewerState> = _state.asStateFlow()

    init {
        observeObject(ObjectId(args.objectId))
            .onEach { result ->
                result
                    .onSuccess { details ->
                        // Photos and receipts are separate folders: page through the tapped one's.
                        val kind = details.photos.find { it.id.value == args.photoId }?.kind
                        val folder = details.photos.filter { kind == null || it.kind == kind }
                        val index = folder.indexOfFirst { it.id.value == args.photoId }.coerceAtLeast(0)
                        _state.update {
                            PhotoViewerState(
                                PhotoViewerStatus.Content,
                                folder.map {
                                    it.toViewer()
                                }.toImmutableList(),
                                index,
                            )
                        }
                    }.onFailure { failure ->
                        _state.update { it.copy(status = PhotoViewerStatus.Error(failure.asAppError().toUiText())) }
                    }
            }.launchIn(viewModelScope)
    }

    /** An empty sum clears the receipt; a sum or day that cannot be read is said so and nothing is saved. */
    fun saveReceipt(
        photoId: String,
        amountText: String,
        dateText: String,
    ) {
        val was = state.value.photos.find { it.id == photoId }?.receipt
        val receipt = when (val input = readReceiptInput(amountText, dateText, was)) {
            ReceiptInput.Clear -> null
            is ReceiptInput.Valid -> input.receipt
            ReceiptInput.BadAmount -> return reject(Res.string.photoviewer_receipt_bad_amount)
            ReceiptInput.BadDate -> return reject(Res.string.photoviewer_receipt_bad_date)
        }
        viewModelScope.launch {
            setReceipt(PhotoId(photoId), receipt).onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }

    private fun reject(message: StringResource) {
        viewModelScope.launch { notifier.showError(UiText.Resource(message)) }
    }

    fun saveNote(photoId: String, note: String) {
        viewModelScope.launch {
            setPhotoNote(PhotoId(photoId), note).onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }

    fun rotate(photoId: String) {
        viewModelScope.launch {
            rotatePhoto(PhotoId(photoId)).onFailure { notifier.showError(it.asAppError().toUiText()) }
        }
    }
}

private fun Photo.toViewer() = ViewerPhoto(
    id = id.value,
    path = path,
    quarterTurns = quarterTurns,
    note = note,
    receiptLine = receipt?.line(),
    isReceipt = kind == AttachmentKind.RECEIPT,
    amountInput = receipt?.amountInput().orEmpty(),
    dateInput = receipt?.dateInput().orEmpty(),
    receipt = receipt,
)
