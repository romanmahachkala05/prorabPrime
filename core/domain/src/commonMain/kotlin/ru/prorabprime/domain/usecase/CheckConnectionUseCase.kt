package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.ServerSettings

/** Checks the settings as they will be saved, so a trailing slash or space cannot pass here and fail later. */
class CheckConnectionUseCase(
    private val checker: ConnectionChecker,
) {
    suspend operator fun invoke(settings: ServerSettings): Result<Unit> = checker.check(settings.normalized())
}
