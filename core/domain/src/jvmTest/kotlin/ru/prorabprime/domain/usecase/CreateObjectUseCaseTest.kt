package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeObjectsRepository

class CreateObjectUseCaseTest {

    private val repository = FakeObjectsRepository()
    private val createObject = CreateObjectUseCase(repository)

    @Test
    fun `creates the normalized draft and returns the new id`() = runTest {
        val result = createObject(ObjectDraft(title = " ", address = " Тверская, 5 "))

        assertThat(result.getOrThrow()).isEqualTo(ObjectId("created-1"))
        assertThat(repository.created).containsExactly(ObjectDraft(title = null, address = "Тверская, 5"))
    }

    @Test
    fun `an invalid draft fails without reaching the repository`() = runTest {
        val result = createObject(ObjectDraft(address = "  "))

        assertThat(result.exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Validation(persistentMapOf(ObjectField.ADDRESS to FieldProblem.REQUIRED)))
        assertThat(repository.created).isEmpty()
    }

    @Test
    fun `passes a repository failure through`() = runTest {
        repository.writeError = AppError.Unauthorized

        val result = createObject(ObjectDraft(address = "Тверская, 5"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Unauthorized)
    }
}
