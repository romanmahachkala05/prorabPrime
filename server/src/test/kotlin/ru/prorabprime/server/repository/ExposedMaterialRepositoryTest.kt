package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord

class ExposedMaterialRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var objects: ExposedObjectRepository
    private lateinit var materials: ExposedMaterialRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")
    private val objectId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        objects = ExposedObjectRepository(db)
        materials = ExposedMaterialRepository(db)
        objects.insert(
            ObjectRecord(
                id = objectId,
                fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
                coverPhotoId = null,
                createdAt = base,
                updatedAt = base,
            ),
        )
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun material(order: Int, title: String = "Плитка") = MaterialRecord(
        UUID.randomUUID(),
        objectId,
        MaterialFields(title, MaterialStatusDto.NOT_CHOSEN),
        order,
        base,
    )

    @Test
    fun `materials read back unchanged and list in checklist order`() = runTest {
        val second = material(2, "Ламинат")
        val first = material(1)
        materials.insert(second)
        materials.insert(first)

        assertThat(materials.find(first.id)).isEqualTo(first)
        assertThat(materials.listByObject(objectId)).containsExactly(first, second).inOrder()
    }

    @Test
    fun `updating and deleting report whether there was a material`() = runTest {
        val material = material(1)
        materials.insert(material)

        val changed = MaterialFields("Плитка 60х60", MaterialStatusDto.IN_APARTMENT)
        assertThat(materials.update(material.id, changed)).isTrue()
        assertThat(materials.find(material.id)?.fields).isEqualTo(changed)
        assertThat(materials.update(UUID.randomUUID(), changed)).isFalse()
        assertThat(materials.delete(material.id)).isTrue()
        assertThat(materials.delete(material.id)).isFalse()
    }

    @Test
    fun `the next sort order follows the highest, and the object takes its materials with it`() = runTest {
        assertThat(materials.nextSortOrder(objectId)).isEqualTo(1)
        materials.insert(material(7))
        assertThat(materials.nextSortOrder(objectId)).isEqualTo(8)

        objects.delete(objectId)

        assertThat(materials.listByObject(objectId)).isEmpty()
    }
}
