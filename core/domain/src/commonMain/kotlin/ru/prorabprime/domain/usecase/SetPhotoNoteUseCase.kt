package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.repository.PhotosRepository

/** Writes, changes or (with an empty text) clears the note of a photo. */
class SetPhotoNoteUseCase(
    private val repository: PhotosRepository,
) {
    suspend operator fun invoke(id: PhotoId, note: String?): Result<Unit> = repository.setNote(id, note)
}
