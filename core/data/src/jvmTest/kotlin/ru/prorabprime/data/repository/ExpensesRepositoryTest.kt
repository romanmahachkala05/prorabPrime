package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.contract.ReceiptDto
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentSide

class ExpensesRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private fun receipt(
        id: String,
        receipt: ReceiptDto?,
        kind: AttachmentKindDto = AttachmentKindDto.RECEIPT,
    ) = PhotoDto(id, "/files/o/$id.jpg", "/files/o/${id}_t.jpg", 8, 6, phone.server.at, kind = kind, receipt = receipt)

    private suspend fun synced(vararg photos: PhotoDto) {
        phone.server.details = phone.server.details.copy(photos = photos.toList())
        phone.server.objects = listOf(phone.server.details)
        phone.engine.sync()
    }

    private suspend fun items() = phone.expenses.observeExpenses().first().getOrThrow().items

    @Test
    fun `receipts become expenses with the sum and the day on the code, and photos do not`() = runTest {
        synced(
            receipt("11111111-1111-1111-1111-111111111111", ReceiptDto(79_000, "2026-10-01T15:26")),
            receipt("22222222-2222-2222-2222-222222222222", null, AttachmentKindDto.PHOTO),
        )

        val item = items().single()

        assertThat(item.kind).isEqualTo(ExpenseKind.RECEIPT)
        assertThat(item.amountKopecks).isEqualTo(79_000L)
        assertThat(item.day).isEqualTo(LocalDay.of(2026, 10, 1))
        assertThat(item.photoId?.value).isEqualTo("11111111-1111-1111-1111-111111111111")
        assertThat(item.objectTitle).isEqualTo("Кухня")
    }

    @Test
    fun `a receipt without a code has no sum, and takes the day it was photographed`() = runTest {
        synced(receipt("33333333-3333-3333-3333-333333333333", null))

        val item = items().single()

        assertThat(item.amountKopecks).isNull()
        assertThat(item.day).isEqualTo(LocalDay.ofInstant(phone.server.at))
    }

    @Test
    fun `payments to the crew are expenses and payments from the client are not`() = runTest {
        synced()
        val object1 = ObjectId(phone.server.objectId)
        val day = LocalDay.of(2026, 10, 2)
        phone.finance.addPayment(
            object1,
            PaymentDraft(PaymentSide.CREW, 300_000, paidOn = day, note = "Аванс"),
        ).getOrThrow()
        phone.finance.addPayment(object1, PaymentDraft(PaymentSide.CLIENT, 900_000, paidOn = day)).getOrThrow()

        val item = items().single()

        assertThat(item.kind).isEqualTo(ExpenseKind.CREW)
        assertThat(item.amountKopecks).isEqualTo(300_000L)
        assertThat(item.day).isEqualTo(day)
        assertThat(item.note).isEqualTo("Аванс")
    }

    @Test
    fun `items are newest day first, and a receipt set by hand shows at once`() = runTest {
        val id = "44444444-4444-4444-4444-444444444444"
        synced(receipt(id, null))
        phone.finance.addPayment(
            ObjectId(phone.server.objectId),
            PaymentDraft(PaymentSide.CREW, 1_000, paidOn = LocalDay.of(2030, 1, 1)),
        ).getOrThrow()
        phone.server.offline = true

        phone.photos.setReceipt(
            ru.prorabprime.domain.model.PhotoId(id),
            ru.prorabprime.domain.model.ReceiptInfo(5_000, "2026-10-05"),
        )
            .getOrThrow()

        assertThat(items().map { it.kind }).containsExactly(ExpenseKind.CREW, ExpenseKind.RECEIPT).inOrder()
        assertThat(items().last().amountKopecks).isEqualTo(5_000L)
    }

    @Test
    fun `an empty phone says nothing spent once it has asked the server`() = runTest {
        phone.server.objects = emptyList()
        phone.engine.sync()

        assertThat(items()).isEmpty()
    }
}
