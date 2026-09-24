package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.SortFieldDto
import ru.prorabprime.contract.SortOrderDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord

class ExposedObjectRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: ExposedObjectRepository
    private lateinit var photos: ExposedPhotoRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        repository = ExposedObjectRepository(db)
        photos = ExposedPhotoRepository(db)
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun fields(address: String, title: String? = null) = ObjectFields(
        title = title,
        address = address,
        status = ObjectStatusDto.IN_PROGRESS,
        clientName = "Иван",
        clientPhone = "+7 900 000-00-00",
        notes = null,
    )

    private suspend fun insert(
        address: String,
        title: String? = null,
        minutes: Int = 0,
    ): ObjectRecord {
        val at = base + minutes.minutes
        val record = ObjectRecord(UUID.randomUUID(), fields(address, title), null, at, at)
        repository.insert(record)
        return record
    }

    private fun insertPhoto(
        photoId: UUID,
        objectId: UUID,
        sortOrder: Int,
    ) = dataSource.connection.use {
        it.createStatement().execute(
            "INSERT INTO photos (id, object_id, file_name, thumb_file_name, content_type, size_bytes, " +
                "width, height, sort_order, created_at) VALUES ('$photoId', '$objectId', '$photoId.jpg', " +
                "'${photoId}_thumb.jpg', 'image/jpeg', 10, 4, 3, $sortOrder, now())",
        )
    }

    private fun setCover(objectId: UUID, photoId: UUID) = dataSource.connection.use {
        it.createStatement().execute("UPDATE objects SET cover_photo_id = '$photoId' WHERE id = '$objectId'")
    }

    private suspend fun addresses(query: ObjectListQuery) = repository.list(query).map { it.record.fields.address }

    @Test
    fun `an inserted object reads back unchanged`() = runTest {
        val record = insert("Тверская, 5", title = "Кухня")

        assertThat(repository.find(record.id)).isEqualTo(record)
    }

    @Test
    fun `search is case-insensitive over address and title, Cyrillic included`() = runTest {
        insert("ул. Ленина, 1")
        insert("Тверская, 5", title = "Кухня у Лены")
        insert("Арбат, 3")

        assertThat(addresses(ObjectListQuery(search = "ЛЕН"))).containsExactly("ул. Ленина, 1", "Тверская, 5")
    }

    @Test
    fun `search treats LIKE wildcards literally`() = runTest {
        insert("Офис 100%")
        insert("Офис 1000")

        assertThat(addresses(ObjectListQuery(search = "100%"))).containsExactly("Офис 100%")
    }

    @Test
    fun `sorts by address both ways`() = runTest {
        insert("Б")
        insert("В")
        insert("А")

        assertThat(addresses(ObjectListQuery(sort = SortFieldDto.ADDRESS, order = SortOrderDto.ASC)))
            .containsExactly("А", "Б", "В").inOrder()
        assertThat(addresses(ObjectListQuery(sort = SortFieldDto.ADDRESS, order = SortOrderDto.DESC)))
            .containsExactly("В", "Б", "А").inOrder()
    }

    @Test
    fun `sorts by creation and by last update, newest first`() = runTest {
        val old = insert("старый", minutes = 0)
        insert("средний", minutes = 10)
        insert("новый", minutes = 20)
        repository.update(old.id, old.fields, base + 30.minutes)

        assertThat(addresses(ObjectListQuery(sort = SortFieldDto.CREATED, order = SortOrderDto.DESC)))
            .containsExactly("новый", "средний", "старый").inOrder()
        assertThat(addresses(ObjectListQuery(sort = SortFieldDto.UPDATED, order = SortOrderDto.DESC)))
            .containsExactly("старый", "новый", "средний").inOrder()
    }

    @Test
    fun `the list counts photos and names the cover thumbnail`() = runTest {
        val withPhotos = insert("с фото")
        insert("без фото")
        val cover = UUID.randomUUID()
        insertPhoto(cover, withPhotos.id, sortOrder = 1)
        insertPhoto(UUID.randomUUID(), withPhotos.id, sortOrder = 2)
        setCover(withPhotos.id, cover)

        val items = repository.list(ObjectListQuery(sort = SortFieldDto.ADDRESS, order = SortOrderDto.DESC))

        assertThat(items.map { it.photoCount }).containsExactly(2, 0).inOrder()
        assertThat(items.map { it.coverThumbFileName }).containsExactly("${cover}_thumb.jpg", null).inOrder()
    }

    @Test
    fun `an update changes the fields and the search text`() = runTest {
        val record = insert("Тверская, 5")

        val updated = repository.update(record.id, fields("Арбат, 3", title = "Ванная"), base + 5.minutes)

        assertThat(updated).isTrue()
        assertThat(repository.find(record.id)?.fields?.address).isEqualTo("Арбат, 3")
        assertThat(addresses(ObjectListQuery(search = "ванн"))).containsExactly("Арбат, 3")
        assertThat(addresses(ObjectListQuery(search = "тверск"))).isEmpty()
    }

    @Test
    fun `updating or deleting an unknown object reports false`() = runTest {
        assertThat(repository.update(UUID.randomUUID(), fields("x"), base)).isFalse()
        assertThat(repository.delete(UUID.randomUUID())).isFalse()
    }

    @Test
    fun `deleting an object removes it and its photos`() = runTest {
        val record = insert("Тверская, 5")
        insertPhoto(UUID.randomUUID(), record.id, sortOrder = 1)

        assertThat(repository.delete(record.id)).isTrue()
        assertThat(repository.find(record.id)).isNull()
        assertThat(photos.listByObject(record.id)).isEmpty()
    }

    @Test
    fun `photos are listed in carousel order`() = runTest {
        val record = insert("Тверская, 5")
        val second = UUID.randomUUID()
        val first = UUID.randomUUID()
        insertPhoto(second, record.id, sortOrder = 2)
        insertPhoto(first, record.id, sortOrder = 1)

        assertThat(photos.listByObject(record.id).map { it.id }).containsExactly(first, second).inOrder()
    }
}
