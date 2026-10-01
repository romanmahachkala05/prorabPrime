package ru.prorabprime.feature.objects.details

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ContactRole
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface ObjectDetailsStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : ObjectDetailsStatus

    data object Loading : ObjectDetailsStatus

    data class Error(
        val message: UiText,
    ) : ObjectDetailsStatus
}

@Immutable
internal data class ObjectDetailsUi(
    val title: String,
    /** Shown separately only when the title is something other than the address. */
    val address: String?,
    val status: ObjectStatus,
    val clientName: String?,
    val clientPhone: String?,
    val notes: String?,
    val chatLink: String? = null,
    val contacts: ImmutableList<ContactUi> = persistentListOf(),
    val photos: ImmutableList<PhotoUi> = persistentListOf(),
    val receipts: ImmutableList<PhotoUi> = persistentListOf(),
)

@Immutable
internal data class ContactUi(
    val id: String,
    val name: String,
    val phone: String?,
    val role: ContactRole,
    val isPending: Boolean = false,
)

/** The contact form, open over the card: a new contact when [contactId] is null. */
@Immutable
internal data class ContactEditorUi(
    val contactId: String? = null,
    val name: String = "",
    val phone: String = "",
    val role: ContactRole = ContactRole.OTHER,
    val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
    val isSaving: Boolean = false,
)

@Immutable
internal data class PhotoUi(
    val id: String,
    val thumb: ServerFilePath,
    val isCover: Boolean,
    val isPending: Boolean = false,
)

/** A picture on its way to the server, shown in the carousel until it arrives as a [PhotoUi]. */
@Immutable
internal data class UploadUi(
    val image: LocalImageRef,
    val kind: AttachmentKind = AttachmentKind.PHOTO,
    val failure: UiText? = null,
) {
    val isFailed: Boolean get() = failure != null
}

/** An operation waiting for the user to confirm it in [ObjectDetailsState.dialog]. */
internal sealed interface ObjectDetailsAction {
    data object DeleteObject : ObjectDetailsAction

    data class DeletePhoto(
        val photoId: String,
    ) : ObjectDetailsAction

    data class DeleteContact(
        val contactId: String,
    ) : ObjectDetailsAction
}

@Immutable
internal data class ObjectDetailsState(
    val status: ObjectDetailsStatus = ObjectDetailsStatus.Loading,
    val details: ObjectDetailsUi? = null,
    val uploads: ImmutableList<UploadUi> = persistentListOf(),
    val dialog: DialogModel? = null,
    val contactEditor: ContactEditorUi? = null,
    val pendingAction: ObjectDetailsAction? = null,
    val isDeleting: Boolean = false,
    /** Set once the object is gone; the screen then leaves. */
    val isClosed: Boolean = false,
)

/** Everything the contact form does; [ContactEditorController] handles these. */
internal sealed interface ContactEvent : ObjectDetailsEvent

internal sealed interface ObjectDetailsEvent {
    data object DeleteClicked : ObjectDetailsEvent

    data object DialogConfirmed : ObjectDetailsEvent

    data object DialogDismissed : ObjectDetailsEvent

    data object Retry : ObjectDetailsEvent

    /** Pictures taken with the camera or chosen in the gallery. */
    data class PhotosPicked(
        val images: List<LocalImageRef>,
        val kind: AttachmentKind = AttachmentKind.PHOTO,
    ) : ObjectDetailsEvent

    data class RetryUpload(
        val image: LocalImageRef,
        val kind: AttachmentKind = AttachmentKind.PHOTO,
    ) : ObjectDetailsEvent

    data class DismissUpload(
        val image: LocalImageRef,
    ) : ObjectDetailsEvent

    data class MakeCoverClicked(
        val photoId: String,
    ) : ObjectDetailsEvent

    data class DeletePhotoClicked(
        val photoId: String,
    ) : ObjectDetailsEvent

    data object AddContactClicked : ContactEvent

    data class EditContactClicked(
        val contactId: String,
    ) : ContactEvent

    data class DeleteContactClicked(
        val contactId: String,
    ) : ContactEvent

    data class ContactNameChanged(
        val value: String,
    ) : ContactEvent

    data class ContactPhoneChanged(
        val value: String,
    ) : ContactEvent

    data class ContactRoleChanged(
        val value: ContactRole,
    ) : ContactEvent

    data object ContactSaveClicked : ContactEvent

    data object ContactEditorDismissed : ContactEvent
}
