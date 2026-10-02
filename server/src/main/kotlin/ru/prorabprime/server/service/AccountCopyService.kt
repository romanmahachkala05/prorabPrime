package ru.prorabprime.server.service

import io.ktor.util.logging.KtorSimpleLogger
import java.util.UUID
import kotlin.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.model.UserRecord
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ExtraWorkRepository
import ru.prorabprime.server.repository.FinanceTermsRepository
import ru.prorabprime.server.repository.MaterialRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PaymentRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.repository.TaskRepository
import ru.prorabprime.server.repository.UserRepository
import ru.prorabprime.server.storage.FileStorage

/** What a copy made. */
data class CopyReport(
    val objects: Int,
    val photos: Int,
    val tasks: Int,
)

/**
 * Makes a new account with a copy of what another account has: objects and everything on them, and the tasks.
 * What is in the trash is left behind. Every record and every file gets a new id, so the two accounts share
 * nothing and a change to one never reaches the other. The new account is made and filled in one transaction,
 * and the files written for it are removed again if anything fails, so a copy is all there or not there.
 * The new account has no token yet: [AccountService.issueToken] gives it one.
 */
@Suppress("LongParameterList") // One repository per kind of record there is to copy.
class AccountCopyService(
    private val users: UserRepository,
    private val objects: ObjectRepository,
    private val photos: PhotoRepository,
    private val contacts: ContactRepository,
    private val terms: FinanceTermsRepository,
    private val payments: PaymentRepository,
    private val extras: ExtraWorkRepository,
    private val materials: MaterialRepository,
    private val tasks: TaskRepository,
    private val storage: FileStorage,
    private val transactor: Transactor,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun copy(from: String, to: String): Result<CopyReport> {
        val name = validAccountName(to).getOrElse { return Result.failure(it) }
        val source = users.findByName(from.trim())
            ?: return ServiceError.NotFound("No account $from").asFailure()
        if (users.findByName(name) != null) return ServiceError.Conflict("There is an account $name").asFailure()

        val written = mutableListOf<Pair<UUID, String>>()
        return try {
            Result.success(
                transactor.inTransaction {
                    val target = UserRecord(newId(), name, clock.now())
                    users.insert(target)
                    Copying(source.owner, target.owner, written).run()
                },
            )
        } catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
            // Cancellation included: the files must go whether the copy failed or was abandoned.
            removeFiles(written)
            throw failure
        }
    }

    /** One copy in progress: the ids it has handed out, so what points at a copied record points at its copy. */
    private inner class Copying(
        private val from: OwnerId,
        private val to: OwnerId,
        private val written: MutableList<Pair<UUID, String>>,
    ) {
        private var photoCount = 0

        suspend fun run(): CopyReport {
            val sources = objects.list(from, ObjectListQuery(sort = SortFieldDto.CREATED, order = SortOrderDto.ASC))
            sources.forEach { copyObject(it.record) }
            val sourceTasks = tasks.list(from, TaskQuery())
            sourceTasks.forEach { tasks.insert(TaskRecord(newId(), to, it.fields, it.createdAt)) }
            return CopyReport(sources.size, photoCount, sourceTasks.size)
        }

        private suspend fun copyObject(source: ObjectRecord) {
            val id = newId()
            // The cover points at a photo, which does not exist yet: it is set once the photos are copied.
            objects.insert(source.copy(id = id, ownerId = to, coverPhotoId = null))
            source.coordinates?.let { objects.setCoordinates(to, id, it) }

            val photoIds = copyPhotos(source.id, id)
            source.coverPhotoId?.let { photoIds[it] }?.let { objects.setCover(to, id, it) }

            contacts.listByObject(source.id).forEach {
                contacts.insert(ContactRecord(newId(), id, it.fields, it.sortOrder, it.createdAt))
            }
            terms.save(id, terms.find(source.id))
            copyPayments(source.id, id)
            extras.listByObject(source.id).forEach {
                extras.insert(ExtraWorkRecord(newId(), id, it.fields, it.createdAt))
            }
            materials.listByObject(source.id).forEach {
                materials.insert(MaterialRecord(newId(), id, it.fields, it.sortOrder, it.createdAt))
            }
        }

        /** Returns the new id of each photo that was copied, by the id it had. */
        private suspend fun copyPhotos(sourceObject: UUID, newObject: UUID): Map<UUID, UUID> {
            val ids = mutableMapOf<UUID, UUID>()
            for (photo in photos.listByObject(sourceObject)) {
                val id = newId()
                val fileName = "$id.${photo.fileName.substringAfterLast('.')}"
                val thumbFileName = "${id}_thumb.jpg"
                copyFile(sourceObject, photo.fileName, newObject, fileName)
                copyFile(sourceObject, photo.thumbFileName, newObject, thumbFileName)
                photos.insert(
                    photo.copy(id = id, objectId = newObject, fileName = fileName, thumbFileName = thumbFileName),
                )
                ids[photo.id] = id
                photoCount++
            }
            return ids
        }

        private suspend fun copyFile(
            fromObject: UUID,
            fromName: String,
            toObject: UUID,
            toName: String,
        ) {
            val bytes = storage.read(fromObject, fromName)
                ?: error("The file $fromObject/$fromName is missing, so the copy cannot be made whole")
            storage.write(toObject, toName, bytes)
            written += toObject to toName
        }

        private suspend fun copyPayments(sourceObject: UUID, newObject: UUID) {
            val ids = mutableMapOf<UUID, UUID>()
            payments.listByObject(sourceObject).forEach {
                val id = newId()
                ids[it.id] = id
                payments.insert(PaymentRecord(id, newObject, it.fields, it.createdAt))
            }
            // The history outlives a deleted payment, so some of it names a payment that is not there any more.
            payments.revisionsOf(sourceObject).forEach {
                val paymentId = ids.getOrPut(it.paymentId) { newId() }
                payments.addRevision(PaymentRevisionRecord(newId(), newObject, paymentId, it.action, it.fields, it.at))
            }
        }
    }

    private suspend fun removeFiles(files: List<Pair<UUID, String>>) = withContext(NonCancellable) {
        for ((objectId, fileName) in files) {
            try {
                storage.delete(objectId, fileName)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                log.warn("Could not remove $objectId/$fileName of a copy that failed; it is now an orphan", failure)
            }
        }
    }

    private companion object {
        val log = KtorSimpleLogger(AccountCopyService::class.qualifiedName!!)
    }
}
