package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakePhotosRepository

class SetCoverPhotoUseCaseTest {

    private val repository = FakePhotosRepository()
    private val setCover = SetCoverPhotoUseCase(repository)

    @Test
    fun `sets the photo as the object's cover`() = runTest {
        assertThat(setCover(ObjectId("a"), PhotoId("p")).isSuccess).isTrue()
        assertThat(repository.covers).containsExactly(ObjectId("a") to PhotoId("p"))
    }

    @Test
    fun `passes a repository failure through`() = runTest {
        repository.error = AppError.Network

        assertThat(setCover(ObjectId("a"), PhotoId("p")).exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
    }
}
