package ru.prorabprime.contract

/** Route templates in Ktor's `{param}` syntax; the client fills them in. */
object ApiPaths {
    const val HEALTH = "/health"
    const val OBJECTS = "/api/objects"
    const val OBJECT = "/api/objects/{id}"
    const val OBJECT_PHOTOS = "/api/objects/{id}/photos"
    const val OBJECT_COVER = "/api/objects/{id}/cover"
    const val PHOTO = "/api/photos/{id}"
    const val FILE = "/files/{objectId}/{fileName}"
}

object ApiParams {
    const val ID = "id"
    const val OBJECT_ID = "objectId"
    const val FILE_NAME = "fileName"
}

object ApiQuery {
    const val SEARCH = "search"
    const val SORT = "sort"
    const val ORDER = "order"
}

object ApiMultipart {
    const val FILE = "file"
}
