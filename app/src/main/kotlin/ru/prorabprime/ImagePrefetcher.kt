package ru.prorabprime

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.request.ImageRequest
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase

/**
 * Loads the pictures of the objects ahead of time, so they open without a signal too: the small ones of every
 * object, and the full-size ones of the objects worked on most recently. The image loader keeps them in its disk
 * cache, so asking again for what is already there costs nothing.
 */
internal class ImagePrefetcher(
    private val context: PlatformContext,
    private val loader: ImageLoader,
    private val observeObjects: ObserveObjectsUseCase,
    private val observeObject: ObserveObjectUseCase,
) {
    @OptIn(FlowPreview::class)
    suspend fun run() {
        observeObjects(ObjectQuery())
            .mapNotNull { it.getOrNull() }
            .debounce(SETTLE)
            .collect { objects ->
                objects.forEachIndexed { index, summary -> prefetch(summary, index < FULL_SIZE_OBJECTS) }
            }
    }

    private suspend fun prefetch(summary: ObjectSummary, fullSize: Boolean) {
        val details = observeObject(summary.id).first().getOrNull() ?: return
        // A picture still on the phone needs no loading; one without a path has nothing to load.
        details.photos.filter { !it.isPending }.forEach { photo ->
            enqueue(photo.thumbPath)
            if (fullSize) enqueue(photo.path)
        }
    }

    private fun enqueue(path: ServerFilePath) {
        if (path.value.isNotBlank()) loader.enqueue(ImageRequest.Builder(context).data(path).build())
    }

    private companion object {
        val SETTLE = 2.seconds
        const val FULL_SIZE_OBJECTS = 10
    }
}
