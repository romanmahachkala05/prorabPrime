package ru.prorabprime.feature.objects.viewer

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface PhotoViewerStatus {
    data object Content : PhotoViewerStatus

    data object Loading : PhotoViewerStatus

    data class Error(
        val message: UiText,
    ) : PhotoViewerStatus
}

@Immutable
internal data class PhotoViewerState(
    val status: PhotoViewerStatus = PhotoViewerStatus.Loading,
    val photos: ImmutableList<ServerFilePath> = persistentListOf(),
    /** Where the pager opens: the photo that was tapped. */
    val initialPage: Int = 0,
)

internal data class PhotoViewerArgs(
    val objectId: String,
    val photoId: String,
)
