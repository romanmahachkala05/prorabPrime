package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.MaterialLimits
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeMaterialRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock

class MaterialServiceTest {

    private val objects = FakeObjectRepository(FakePhotoRepository())
    private val repository = FakeMaterialRepository()
    private val clock = FixedClock()
    private val objectService =
        ObjectService(objects, FakePhotoRepository(), FakeContactRepository(), FakeFileStorage(), clock)
    private val service = MaterialService(objects, repository, clock)

    private suspend fun anObject(): UUID = objectService
        .create(ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS))
        .getOrThrow().id

    private fun Result<*>.error() = (exceptionOrNull() as? ServiceException)?.error

    @Test
    fun `a material is trimmed, starts not chosen and goes to the end of the list`() = runTest {
        val id = anObject()

        service.create(id, MaterialRequestDto(" Плитка ")).getOrThrow()
        val second = service.create(id, MaterialRequestDto("Ламинат", MaterialStatusDto.CHOSEN)).getOrThrow()

        val list = service.list(id).getOrThrow()
        assertThat(list.map { it.fields.title }).containsExactly("Плитка", "Ламинат").inOrder()
        assertThat(list.first().fields.status).isEqualTo(MaterialStatusDto.NOT_CHOSEN)
        assertThat(second.sortOrder).isEqualTo(2)
    }

    @Test
    fun `a blank or oversized title is rejected`() = runTest {
        val id = anObject()

        val blank = service.create(id, MaterialRequestDto("  ")).error() as ServiceError.Validation
        val long = service.create(id, MaterialRequestDto("я".repeat(MaterialLimits.TITLE + 1))).error()
            as ServiceError.Validation

        assertThat(blank.fieldErrors.map { it.field to it.problem })
            .containsExactly(ObjectFieldDto.MATERIAL_TITLE to FieldProblemDto.REQUIRED)
        assertThat(long.fieldErrors.map { it.problem }).containsExactly(FieldProblemDto.TOO_LONG)
    }

    @Test
    fun `the status is changed through an update, and a deletion removes the material`() = runTest {
        val id = anObject()
        val material = service.create(id, MaterialRequestDto("Плитка")).getOrThrow()

        service.update(material.id, MaterialRequestDto("Плитка", MaterialStatusDto.IN_APARTMENT)).getOrThrow()
        assertThat(repository.records.getValue(material.id).fields.status).isEqualTo(MaterialStatusDto.IN_APARTMENT)

        service.delete(material.id).getOrThrow()
        assertThat(repository.records).isEmpty()
        assertThat(service.delete(material.id).error()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `the defaults fill an empty list and never duplicate a title already there`() = runTest {
        val id = anObject()
        service.create(id, MaterialRequestDto("плитка", MaterialStatusDto.CHOSEN)).getOrThrow()

        val list = service.addDefaults(id).getOrThrow()

        assertThat(list).hasSize(DEFAULT_MATERIALS.size)
        assertThat(list.count { it.fields.title.equals("плитка", ignoreCase = true) }).isEqualTo(1)
        assertThat(list.first().fields.status).isEqualTo(MaterialStatusDto.CHOSEN)
        assertThat(list.map { it.sortOrder }).isInOrder()
        assertThat(service.addDefaults(id).getOrThrow()).hasSize(DEFAULT_MATERIALS.size)
    }

    @Test
    fun `an unknown object has no checklist`() = runTest {
        val missing = UUID.randomUUID()

        assertThat(service.list(missing).error()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.addDefaults(missing).error()).isInstanceOf(ServiceError.NotFound::class.java)
        assertThat(service.create(missing, MaterialRequestDto("x")).error())
            .isInstanceOf(ServiceError.NotFound::class.java)
    }
}
