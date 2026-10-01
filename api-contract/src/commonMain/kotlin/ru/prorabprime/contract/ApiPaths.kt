package ru.prorabprime.contract

/** Route templates in Ktor's `{param}` syntax; the client fills them in. */
object ApiPaths {
    const val HEALTH = "/health"
    const val OBJECTS = "/api/objects"
    const val OBJECT = "/api/objects/{id}"
    const val OBJECT_PHOTOS = "/api/objects/{id}/photos"
    const val OBJECT_COVER = "/api/objects/{id}/cover"
    const val OBJECT_GEOCODE = "/api/objects/{id}/geocode"
    const val GEOCODE_REVERSE = "/api/geocode/reverse"
    const val OBJECT_CONTACTS = "/api/objects/{id}/contacts"
    const val CONTACT = "/api/contacts/{id}"
    const val OBJECT_FINANCE = "/api/objects/{id}/finance"
    const val OBJECT_FINANCE_TERMS = "/api/objects/{id}/finance/terms"
    const val OBJECT_PAYMENTS = "/api/objects/{id}/payments"
    const val OBJECT_PAYMENT_HISTORY = "/api/objects/{id}/payments/history"
    const val PAYMENT = "/api/payments/{id}"
    const val OBJECT_EXTRA_WORKS = "/api/objects/{id}/extra-works"
    const val EXTRA_WORK = "/api/extra-works/{id}"
    const val OBJECT_MATERIALS = "/api/objects/{id}/materials"
    const val OBJECT_MATERIAL_DEFAULTS = "/api/objects/{id}/materials/defaults"
    const val MATERIAL = "/api/materials/{id}"
    const val TASKS = "/api/tasks"
    const val TASK = "/api/tasks/{id}"
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
    const val KIND = "kind"
    const val LAT = "lat"

    /** A photo upload: the id the client chose for it, so a retried upload finds the photo it made. */
    const val ID = "id"
    const val LON = "lon"

    /** Tasks: the first and last day wanted (inclusive, `yyyy-MM-dd`), and `true` to leave out the done ones. */
    const val FROM = "from"
    const val TO = "to"
    const val OPEN_ONLY = "open"
}

object ApiMultipart {
    const val FILE = "file"
}
