package ru.prorabprime.ui

import androidx.lifecycle.ViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LaunchCatchingTest {

    private class TestViewModel : ViewModel()

    private val failures = mutableListOf<Throwable>()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a failure is reported instead of crashing`() {
        TestViewModel().launchCatching(onFailure = { failures += it }) { error("boom") }

        assertThat(failures.single()).hasMessageThat().isEqualTo("boom")
    }

    @Test
    fun `cancellation is not reported as a failure`() {
        val job = TestViewModel().launchCatching(onFailure = { failures += it }) {
            throw CancellationException("left the screen")
        }

        assertThat(job.isCancelled).isTrue()
        assertThat(failures).isEmpty()
    }
}
