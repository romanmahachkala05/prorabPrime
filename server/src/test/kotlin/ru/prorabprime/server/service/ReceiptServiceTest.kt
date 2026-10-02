package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.ReceiptRequestDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.fakes.aPhotoRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.ReceiptData

class ReceiptServiceTest {

    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val clock = FixedClock()
    private val service = ReceiptService(objects, photos, ImmediateTransactor, clock)
    private val objectId = UUID.randomUUID()

    init {
        objects.records[objectId] = ObjectRecord(
            id = objectId,
            ownerId = TEST_OWNER,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
    }

    private fun receipt(withCode: Boolean = false) = aPhotoRecord(objectId, kind = AttachmentKindDto.RECEIPT).also {
        photos.records[it.id] = if (withCode) it.copy(receipt = ReceiptData(1, null, "s=0.01")) else it
    }

    private fun Result<*>.serviceError() = (exceptionOrNull() as? ServiceException)?.error

    @Test
    fun `a sum and a day are set, and the object's update time moves`() = runTest {
        val photo = receipt()
        clock.now = FIXED_NOW + 3.minutes

        service.set(TEST_OWNER, photo.id, ReceiptRequestDto(125_050, "2026-10-01")).getOrThrow()

        assertThat(photos.records.getValue(photo.id).receipt?.amountKopecks).isEqualTo(125_050L)
        assertThat(photos.records.getValue(photo.id).receipt?.purchasedAt).isEqualTo("2026-10-01")
        assertThat(objects.records.getValue(objectId).updatedAt).isEqualTo(FIXED_NOW + 3.minutes)
    }

    @Test
    fun `a sum set by hand replaces what the code said and keeps the code's text`() = runTest {
        val photo = receipt(withCode = true)

        service.set(TEST_OWNER, photo.id, ReceiptRequestDto(79_000, "2026-10-01T15:26")).getOrThrow()

        val stored = photos.records.getValue(photo.id).receipt
        assertThat(stored?.amountKopecks).isEqualTo(79_000L)
        assertThat(stored?.qr).isEqualTo("s=0.01")
    }

    @Test
    fun `no sum clears what is known`() = runTest {
        val photo = receipt(withCode = true)

        service.set(TEST_OWNER, photo.id, ReceiptRequestDto()).getOrThrow()

        assertThat(photos.records.getValue(photo.id).receipt).isNull()
    }

    @Test
    fun `a day that is not one, a negative or huge sum, a plain photo and an unknown photo are refused`() = runTest {
        val photo = receipt()
        val plain = aPhotoRecord(objectId).also { photos.records[it.id] = it }

        for (bad in listOf(
            ReceiptRequestDto(1, "2026-13-01"),
            ReceiptRequestDto(1, "01.10.2026"),
            ReceiptRequestDto(1, "2026-10-01T25:00"),
            ReceiptRequestDto(-1, "2026-10-01"),
            ReceiptRequestDto(Long.MAX_VALUE),
        )) {
            assertThat(
                service.set(TEST_OWNER, photo.id, bad).serviceError(),
            ).isInstanceOf(ServiceError.Validation::class.java)
        }
        assertThat(service.set(TEST_OWNER, plain.id, ReceiptRequestDto(1)).serviceError())
            .isInstanceOf(ServiceError.Validation::class.java)
        assertThat(service.set(TEST_OWNER, UUID.randomUUID(), ReceiptRequestDto(1)).serviceError())
            .isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(photos.records.getValue(photo.id).receipt).isNull()
    }
}
