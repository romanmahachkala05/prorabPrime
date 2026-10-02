package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.testing.FakeSettingsRepository

class ObjectSortUseCasesTest {

    private val repository = FakeSettingsRepository()

    @Test
    fun `the sort defaults to most recently updated first`() = runTest {
        assertThat(ObserveObjectSortUseCase(repository)().first()).isEqualTo(ObjectSort.UPDATED_NEWEST)
    }

    @Test
    fun `a saved sort is what is observed next`() = runTest {
        SaveObjectSortUseCase(repository)(ObjectSort.ADDRESS_DESC)

        assertThat(ObserveObjectSortUseCase(repository)().first()).isEqualTo(ObjectSort.ADDRESS_DESC)
    }
}
