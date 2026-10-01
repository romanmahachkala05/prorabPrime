package ru.prorabprime.data.local

import ru.prorabprime.domain.model.ChangeAction
import ru.prorabprime.domain.model.ChangeKind
import ru.prorabprime.domain.model.FailedChange

/** What a queued change is, for a person to read: its kind, what it does, and the thing's name if it has one. */
@Suppress("CyclomaticComplexMethod") // One line per kind of change.
internal fun QueuedOperation.describe(): FailedChange {
    val (kind, action, title) = when (val op = operation) {
        is Operation.CreateObject -> Triple(
            ChangeKind.OBJECT,
            ChangeAction.CREATE,
            op.request.title ?: op.request.address,
        )

        is Operation.UpdateObject -> Triple(
            ChangeKind.OBJECT,
            ChangeAction.UPDATE,
            op.request.title ?: op.request.address,
        )

        is Operation.DeleteObject -> Triple(ChangeKind.OBJECT, ChangeAction.DELETE, null)

        is Operation.SetCover -> Triple(ChangeKind.COVER, ChangeAction.UPDATE, null)

        is Operation.UploadPhoto -> Triple(ChangeKind.PHOTO, ChangeAction.CREATE, null)

        is Operation.DeletePhoto -> Triple(ChangeKind.PHOTO, ChangeAction.DELETE, null)

        is Operation.CreateContact -> Triple(ChangeKind.CONTACT, ChangeAction.CREATE, op.request.name)

        is Operation.UpdateContact -> Triple(ChangeKind.CONTACT, ChangeAction.UPDATE, op.request.name)

        is Operation.DeleteContact -> Triple(ChangeKind.CONTACT, ChangeAction.DELETE, null)

        is Operation.SetTerms -> Triple(ChangeKind.TERMS, ChangeAction.UPDATE, null)

        is Operation.CreatePayment -> Triple(ChangeKind.PAYMENT, ChangeAction.CREATE, null)

        is Operation.UpdatePayment -> Triple(ChangeKind.PAYMENT, ChangeAction.UPDATE, null)

        is Operation.DeletePayment -> Triple(ChangeKind.PAYMENT, ChangeAction.DELETE, null)

        is Operation.CreateExtraWork -> Triple(ChangeKind.EXTRA_WORK, ChangeAction.CREATE, op.request.title)

        is Operation.UpdateExtraWork -> Triple(ChangeKind.EXTRA_WORK, ChangeAction.UPDATE, op.request.title)

        is Operation.DeleteExtraWork -> Triple(ChangeKind.EXTRA_WORK, ChangeAction.DELETE, null)

        is Operation.CreateMaterial -> Triple(ChangeKind.MATERIAL, ChangeAction.CREATE, op.request.title)

        is Operation.UpdateMaterial -> Triple(ChangeKind.MATERIAL, ChangeAction.UPDATE, op.request.title)

        is Operation.DeleteMaterial -> Triple(ChangeKind.MATERIAL, ChangeAction.DELETE, null)

        is Operation.AddDefaultMaterials -> Triple(ChangeKind.MATERIAL, ChangeAction.CREATE, null)

        is Operation.CreateTask -> Triple(ChangeKind.TASK, ChangeAction.CREATE, op.request.title)

        is Operation.UpdateTask -> Triple(ChangeKind.TASK, ChangeAction.UPDATE, op.request.title)

        is Operation.DeleteTask -> Triple(ChangeKind.TASK, ChangeAction.DELETE, null)
    }
    return FailedChange(seq, kind, action, title, reason.orEmpty())
}
