package ru.prorabprime.server.service

import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.AccountInfo
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.repository.UserRepository

/** What an account may be shown about itself: its name and how much of its room for pictures is used. */
class ProfileService(
    private val users: UserRepository,
    private val photos: PhotoRepository,
    private val quotaBytes: Long?,
) {
    suspend fun get(owner: OwnerId): Result<AccountInfo> {
        val user = users.find(owner.value) ?: return ServiceError.NotFound("No account $owner").asFailure()
        return Result.success(AccountInfo(user.name, photos.usedBytes(owner), quotaBytes))
    }
}
