package ru.prorabprime.domain.model

/**
 * What the objects list asks for. Search is case-insensitive over address and title; [statuses] keeps
 * only objects in one of them, and empty means every status.
 */
data class ObjectQuery(
    val search: String = "",
    val sort: ObjectSort = ObjectSort.DEFAULT,
    val statuses: Set<ObjectStatus> = emptySet(),
)

/** The sort options the list offers; dates always sort newest first. */
enum class ObjectSort {
    ADDRESS_ASC,
    ADDRESS_DESC,
    CREATED_NEWEST,
    UPDATED_NEWEST,
    ;

    companion object {
        val DEFAULT = UPDATED_NEWEST
    }
}
