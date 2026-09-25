package ru.prorabprime.feature.objects.details

import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SetCoverPhotoUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase

/** The use cases the details screen calls, in one collaborator to keep the ViewModel's constructor short. */
internal class ObjectDetailsActions(
    val observeObject: ObserveObjectUseCase,
    val refreshObjects: RefreshObjectsUseCase,
    val deleteObject: DeleteObjectUseCase,
    val uploadPhoto: UploadPhotoUseCase,
    val deletePhoto: DeletePhotoUseCase,
    val setCoverPhoto: SetCoverPhotoUseCase,
)
