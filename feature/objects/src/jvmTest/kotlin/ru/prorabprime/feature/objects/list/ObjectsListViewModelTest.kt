package ru.prorabprime.feature.objects.list

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectSummary
import ru.prorabprime.ui.toUiText

@OptIn(ExperimentalCoroutinesApi::class)
class ObjectsListViewModelTest {

    // Standard, not unconfined: the debounce is measured in virtual time.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val objects = FakeObjectsRepository()
    private val settings = FakeSettingsRepository()
    private val notifier = FakeSnackbarNotifier()

    private val viewModel by lazy {
        val holder = ObjectsListStateHolder()
        ObjectsListViewModel(
            stateHolder = holder,
            errorHandler = ObjectsListErrorHandler(holder, notifier),
            observeObjects = ObserveObjectsUseCase(objects),
            refreshObjects = RefreshObjectsUseCase(objects),
            observeObjectSort = ObserveObjectSortUseCase(settings),
            saveObjectSort = SaveObjectSortUseCase(settings),
        )
    }

    private val state get() = viewModel.state.value

    private fun runVmTest(block: suspend kotlinx.coroutines.test.TestScope.() -> Unit) =
        runTest(mainDispatcherRule.dispatcher) {
            viewModel
            block()
        }

    @Test
    fun `the objects are shown after the initial debounce`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary(id = "a"), anObjectSummary(id = "b"))

        advanceTimeBy(301)
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Content)
        assertThat(state.items.map { it.id }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `typing queries the server once, after the pause`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.SearchChanged("л"))
        advanceTimeBy(100)
        viewModel.onEvent(ObjectsListEvent.SearchChanged("ле"))
        advanceTimeBy(100)
        viewModel.onEvent(ObjectsListEvent.SearchChanged("лен "))
        advanceTimeBy(301)
        runCurrent()

        assertThat(
            objects.queries,
        ).containsExactly(ObjectQuery("", ObjectSort.DEFAULT), ObjectQuery("лен", ObjectSort.DEFAULT))
            .inOrder()
    }

    @Test
    fun `a chosen sort is saved and queried`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.SortSelected(ObjectSort.ADDRESS_ASC))
        runCurrent()

        assertThat(settings.objectSort.value).isEqualTo(ObjectSort.ADDRESS_ASC)
        assertThat(state.sort).isEqualTo(ObjectSort.ADDRESS_ASC)
        assertThat(objects.queries.last()).isEqualTo(ObjectQuery("", ObjectSort.ADDRESS_ASC))
    }

    @Test
    fun `a failed first load takes over the screen`() = runVmTest {
        objects.loadError.value = AppError.Network

        advanceTimeBy(301)
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Error(AppError.Network.toUiText()))
    }

    @Test
    fun `a failed reload keeps the list and says so`() = runVmTest {
        objects.objects.value = listOf(anObjectSummary())
        advanceTimeBy(301)
        runCurrent()

        objects.loadError.value = AppError.Network
        runCurrent()

        assertThat(state.status).isEqualTo(ObjectsListStatus.Content)
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
    }

    @Test
    fun `pulling to refresh reloads and ends refreshing when the list arrives`() = runVmTest {
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.Refresh)
        assertThat(state.isRefreshing).isTrue()
        runCurrent()
        objects.objects.value = listOf(anObjectSummary())
        runCurrent()

        assertThat(objects.refreshCount).isEqualTo(1)
        assertThat(state.isRefreshing).isFalse()
    }

    @Test
    fun `retry shows loading and reloads`() = runVmTest {
        objects.loadError.value = AppError.Network
        advanceTimeBy(301)
        runCurrent()

        viewModel.onEvent(ObjectsListEvent.Retry)

        assertThat(state.status).isEqualTo(ObjectsListStatus.Loading)
        runCurrent()
        assertThat(objects.refreshCount).isEqualTo(1)
    }
}
