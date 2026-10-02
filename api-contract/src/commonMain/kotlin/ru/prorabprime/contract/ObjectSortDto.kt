package ru.prorabprime.contract

/** Values of the `sort` query parameter. */
enum class SortFieldDto(
    val wireName: String,
) {
    ADDRESS("address"),
    CREATED("created"),
    UPDATED("updated"),
}

/** Values of the `order` query parameter. */
enum class SortOrderDto(
    val wireName: String,
) {
    ASC("asc"),
    DESC("desc"),
}
