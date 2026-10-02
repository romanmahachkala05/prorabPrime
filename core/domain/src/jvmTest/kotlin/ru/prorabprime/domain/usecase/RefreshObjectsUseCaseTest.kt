package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.testing.FakeObjectsRepository

class RefreshObjectsUseCaseTest {

    @Test
    fun `asks the repository to reload`() = runTest {
        val repository = FakeObjectsRepository()

        RefreshObjectsUseCase(repository)()

        assertThat(repository.refreshCount).isEqualTo(1)
    }
}
