package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoRejection
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakePhotosRepository

class UploadPhotoUseCaseTest {

    private val compressor = FakeImageCompressor()
    private val repository = FakePhotosRepository()
    private val uploadPhoto = UploadPhotoUseCase(compressor, repository)

    @Test
    fun `uploads the compressed image to the object`() = runTest {
        val result = uploadPhoto(ObjectId("a"), LocalImageRef("content://camera/1"))

        assertThat(result.isSuccess).isTrue()
        val (objectId, image) = repository.uploaded.single()
        assertThat(objectId).isEqualTo(ObjectId("a"))
        assertThat(image.bytes.decodeToString()).isEqualTo("content://camera/1")
    }

    @Test
    fun `a compression failure uploads nothing`() = runTest {
        compressor.error = AppError.Unknown

        val result = uploadPhoto(ObjectId("a"), LocalImageRef("content://camera/1"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Unknown)
        assertThat(repository.uploaded).isEmpty()
    }

    @Test
    fun `passes an upload rejection through`() = runTest {
        repository.error = AppError.PhotoRejected(PhotoRejection.TOO_LARGE)

        val result = uploadPhoto(ObjectId("a"), LocalImageRef("content://camera/1"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.PhotoRejected(PhotoRejection.TOO_LARGE))
    }
}
