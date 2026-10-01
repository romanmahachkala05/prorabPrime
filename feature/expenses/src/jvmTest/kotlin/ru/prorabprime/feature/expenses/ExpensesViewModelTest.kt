package ru.prorabprime.feature.expenses

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExpenseItem
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.testing.FakeExpensesRepository
import ru.prorabprime.testing.MainDispatcherRule

class ExpensesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeExpensesRepository()
    private val today = LocalDay.of(2026, 10, 15)

    private fun viewModel() = ExpensesViewModel(ObserveExpensesUseCase(repository)) { today }

    private fun receipt(
        id: String,
        objectId: String,
        day: LocalDay,
        kopecks: Long?,
    ) = ExpenseItem(
        id,
        ExpenseKind.RECEIPT,
        ObjectId(objectId),
        "Объект $objectId",
        day,
        kopecks,
        photoId = PhotoId(id),
    )

    private fun crew(
        id: String,
        objectId: String,
        day: LocalDay,
        kopecks: Long,
    ) = ExpenseItem(id, ExpenseKind.CREW, ObjectId(objectId), "Объект $objectId", day, kopecks, note = "Аванс")

    private val items = listOf(
        crew("c1", "1", LocalDay.of(2026, 10, 10), 2_100_000),
        receipt("r1", "1", LocalDay.of(2026, 10, 3), 7_900_000),
        receipt("r2", "2", LocalDay.of(2026, 9, 28), 50_000_000),
        receipt("r3", "2", LocalDay.of(2026, 9, 1), null),
    )

    @Test
    fun `it waits for the copy of the data before showing anything`() {
        assertThat(viewModel().state.value.status).isEqualTo(ExpensesStatus.Loading)
    }

    @Test
    fun `the figures, the bars and the rows are made from the expenses`() {
        repository.result.value = Result.success(Expenses(items))

        val state = viewModel().state.value

        assertThat(state.status).isEqualTo(ExpensesStatus.Content)
        assertThat(state.summary.total).isEqualTo(Money.format(60_000_000))
        assertThat(state.summary.thisMonth).isEqualTo(Money.format(10_000_000))
        assertThat(state.summary.materials).isEqualTo(Money.format(57_900_000))
        assertThat(state.summary.crew).isEqualTo(Money.format(2_100_000))
        assertThat(state.summary.withoutAmount).isEqualTo(1)
        assertThat(state.byObject.map { it.label }).containsExactly("Объект 2", "Объект 1").inOrder()
        assertThat(state.byObject.first().fraction).isEqualTo(1f)
        assertThat(state.byMonth.map { it.label }).containsExactly("Октябрь 2026", "Сентябрь 2026").inOrder()
        assertThat(state.rows).hasSize(4)
        assertThat(state.allRows).isEqualTo(4)
    }

    @Test
    fun `a receipt row opens its photo, and shows no sum when there is none`() {
        repository.result.value = Result.success(Expenses(items))

        val rows = viewModel().state.value.rows.associateBy { it.id }

        assertThat(rows.getValue("r1").photoId).isEqualTo("r1")
        assertThat(rows.getValue("r1").amount).isEqualTo(Money.format(7_900_000))
        assertThat(rows.getValue("r3").amount).isNull()
        assertThat(rows.getValue("c1").photoId).isNull()
        assertThat(rows.getValue("c1").isReceipt).isFalse()
        assertThat(rows.getValue("c1").day).isEqualTo("10.10.2026")
    }

    @Test
    fun `the filter narrows the rows and leaves the figures`() {
        repository.result.value = Result.success(Expenses(items))
        val viewModel = viewModel()

        viewModel.selectFilter(ExpenseFilter.CREW)

        val state = viewModel.state.value
        assertThat(state.rows.map { it.id }).containsExactly("c1")
        assertThat(state.summary.total).isEqualTo(Money.format(60_000_000))
        assertThat(state.filter).isEqualTo(ExpenseFilter.CREW)

        viewModel.selectFilter(ExpenseFilter.MATERIALS)
        assertThat(viewModel.state.value.rows.map { it.id }).containsExactly("r1", "r2", "r3")
    }

    @Test
    fun `a new reading keeps the filter`() {
        val viewModel = viewModel()
        repository.result.value = Result.success(Expenses(items))
        viewModel.selectFilter(ExpenseFilter.CREW)

        repository.result.value = Result.success(Expenses(items + crew("c2", "1", today, 100_000)))

        assertThat(viewModel.state.value.rows.map { it.id }).containsExactly("c2", "c1")
    }

    @Test
    fun `nothing spent is said by an empty list of rows`() {
        repository.result.value = Result.success(Expenses(emptyList()))

        assertThat(viewModel().state.value.allRows).isEqualTo(0)
    }

    @Test
    fun `a failure to read is an error`() {
        repository.result.value = AppError.Unknown.asFailure()

        assertThat(viewModel().state.value.status).isInstanceOf(ExpensesStatus.Error::class.java)
    }

    @Test
    fun `months are named in Russian, and a strange one is left as it came`() {
        assertThat(monthLabel("2026-01")).isEqualTo("Январь 2026")
        assertThat(monthLabel("2026-12")).isEqualTo("Декабрь 2026")
        assertThat(monthLabel("2026-13")).isEqualTo("2026-13")
        assertThat(monthLabel("soon")).isEqualTo("soon")
    }
}
