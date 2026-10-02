package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeObjectsRepository

class DeleteObjectUseCaseTest {

    private val repository = FakeObjectsRepository()
    private val deleteObject = DeleteObjectUseCase(repository)

    @Test
    fun `deletes the object`() = runTest {
        assertThat(deleteObject(ObjectId("a")).isSuccess).isTrue()
        assertThat(repository.deleted).containsExactly(ObjectId("a"))
    }

    @Test
    fun `passes a repository failure through`() = runTest {
        repository.writeError = AppError.Network

        assertThat(deleteObject(ObjectId("a")).exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
    }
}
