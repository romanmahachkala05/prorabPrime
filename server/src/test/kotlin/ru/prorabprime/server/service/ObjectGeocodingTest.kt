package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeGeocoder
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.model.Coordinates

class ObjectGeocodingTest {

    private val objects = FakeObjectRepository(FakePhotoRepository())
    private val geocoder = FakeGeocoder(
        mutableMapOf(
            "Тверская, 5" to Coordinates(55.76, 37.61),
            "Арбат, 3" to Coordinates(55.75, 37.59),
        ),
    )
    private val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val service = ObjectService(
        objects,
        FakePhotoRepository(),
        FakeContactRepository(),
        FixedClock(),
        geocoder,
        newId = { id },
    )

    private fun request(address: String) = ObjectRequestDto(address = address, status = ObjectStatusDto.IN_PROGRESS)

    @Test
    fun `a new object is put on the map by its address`() = runTest {
        service.create(request("Тверская, 5")).getOrThrow()

        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(55.76, 37.61))
        assertThat(geocoder.asked).containsExactly("Тверская, 5")
    }

    @Test
    fun `a point picked on the map is kept as it is, without asking the geocoder`() = runTest {
        service.create(request("Тверская, 5").copy(latitude = 56.0, longitude = 60.0)).getOrThrow()

        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(56.0, 60.0))
        assertThat(geocoder.asked).isEmpty()

        service.update(id, request("Арбат, 3").copy(latitude = 57.0, longitude = 61.0)).getOrThrow()
        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(57.0, 61.0))
        assertThat(geocoder.asked).isEmpty()
    }

    @Test
    fun `half a point or one off the globe is ignored and the address decides`() = runTest {
        service.create(request("Тверская, 5").copy(latitude = 56.0)).getOrThrow()
        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(55.76, 37.61))

        service.update(id, request("Арбат, 3").copy(latitude = 95.0, longitude = 60.0)).getOrThrow()
        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(55.75, 37.59))
    }

    @Test
    fun `saving again with the same address and no point leaves the pin alone`() = runTest {
        service.create(request("Тверская, 5").copy(latitude = 56.0, longitude = 60.0)).getOrThrow()

        service.update(id, request("Тверская, 5")).getOrThrow()

        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(56.0, 60.0))
    }

    @Test
    fun `an address nobody knows is saved without a pin, not refused`() = runTest {
        val created = service.create(request("Деревня Гадюкино")).getOrThrow()

        assertThat(created.id).isEqualTo(id)
        assertThat(objects.records.getValue(id).coordinates).isNull()
    }

    @Test
    fun `a changed address moves the pin, and an unknown one removes it`() = runTest {
        service.create(request("Тверская, 5")).getOrThrow()

        service.update(id, request("Арбат, 3")).getOrThrow()
        assertThat(objects.records.getValue(id).coordinates).isEqualTo(Coordinates(55.75, 37.59))

        service.update(id, request("Деревня Гадюкино")).getOrThrow()
        assertThat(objects.records.getValue(id).coordinates).isNull()
    }

    @Test
    fun `an edit that keeps the address does not ask the geocoder again`() = runTest {
        service.create(request("Тверская, 5")).getOrThrow()
        geocoder.asked.clear()

        service.update(id, request("Тверская, 5").copy(title = "Кухня")).getOrThrow()

        assertThat(geocoder.asked).isEmpty()
        assertThat(objects.records.getValue(id).coordinates).isNotNull()
    }

    @Test
    fun `asking again finds an address the geocoder learned since`() = runTest {
        service.create(request("Деревня Гадюкино")).getOrThrow()
        geocoder.known["Деревня Гадюкино"] = Coordinates(60.0, 30.0)

        val details = service.geocode(id).getOrThrow()

        assertThat(details.record.coordinates).isEqualTo(Coordinates(60.0, 30.0))
    }

    @Test
    fun `an unknown object cannot be geocoded`() = runTest {
        assertThat(service.geocode(UUID.randomUUID()).isFailure).isTrue()
    }
}
