package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakePhotosRepository

class DeletePhotoUseCaseTest {

    private val repository = FakePhotosRepository()
    private val deletePhoto = DeletePhotoUseCase(repository)

    @Test
    fun `deletes the photo`() = runTest {
        assertThat(deletePhoto(PhotoId("p")).isSuccess).isTrue()
        assertThat(repository.deleted).containsExactly(PhotoId("p"))
    }

    @Test
    fun `passes a repository failure through`() = runTest {
        repository.error = AppError.NotFound

        assertThat(deletePhoto(PhotoId("p")).exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
    }
}
