package ru.prorabprime.feature.objects.viewer

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.aPhoto
import ru.prorabprime.testing.anObjectDetails

class PhotoViewerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository().apply {
        details.value = mapOf(
            ObjectId("o1") to
                anObjectDetails(
                    id = "o1",
                    photos = persistentListOf(aPhoto("p1", "o1"), aPhoto("p2", "o1"), aPhoto("p3", "o1")),
                ),
        )
    }

    @Test
    fun `opens at the tapped photo, showing full-size pictures`() {
        val viewModel = PhotoViewerViewModel(PhotoViewerArgs("o1", "p2"), ObserveObjectUseCase(objects))

        val state = viewModel.state.value
        assertThat(state.status).isEqualTo(PhotoViewerStatus.Content)
        assertThat(state.initialPage).isEqualTo(1)
        assertThat(state.photos.first()).isEqualTo(ServerFilePath("/files/o1/p1.jpg"))
    }

    @Test
    fun `a photo that is gone opens at the first one`() {
        val viewModel = PhotoViewerViewModel(PhotoViewerArgs("o1", "missing"), ObserveObjectUseCase(objects))

        assertThat(viewModel.state.value.initialPage).isEqualTo(0)
    }
}
