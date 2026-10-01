package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.contract.MaterialDefaults
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.Operation
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.TaskDraft

class FinanceMaterialsTasksRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private val objectId get() = ObjectId(phone.server.objectId)

    private val today = LocalDay.of(2026, 10, 1)

    private suspend fun offlineAndSynced(): OfflineFixture = phone.also {
        it.engine.sync()
        it.server.offline = true
    }

    private fun payment(side: PaymentSide, kopecks: Long) =
        PaymentDraft(side = side, amountKopecks = kopecks, paidOn = today)

    @Test
    fun `the sums are worked out on the phone as the server works them out`() = runTest {
        offlineAndSynced()

        phone.finance.addPayment(objectId, payment(PaymentSide.CLIENT, 400)).getOrThrow()
        phone.finance.addPayment(objectId, payment(PaymentSide.CREW, 100)).getOrThrow()
        phone.finance.addExtraWork(objectId, ExtraWorkDraft("Штробление", 200, ExtraWorkStatus.AGREED)).getOrThrow()
        phone.finance.addExtraWork(objectId, ExtraWorkDraft("Балкон", 70, ExtraWorkStatus.NOT_AGREED)).getOrThrow()

        val finance = phone.finance.observeFinance(objectId).first().getOrThrow()

        // The contract was 1000 (from the server); the agreed extra makes it 1200; 400 is paid.
        assertThat(finance.client.agreedKopecks).isEqualTo(1_200L)
        assertThat(finance.client.paidKopecks).isEqualTo(400L)
        assertThat(finance.client.remainingKopecks).isEqualTo(800L)
        assertThat(finance.crew.agreedKopecks).isNull()
        assertThat(finance.crew.paidKopecks).isEqualTo(100L)
        assertThat(finance.extrasAgreedKopecks).isEqualTo(200L)
        assertThat(finance.extrasPendingKopecks).isEqualTo(70L)
        assertThat(finance.payments.all { it.isPending }).isTrue()
    }

    @Test
    fun `agreed totals are saved on the phone and queued`() = runTest {
        offlineAndSynced()

        phone.finance.setTerms(
            objectId,
            FinanceTermsDraft(clientTotalKopecks = 5_000, crewTotalKopecks = 2_000),
        ).getOrThrow()

        val finance = phone.finance.observeFinance(objectId).first().getOrThrow()
        assertThat(finance.client.agreedKopecks).isEqualTo(5_000L)
        assertThat(finance.crew.agreedKopecks).isEqualTo(2_000L)
        assertThat(Keys.terms(objectId.value)).isIn(phone.db.outbox.dirtyKeys())
    }

    @Test
    fun `a payment that never left the phone can be changed and deleted without a trace to send`() = runTest {
        offlineAndSynced()
        phone.finance.addPayment(objectId, payment(PaymentSide.CLIENT, 400)).getOrThrow()
        val id = phone.finance.observeFinance(objectId).first().getOrThrow().payments.single().id

        phone.finance.updatePayment(id, payment(PaymentSide.CLIENT, 450)).getOrThrow()
        assertThat(phone.finance.observeFinance(objectId).first().getOrThrow().client.paidKopecks).isEqualTo(450L)

        phone.finance.deletePayment(id).getOrThrow()

        assertThat(phone.db.outbox.snapshot()).isEmpty()
        assertThat(phone.finance.observeFinance(objectId).first().getOrThrow().payments).isEmpty()
    }

    @Test
    fun `the checklist is edited on the phone, and the usual materials skip what is already there`() = runTest {
        offlineAndSynced()
        phone.materials.add(objectId, MaterialDraft(MaterialDefaults.TITLES.first().uppercase())).getOrThrow()

        phone.materials.addDefaults(objectId).getOrThrow()

        val list = phone.materials.observeMaterials(objectId).first().getOrThrow()
        // The server had one ("Ламинат") already, the phone added one of the usual by hand: neither is doubled.
        assertThat(list.map { it.title.lowercase() }.distinct()).hasSize(list.size)
        assertThat(list.size).isEqualTo(MaterialDefaults.TITLES.size + 1)
        assertThat(phone.db.outbox.snapshot().last().operation).isEqualTo(Operation.AddDefaultMaterials(objectId.value))
    }

    @Test
    fun `a material's status change is kept and queued`() = runTest {
        offlineAndSynced()
        val id = phone.materials.observeMaterials(objectId).first().getOrThrow().single().id

        phone.materials.update(id, MaterialDraft("Ламинат", MaterialStatus.IN_APARTMENT)).getOrThrow()

        val shown = phone.materials.observeMaterials(objectId).first().getOrThrow().single()
        assertThat(shown.status).isEqualTo(MaterialStatus.IN_APARTMENT)
        assertThat(shown.isPending).isTrue()
    }

    @Test
    fun `the day plan reads from the phone, in the order of the day`() = runTest {
        offlineAndSynced()
        phone.tasks.add(TaskDraft("Без времени", today)).getOrThrow()
        phone.tasks.add(TaskDraft("Утром", today, remindAtMinutes = 9 * 60)).getOrThrow()
        phone.tasks.add(TaskDraft("Вчера", today.plusDays(-1))).getOrThrow()
        phone.tasks.add(TaskDraft("Завтра", today.plusDays(1))).getOrThrow()

        val day = phone.tasks.observeDay(today).first().getOrThrow().map { it.title }
        val overdue = phone.tasks.observeOverdue(today).first().getOrThrow().map { it.title }
        val upcoming = phone.tasks.observeOpenFrom(today).first().getOrThrow().map { it.title }

        // "Позвонить" is the task the server already had for today.
        assertThat(day).containsExactly("Утром", "Позвонить", "Без времени").inOrder()
        assertThat(overdue).containsExactly("Вчера")
        assertThat(upcoming).containsExactly("Утром", "Позвонить", "Без времени", "Завтра").inOrder()
    }

    @Test
    fun `a done task is not overdue or open, and a task that never left the phone is forgotten when deleted`() =
        runTest {
            offlineAndSynced()
            phone.tasks.add(TaskDraft("Сделано", today.plusDays(-1), done = true)).getOrThrow()
            phone.tasks.add(TaskDraft("Забыть", today)).getOrThrow()
            val forgotten = phone.tasks.observeDay(today).first().getOrThrow().first { it.title == "Забыть" }.id

            phone.tasks.delete(forgotten).getOrThrow()

            assertThat(phone.tasks.observeOverdue(today).first().getOrThrow()).isEmpty()
            assertThat(
                phone.db.outbox.snapshot().map {
                    it.operation
                },
            ).doesNotContain(Operation.DeleteTask(forgotten.value))
            assertThat(phone.tasks.observeDay(today).first().getOrThrow().map { it.title }).doesNotContain("Забыть")
        }
}
