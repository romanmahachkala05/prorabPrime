package ru.prorabprime.domain.model

/** What the objects list asks the server for. Search is case-insensitive over address and title. */
data class ObjectQuery(
    val search: String = "",
    val sort: ObjectSort = ObjectSort.DEFAULT,
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
