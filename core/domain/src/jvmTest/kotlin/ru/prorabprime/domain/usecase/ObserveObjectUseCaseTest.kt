package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.anObjectDetails

class ObserveObjectUseCaseTest {

    private val repository = FakeObjectsRepository()
    private val observeObject = ObserveObjectUseCase(repository)

    @Test
    fun `emits the requested object`() = runTest {
        val details = anObjectDetails(id = "a")
        repository.details.value = mapOf(ObjectId("a") to details)

        assertThat(observeObject(ObjectId("a")).first().getOrThrow()).isEqualTo(details)
    }

    @Test
    fun `a missing object fails as NotFound`() = runTest {
        val result = observeObject(ObjectId("gone")).first()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
    }
}
