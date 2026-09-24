package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.anObjectSummary

class ObserveObjectsUseCaseTest {

    private val repository = FakeObjectsRepository()
    private val observeObjects = ObserveObjectsUseCase(repository)

    @Test
    fun `emits the repository's objects for the query`() = runTest {
        repository.objects.value = listOf(anObjectSummary(id = "a"), anObjectSummary(id = "b"))
        val query = ObjectQuery(search = "лен", sort = ObjectSort.ADDRESS_ASC)

        val result = observeObjects(query).first()

        assertThat(result.getOrThrow().map { it.id.value }).containsExactly("a", "b").inOrder()
        assertThat(repository.queries).containsExactly(query)
    }

    @Test
    fun `passes a load failure through`() = runTest {
        repository.loadError.value = AppError.Network

        val result = observeObjects(ObjectQuery()).first()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
    }
}
