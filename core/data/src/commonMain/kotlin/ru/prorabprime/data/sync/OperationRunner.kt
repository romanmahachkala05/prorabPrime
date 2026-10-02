package ru.prorabprime.data.sync

import ru.prorabprime.data.local.BlobStore
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.model.asFailure

/** Sends one queued change to the server, exactly as it was made. */
internal class OperationRunner(
    private val remote: RemoteApi,
    private val blobs: BlobStore,
) {
    @Suppress("CyclomaticComplexMethod") // One branch per kind of change, each a single call.
    suspend fun run(operation: Operation): Result<Unit> = when (operation) {
        is Operation.CreateObject -> remote.createObject(operation.request)

        is Operation.UpdateObject -> remote.updateObject(operation.id, operation.request)

        is Operation.DeleteObject -> remote.deleteObject(operation.id).goneIsDone()

        is Operation.SetCover -> remote.setCover(operation.objectId, operation.photoId)

        is Operation.UploadPhoto -> upload(operation)

        is Operation.SetReceipt ->
            remote.setReceipt(operation.photoId, operation.amountKopecks, operation.purchasedAt).goneIsDone()

        is Operation.SetPhotoNote -> remote.setPhotoNote(operation.photoId, operation.note).goneIsDone()

        is Operation.RotatePhoto ->
            remote.rotatePhoto(operation.photoId, operation.quarterTurns, operation.rotationId).goneIsDone()

        is Operation.DeletePhoto -> remote.deletePhoto(operation.id).goneIsDone()

        is Operation.CreateContact -> remote.createContact(operation.objectId, operation.request)

        is Operation.UpdateContact -> remote.updateContact(operation.id, operation.request)

        is Operation.DeleteContact -> remote.deleteContact(operation.id).goneIsDone()

        is Operation.SetTerms -> remote.setTerms(operation.objectId, operation.terms)

        is Operation.CreatePayment -> remote.createPayment(operation.objectId, operation.request)

        is Operation.UpdatePayment -> remote.updatePayment(operation.id, operation.request)

        is Operation.DeletePayment -> remote.deletePayment(operation.id).goneIsDone()

        is Operation.CreateExtraWork -> remote.createExtraWork(operation.objectId, operation.request)

        is Operation.UpdateExtraWork -> remote.updateExtraWork(operation.id, operation.request)

        is Operation.DeleteExtraWork -> remote.deleteExtraWork(operation.id).goneIsDone()

        is Operation.CreateMaterial -> remote.createMaterial(operation.objectId, operation.request)

        is Operation.UpdateMaterial -> remote.updateMaterial(operation.id, operation.request)

        is Operation.DeleteMaterial -> remote.deleteMaterial(operation.id).goneIsDone()

        is Operation.AddDefaultMaterials -> remote.addDefaultMaterials(operation.objectId)

        is Operation.CreateTask -> remote.createTask(operation.request)

        is Operation.UpdateTask -> remote.updateTask(operation.id, operation.request)

        is Operation.DeleteTask -> remote.deleteTask(operation.id).goneIsDone()
    }

    private suspend fun upload(operation: Operation.UploadPhoto): Result<Unit> {
        val bytes = blobs.get(operation.blob) ?: return AppError.NotFound.asFailure()
        return remote.uploadPhoto(
            operation.objectId,
            operation.photoId,
            operation.kind,
            bytes,
            operation.mimeType,
            operation.note,
        )
            .onSuccess { blobs.delete(operation.blob) }
    }

    /** Deleting what is already gone is what was wanted. */
    private fun Result<Unit>.goneIsDone(): Result<Unit> =
        if (exceptionOrNull()?.asAppError() == AppError.NotFound) Result.success(Unit) else this
}
