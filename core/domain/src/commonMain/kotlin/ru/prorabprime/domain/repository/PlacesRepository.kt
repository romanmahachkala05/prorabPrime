package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.GeoPoint

interface PlacesRepository {
    /** The street address at a point of the map; success with null when nothing is known there. */
    suspend fun addressAt(point: GeoPoint): Result<String?>
}
