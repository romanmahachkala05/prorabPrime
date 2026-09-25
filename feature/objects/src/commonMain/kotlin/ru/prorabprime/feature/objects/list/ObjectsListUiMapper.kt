package ru.prorabprime.feature.objects.list

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.domain.model.ObjectSummary

internal fun ObjectSummary.toCardUi() = ObjectCardUi(
    id = id.value,
    title = displayTitle,
    address = address.takeIf { title != null },
    status = status,
    photoCount = photoCount,
    cover = coverThumbPath,
)

internal fun List<ObjectSummary>.toCardsUi(): ImmutableList<ObjectCardUi> = map { it.toCardUi() }.toImmutableList()
