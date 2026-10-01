package ru.prorabprime.server.fakes

import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.service.Geocoder

/** Knows the addresses it is given; every other address is "not found". */
class FakeGeocoder(
    val known: MutableMap<String, Coordinates> = mutableMapOf(),
    val streets: MutableMap<Coordinates, String> = mutableMapOf(),
) : Geocoder {

    val asked = mutableListOf<String>()

    override suspend fun locate(address: String): Coordinates? {
        asked += address
        return known[address]
    }

    override suspend fun addressAt(point: Coordinates): String? = streets[point]
}
