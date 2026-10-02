package ru.prorabprime.domain.model

import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

enum class PaymentSide {
    /** Money the client paid us. */
    CLIENT,

    /** Money we paid the crew. */
    CREW,
}

enum class PaymentMethod {
    CASH,
    TRANSFER,
    CARD,
    OTHER,
}

enum class ExtraWorkStatus {
    AGREED,
    NOT_AGREED,
}

enum class RevisionAction {
    CREATED,
    UPDATED,
    DELETED,
}

data class Payment(
    val id: PaymentId,
    val side: PaymentSide,
    val amountKopecks: Long,
    val method: PaymentMethod,
    val paidOn: LocalDay,
    val note: String?,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
)

data class ExtraWork(
    val id: ExtraWorkId,
    val title: String,
    val amountKopecks: Long,
    val status: ExtraWorkStatus,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
)

/** One side of the books; the amounts are null while nothing is agreed on that side. */
data class SideSummary(
    val agreedKopecks: Long?,
    val paidKopecks: Long,
    val remainingKopecks: Long?,
)

data class Finance(
    val clientTotalKopecks: Long?,
    val crewTotalKopecks: Long?,
    val client: SideSummary,
    val crew: SideSummary,
    val extrasAgreedKopecks: Long,
    val extrasPendingKopecks: Long,
    val payments: ImmutableList<Payment> = persistentListOf(),
    val extraWorks: ImmutableList<ExtraWork> = persistentListOf(),
)

data class PaymentRevision(
    val id: String,
    val paymentId: PaymentId,
    val action: RevisionAction,
    val side: PaymentSide,
    val amountKopecks: Long,
    val method: PaymentMethod,
    val paidOn: LocalDay,
    val note: String?,
    val at: Instant,
)

/** A payment as the form submits it; the amount and the day are null until they parse. */
data class PaymentDraft(
    val side: PaymentSide = PaymentSide.CLIENT,
    val amountKopecks: Long? = null,
    val method: PaymentMethod = PaymentMethod.CASH,
    val paidOn: LocalDay? = null,
    val note: String? = null,
) {
    fun normalized(): PaymentDraft = copy(note = note?.trim()?.takeIf { it.isNotEmpty() })

    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (amountKopecks == null || amountKopecks <= 0 || amountKopecks > MAX_AMOUNT_KOPECKS) {
                put(ObjectField.PAYMENT_AMOUNT, FieldProblem.INVALID)
            }
            if (paidOn == null) put(ObjectField.PAYMENT_DATE, FieldProblem.INVALID)
            if ((note?.length ?: 0) > MAX_NOTE) put(ObjectField.PAYMENT_NOTE, FieldProblem.TOO_LONG)
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        // Mirror :api-contract's FinanceLimits, which the domain cannot see.
        const val MAX_AMOUNT_KOPECKS = 100_000_000_000L
        const val MAX_NOTE = 500
    }
}

data class ExtraWorkDraft(
    val title: String = "",
    val amountKopecks: Long? = null,
    val status: ExtraWorkStatus = ExtraWorkStatus.NOT_AGREED,
) {
    fun normalized(): ExtraWorkDraft = copy(title = title.trim())

    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (title.isBlank()) put(ObjectField.WORK_TITLE, FieldProblem.REQUIRED)
            if (title.length > MAX_TITLE) put(ObjectField.WORK_TITLE, FieldProblem.TOO_LONG)
            if (amountKopecks == null || amountKopecks < 0 || amountKopecks > PaymentDraft.MAX_AMOUNT_KOPECKS) {
                put(ObjectField.WORK_AMOUNT, FieldProblem.INVALID)
            }
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        const val MAX_TITLE = 200
    }
}

/** What was agreed for the whole object; either side may be left unset. */
data class FinanceTermsDraft(
    val clientTotalKopecks: Long? = null,
    val crewTotalKopecks: Long? = null,
) {
    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val valid = listOfNotNull(clientTotalKopecks, crewTotalKopecks).all { it in 0..PaymentDraft.MAX_AMOUNT_KOPECKS }
        return if (valid) persistentMapOf() else persistentMapOf(ObjectField.TOTAL_AMOUNT to FieldProblem.INVALID)
    }
}
