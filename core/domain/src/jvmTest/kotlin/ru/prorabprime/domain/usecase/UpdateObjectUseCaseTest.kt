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

class UpdateObjectUseCaseTest {

    private val repository = FakeObjectsRepository()
    private val updateObject = UpdateObjectUseCase(repository)

    @Test
    fun `updates the object with the normalized draft`() = runTest {
        val result = updateObject(ObjectId("a"), ObjectDraft(address = "Тверская, 5", notes = " "))

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.updated).containsExactly(ObjectId("a") to ObjectDraft(address = "Тверская, 5"))
    }

    @Test
    fun `an invalid draft fails without reaching the repository`() = runTest {
        val result = updateObject(ObjectId("a"), ObjectDraft(address = ""))

        assertThat(result.exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Validation(persistentMapOf(ObjectField.ADDRESS to FieldProblem.REQUIRED)))
        assertThat(repository.updated).isEmpty()
    }

    @Test
    fun `passes a repository failure through`() = runTest {
        repository.writeError = AppError.NotFound

        val result = updateObject(ObjectId("a"), ObjectDraft(address = "Тверская, 5"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
    }
}
