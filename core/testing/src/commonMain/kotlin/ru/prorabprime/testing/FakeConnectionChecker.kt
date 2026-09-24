package ru.prorabprime.testing

import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asFailure

class FakeConnectionChecker : ConnectionChecker {

    var error: AppError? = null

    val checked = mutableListOf<ServerSettings>()

    override suspend fun check(settings: ServerSettings): Result<Unit> {
        checked += settings
        return error?.asFailure() ?: Result.success(Unit)
    }
}
