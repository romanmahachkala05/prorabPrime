package ru.prorabprime.data.local

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.TaskRequestDto

/** Names a row for "this is changed on the phone and not yet on the server". */
internal object Keys {
    fun obj(id: String) = "object:$id"

    fun contact(id: String) = "contact:$id"

    fun photo(id: String) = "photo:$id"

    fun payment(id: String) = "payment:$id"

    fun extra(id: String) = "extra:$id"

    fun material(id: String) = "material:$id"

    fun task(id: String) = "task:$id"

    fun terms(objectId: String) = "terms:$objectId"

    /** Anything about an object's whole checklist, such as "add the usual materials". */
    fun materialsOf(objectId: String) = "materials-of:$objectId"
}

/**
 * A change made on the phone that the server has not heard of yet. Each carries what it takes to be
 * sent again byte for byte, including the id the phone chose for a new record, so a send that died
 * half way can be repeated without making a second record.
 */
@Serializable
internal sealed interface Operation {
    /** The rows this changes: while one is waiting, a pull must not overwrite the phone's version of it. */
    val touched: List<String>

    @Serializable
    data class CreateObject(
        val request: ObjectRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.obj(request.id.orEmpty()))
    }

    @Serializable
    data class UpdateObject(
        val id: String,
        val request: ObjectRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.obj(id))
    }

    @Serializable
    data class DeleteObject(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.obj(id))
    }

    @Serializable
    data class SetCover(
        val objectId: String,
        val photoId: String,
    ) : Operation {
        override val touched get() = listOf(Keys.obj(objectId))
    }

    /** [blob] names the picture's bytes in the blob store; they are deleted once the server has them. */
    @Serializable
    data class UploadPhoto(
        val objectId: String,
        val photoId: String,
        val kind: AttachmentKindDto,
        val blob: String,
        val mimeType: String,
    ) : Operation {
        override val touched get() = listOf(Keys.photo(photoId))
    }

    @Serializable
    data class DeletePhoto(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.photo(id))
    }

    @Serializable
    data class CreateContact(
        val objectId: String,
        val request: ContactRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.contact(request.id.orEmpty()))
    }

    @Serializable
    data class UpdateContact(
        val id: String,
        val request: ContactRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.contact(id))
    }

    @Serializable
    data class DeleteContact(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.contact(id))
    }

    @Serializable
    data class SetTerms(
        val objectId: String,
        val terms: FinanceTermsDto,
    ) : Operation {
        override val touched get() = listOf(Keys.terms(objectId))
    }

    @Serializable
    data class CreatePayment(
        val objectId: String,
        val request: PaymentRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.payment(request.id.orEmpty()))
    }

    @Serializable
    data class UpdatePayment(
        val id: String,
        val request: PaymentRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.payment(id))
    }

    @Serializable
    data class DeletePayment(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.payment(id))
    }

    @Serializable
    data class CreateExtraWork(
        val objectId: String,
        val request: ExtraWorkRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.extra(request.id.orEmpty()))
    }

    @Serializable
    data class UpdateExtraWork(
        val id: String,
        val request: ExtraWorkRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.extra(id))
    }

    @Serializable
    data class DeleteExtraWork(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.extra(id))
    }

    @Serializable
    data class CreateMaterial(
        val objectId: String,
        val request: MaterialRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.material(request.id.orEmpty()))
    }

    @Serializable
    data class UpdateMaterial(
        val id: String,
        val request: MaterialRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.material(id))
    }

    @Serializable
    data class DeleteMaterial(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.material(id))
    }

    @Serializable
    data class AddDefaultMaterials(
        val objectId: String,
    ) : Operation {
        override val touched get() = listOf(Keys.materialsOf(objectId))
    }

    @Serializable
    data class CreateTask(
        val request: TaskRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.task(request.id.orEmpty()))
    }

    @Serializable
    data class UpdateTask(
        val id: String,
        val request: TaskRequestDto,
    ) : Operation {
        override val touched get() = listOf(Keys.task(id))
    }

    @Serializable
    data class DeleteTask(
        val id: String,
    ) : Operation {
        override val touched get() = listOf(Keys.task(id))
    }
}

internal enum class OperationState {
    /** Waiting for its turn, or for the network. */
    PENDING,

    /** The server refused it for good (not "try later"); it stays for the user to see and decide. */
    FAILED,
}

@Serializable
internal data class QueuedOperation(
    val seq: Long,
    val operation: Operation,
    val createdAt: Instant,
    val state: OperationState = OperationState.PENDING,
    val attempts: Int = 0,
    /** Why the server refused it, in words for a person; null while pending. */
    val reason: String? = null,
)

/** A change that makes a record, as opposed to one that changes or removes it. */
internal fun Operation.isCreate(): Boolean = this is Operation.CreateObject ||
    this is Operation.UploadPhoto ||
    this is Operation.CreateContact ||
    this is Operation.CreatePayment ||
    this is Operation.CreateExtraWork ||
    this is Operation.CreateMaterial ||
    this is Operation.CreateTask

/**
 * Deletes a record the server may have: queues the delete. A record only the phone ever knew has
 * nothing to delete on the server, so its changes are forgotten instead and nothing is sent.
 */
internal suspend fun LocalDb.forgetOrQueueDelete(key: String, delete: Operation) {
    val neverSent = outbox.snapshot().any { it.operation.isCreate() && key in it.operation.touched }
    outbox.removeWhere { key in it.operation.touched }
    if (!neverSent) outbox.enqueue(delete)
}
