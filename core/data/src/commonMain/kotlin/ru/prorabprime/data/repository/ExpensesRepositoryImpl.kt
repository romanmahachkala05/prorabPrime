package ru.prorabprime.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.ObjectRow
import ru.prorabprime.data.local.PaymentRow
import ru.prorabprime.data.local.PhotoRow
import ru.prorabprime.domain.model.ExpenseItem
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.repository.ExpensesRepository

/** Receipts and payments to the crew, gathered from the phone's copy of every object. */
internal class ExpensesRepositoryImpl(
    private val db: LocalDb,
) : ExpensesRepository {

    override fun observeExpenses(): Flow<Result<Expenses>> = db.changes.transform {
        // Nothing to say until the copy is loaded: an empty list now would read as "nothing spent".
        if (db.objects.rows.value.isNotEmpty() || db.metaRow.syncTried) emit(Result.success(expenses()))
    }

    private fun expenses(): Expenses {
        val objects = db.objects.rows.value
        val receipts = db.photos.rows.value.values
            .filter { it.kind == AttachmentKindDto.RECEIPT }
            .mapNotNull { photo -> objects[photo.objectId]?.let { receiptItem(photo, it) } }
        val crew = db.payments.rows.value.values
            .filter { it.dto.side == PaymentSideDto.CREW }
            .mapNotNull { payment -> objects[payment.objectId]?.let { crewItem(payment, it) } }
        return Expenses(
            (receipts + crew).sortedWith(compareByDescending<ExpenseItem> { it.day }.thenBy { it.id }),
        )
    }

    private fun receiptItem(photo: PhotoRow, obj: ObjectRow) = ExpenseItem(
        id = photo.id,
        kind = ExpenseKind.RECEIPT,
        objectId = ObjectId(obj.id),
        objectTitle = obj.title ?: obj.address,
        day = photo.receiptAt?.substringBefore('T')?.let(LocalDay::parseIso) ?: LocalDay.ofInstant(photo.createdAt),
        amountKopecks = photo.receiptAmountKopecks,
        photoId = PhotoId(photo.id),
        note = photo.note,
    )

    private fun crewItem(payment: PaymentRow, obj: ObjectRow): ExpenseItem? {
        val day = LocalDay.parseIso(payment.dto.paidOn) ?: return null
        return ExpenseItem(
            id = payment.dto.id,
            kind = ExpenseKind.CREW,
            objectId = ObjectId(obj.id),
            objectTitle = obj.title ?: obj.address,
            day = day,
            amountKopecks = payment.dto.amountKopecks,
            note = payment.dto.note,
        )
    }
}
