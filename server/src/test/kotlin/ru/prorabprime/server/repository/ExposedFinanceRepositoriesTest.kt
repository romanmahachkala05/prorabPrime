package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord

class ExposedFinanceRepositoriesTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var objects: ExposedObjectRepository
    private lateinit var terms: ExposedFinanceTermsRepository
    private lateinit var payments: ExposedPaymentRepository
    private lateinit var extras: ExposedExtraWorkRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")
    private val objectId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        objects = ExposedObjectRepository(db)
        terms = ExposedFinanceTermsRepository(db)
        payments = ExposedPaymentRepository(db)
        extras = ExposedExtraWorkRepository(db)
        objects.insert(
            ObjectRecord(
                id = objectId,
                fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
                coverPhotoId = null,
                createdAt = base,
                updatedAt = base,
            ),
        )
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun fields(day: String, kopecks: Long = 100_000) = PaymentFields(
        PaymentSideDto.CLIENT,
        kopecks,
        PaymentMethodDto.CARD,
        LocalDate.parse(day),
        "аванс",
    )

    private fun payment(day: String, minutesLater: Int = 0) =
        PaymentRecord(UUID.randomUUID(), objectId, fields(day), base + minutesLater.minutes)

    @Test
    fun `no terms read as empty, and saving replaces them`() = runTest {
        assertThat(terms.find(objectId)).isEqualTo(FinanceTerms())

        terms.save(objectId, FinanceTerms(1_000, null))
        terms.save(objectId, FinanceTerms(2_000, 500))

        assertThat(terms.find(objectId)).isEqualTo(FinanceTerms(2_000, 500))
    }

    @Test
    fun `payments read back unchanged, oldest day first`() = runTest {
        val late = payment("2026-09-30")
        val early = payment("2026-09-01")
        payments.insert(late)
        payments.insert(early)

        assertThat(payments.find(early.id)).isEqualTo(early)
        assertThat(payments.listByObject(objectId)).containsExactly(early, late).inOrder()
    }

    @Test
    fun `updating and deleting report whether there was a payment`() = runTest {
        val payment = payment("2026-09-01")
        payments.insert(payment)

        assertThat(payments.update(payment.id, fields("2026-09-02", 5))).isTrue()
        assertThat(payments.find(payment.id)?.fields).isEqualTo(fields("2026-09-02", 5))
        assertThat(payments.update(UUID.randomUUID(), fields("2026-09-02"))).isFalse()
        assertThat(payments.delete(payment.id)).isTrue()
        assertThat(payments.delete(payment.id)).isFalse()
    }

    @Test
    fun `the history survives the payment and lists newest first`() = runTest {
        val payment = payment("2026-09-01")
        val created =
            PaymentRevisionRecord(
                UUID.randomUUID(),
                objectId,
                payment.id,
                RevisionActionDto.CREATED,
                payment.fields,
                base,
            )
        val deleted = PaymentRevisionRecord(
            UUID.randomUUID(),
            objectId,
            payment.id,
            RevisionActionDto.DELETED,
            payment.fields,
            base + 5.minutes,
        )
        payments.insert(payment)
        payments.addRevision(created)
        payments.delete(payment.id)
        payments.addRevision(deleted)

        assertThat(payments.revisionsOf(objectId)).containsExactly(deleted, created).inOrder()
    }

    @Test
    fun `extra works read back, update, delete, and go with the object`() = runTest {
        val work = ExtraWorkRecord(
            UUID.randomUUID(),
            objectId,
            ExtraWorkFields("Штробление", 50_000, ExtraWorkStatusDto.NOT_AGREED),
            base,
        )
        extras.insert(work)
        assertThat(extras.find(work.id)).isEqualTo(work)

        val changed = ExtraWorkFields("Штробление", 60_000, ExtraWorkStatusDto.AGREED)
        assertThat(extras.update(work.id, changed)).isTrue()
        assertThat(extras.listByObject(objectId).single().fields).isEqualTo(changed)

        objects.delete(objectId)
        assertThat(extras.listByObject(objectId)).isEmpty()
        assertThat(payments.listByObject(objectId)).isEmpty()
        assertThat(terms.find(objectId)).isEqualTo(FinanceTerms())
    }
}
