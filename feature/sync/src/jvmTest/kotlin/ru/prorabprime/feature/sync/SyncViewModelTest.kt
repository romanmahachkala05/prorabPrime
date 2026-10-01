package ru.prorabprime.feature.sync

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.ChangeAction
import ru.prorabprime.domain.model.ChangeKind
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.domain.usecase.DiscardFailedChangeUseCase
import ru.prorabprime.domain.usecase.ObserveFailedChangesUseCase
import ru.prorabprime.domain.usecase.ObserveSyncStatusUseCase
import ru.prorabprime.domain.usecase.RetryFailedChangesUseCase
import ru.prorabprime.domain.usecase.SyncNowUseCase
import ru.prorabprime.testing.FakeSyncRepository
import ru.prorabprime.testing.MainDispatcherRule

class SyncViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSyncRepository()
    private val refused = FailedChange(7, ChangeKind.OBJECT, ChangeAction.CREATE, "Кухня", "validation")

    private val viewModel by lazy {
        SyncViewModel(
            stateHolder = SyncStateHolder(),
            observeStatus = ObserveSyncStatusUseCase(repository),
            observeFailed = ObserveFailedChangesUseCase(repository),
            syncNow = SyncNowUseCase(repository),
            retry = RetryFailedChangesUseCase(repository),
            discard = DiscardFailedChangeUseCase(repository),
        )
    }

    @Test
    fun `the state follows the status and the refused changes`() {
        repository.statusFlow.value = SyncStatus(pending = 2, offline = true)
        repository.failedFlow.value = persistentListOf(refused)

        assertThat(viewModel.state.value.status.pending).isEqualTo(2)
        assertThat(viewModel.state.value.failed).containsExactly(refused)
    }

    @Test
    fun `a tap on the chip sends now when nothing was refused`() {
        viewModel.onEvent(SyncEvent.ChipClicked)

        assertThat(repository.syncs).isEqualTo(1)
        assertThat(viewModel.state.value.sheetOpen).isFalse()
    }

    @Test
    fun `a tap on the chip opens the list when something was refused, and the list closes when it is empty`() {
        repository.failedFlow.value = persistentListOf(refused)

        viewModel.onEvent(SyncEvent.ChipClicked)
        assertThat(viewModel.state.value.sheetOpen).isTrue()
        assertThat(repository.syncs).isEqualTo(0)

        repository.failedFlow.value = persistentListOf()
        assertThat(viewModel.state.value.sheetOpen).isFalse()
    }

    @Test
    fun `retrying and discarding go to the repository`() {
        viewModel.onEvent(SyncEvent.RetryClicked(7))
        viewModel.onEvent(SyncEvent.RetryClicked(null))
        viewModel.onEvent(SyncEvent.DiscardClicked(7))

        assertThat(repository.retried).containsExactly(7L, null).inOrder()
        assertThat(repository.discarded).containsExactly(7L)
    }
}
