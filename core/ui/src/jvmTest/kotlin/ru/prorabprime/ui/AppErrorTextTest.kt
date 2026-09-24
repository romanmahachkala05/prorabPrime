package ru.prorabprime.ui

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.PhotoRejection

class AppErrorTextTest {

    private val errors = listOf(
        AppError.Network,
        AppError.Unauthorized,
        AppError.NotFound,
        AppError.Validation(persistentMapOf()),
        AppError.PhotoRejected(PhotoRejection.UNSUPPORTED_TYPE),
        AppError.PhotoRejected(PhotoRejection.TOO_LARGE),
        AppError.Server(503),
        AppError.Unknown,
    )

    @Test
    fun `every error has its own wording`() {
        val texts = errors.map { it.toUiText() }

        assertThat(texts.distinct()).hasSize(errors.size)
    }

    @Test
    fun `a server error carries its status code`() {
        assertThat((AppError.Server(503).toUiText() as UiText.Resource).args).isEqualTo(persistentListOf<Any>(503))
    }
}
