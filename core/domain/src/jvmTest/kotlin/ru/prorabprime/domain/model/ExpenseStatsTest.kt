package ru.prorabprime.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ExpenseStatsTest {

    private val today = LocalDay.of(2026, 10, 15)

    private fun item(
        id: String,
        objectId: String,
        day: LocalDay,
        kopecks: Long?,
        kind: ExpenseKind = ExpenseKind.RECEIPT,
    ) = ExpenseItem(id, kind, ObjectId(objectId), "Объект $objectId", day, kopecks)

    private fun expenses(vararg items: ExpenseItem) = Expenses(items.toList())

    @Test
    fun `nothing spent is all zeros`() {
        val stats = Expenses(emptyList()).stats(today)

        assertThat(stats.totalKopecks).isEqualTo(0L)
        assertThat(stats.byObject).isEmpty()
        assertThat(stats.byMonth).isEmpty()
        assertThat(stats.withoutAmount).isEqualTo(0)
    }

    @Test
    fun `the total, the month and the two kinds add up`() {
        val stats = expenses(
            item("a", "1", LocalDay.of(2026, 10, 3), 79_000),
            item("b", "1", LocalDay.of(2026, 10, 10), 21_000, ExpenseKind.CREW),
            item("c", "2", LocalDay.of(2026, 9, 28), 500_000),
        ).stats(today)

        assertThat(stats.totalKopecks).isEqualTo(600_000L)
        assertThat(stats.thisMonthKopecks).isEqualTo(100_000L)
        assertThat(stats.receiptsKopecks).isEqualTo(579_000L)
        assertThat(stats.crewKopecks).isEqualTo(21_000L)
    }

    @Test
    fun `objects are listed by what was spent on them, most first, with how many items`() {
        val stats = expenses(
            item("a", "1", LocalDay.of(2026, 10, 3), 79_000),
            item("b", "2", LocalDay.of(2026, 10, 4), 500_000),
            item("c", "1", LocalDay.of(2026, 10, 5), 21_000, ExpenseKind.CREW),
        ).stats(today)

        assertThat(stats.byObject.map { it.objectId.value }).containsExactly("2", "1").inOrder()
        assertThat(stats.byObject.last().kopecks).isEqualTo(100_000L)
        assertThat(stats.byObject.last().count).isEqualTo(2)
    }

    @Test
    fun `months are newest first, and only the last twelve are kept`() {
        val items = (0 until 14).map { back ->
            item("i$back", "1", LocalDay.of(2026, 10, 1).plusDays(-31 * back), 1_000L * (back + 1))
        }

        val stats = Expenses(items).stats(today)

        assertThat(stats.byMonth).hasSize(MONTHS_SHOWN)
        assertThat(stats.byMonth.first().month).isEqualTo("2026-10")
        assertThat(stats.byMonth.map { it.month }).isInOrder(compareByDescending<String> { it })
    }

    @Test
    fun `a receipt without a sum is counted apart and adds nothing`() {
        val stats = expenses(
            item("a", "1", LocalDay.of(2026, 10, 3), 79_000),
            item("b", "1", LocalDay.of(2026, 10, 4), null),
        ).stats(today)

        assertThat(stats.withoutAmount).isEqualTo(1)
        assertThat(stats.totalKopecks).isEqualTo(79_000L)
        assertThat(stats.byObject.single().count).isEqualTo(1)
    }
}
