package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Duration.Companion.days
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
import ru.prorabprime.server.OTHER_OWNER
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.OwnerId

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
        val record = ObjectRecord(UUID.randomUUID(), TEST_OWNER, fields(address, title), null, at, at)
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

    private suspend fun addresses(query: ObjectListQuery) = repository.list(TEST_OWNER, query).map {
        it.record.fields.address
    }

    @Test
    fun `an inserted object reads back unchanged`() = runTest {
        val record = insert("Тверская, 5", title = "Кухня")

        assertThat(repository.find(TEST_OWNER, record.id)).isEqualTo(record)
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
        repository.update(TEST_OWNER, old.id, old.fields, base + 30.minutes)

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

        val items = repository.list(TEST_OWNER, ObjectListQuery(sort = SortFieldDto.ADDRESS, order = SortOrderDto.DESC))

        assertThat(items.map { it.photoCount }).containsExactly(2, 0).inOrder()
        assertThat(items.map { it.coverThumbFileName }).containsExactly("${cover}_thumb.jpg", null).inOrder()
    }

    @Test
    fun `an update changes the fields and the search text`() = runTest {
        val record = insert("Тверская, 5")

        val updated = repository.update(TEST_OWNER, record.id, fields("Арбат, 3", title = "Ванная"), base + 5.minutes)

        assertThat(updated).isTrue()
        assertThat(repository.find(TEST_OWNER, record.id)?.fields?.address).isEqualTo("Арбат, 3")
        assertThat(addresses(ObjectListQuery(search = "ванн"))).containsExactly("Арбат, 3")
        assertThat(addresses(ObjectListQuery(search = "тверск"))).isEmpty()
    }

    @Test
    fun `updating or deleting an unknown object reports false`() = runTest {
        assertThat(repository.update(TEST_OWNER, UUID.randomUUID(), fields("x"), base)).isFalse()
        assertThat(repository.delete(TEST_OWNER, UUID.randomUUID())).isFalse()
    }

    @Test
    fun `deleting an object removes it and its photos`() = runTest {
        val record = insert("Тверская, 5")
        insertPhoto(UUID.randomUUID(), record.id, sortOrder = 1)

        assertThat(repository.delete(TEST_OWNER, record.id)).isTrue()
        assertThat(repository.find(TEST_OWNER, record.id)).isNull()
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

    @Test
    fun `coordinates are saved, read back in the record, and cleared`() = runTest {
        val record = insert("Тверская, 5")
        assertThat(repository.find(TEST_OWNER, record.id)?.coordinates).isNull()

        repository.setCoordinates(TEST_OWNER, record.id, Coordinates(55.76, 37.61))
        assertThat(repository.find(TEST_OWNER, record.id)?.coordinates).isEqualTo(Coordinates(55.76, 37.61))
        assertThat(repository.list(TEST_OWNER, ObjectListQuery()).single().record.coordinates)
            .isEqualTo(Coordinates(55.76, 37.61))

        repository.setCoordinates(TEST_OWNER, record.id, null)
        assertThat(repository.find(TEST_OWNER, record.id)?.coordinates).isNull()
    }

    @Test
    fun `a trashed object is left out of the list and of find, and is found in the trash`() = runTest {
        val kept = insert("Арбат, 3")
        val gone = insert("Тверская, 5")
        val photoId = UUID.randomUUID()
        insertPhoto(photoId, gone.id, sortOrder = 1)

        assertThat(repository.trash(TEST_OWNER, gone.id, base + 5.minutes)).isTrue()

        assertThat(repository.list(TEST_OWNER, ObjectListQuery()).map { it.record.id }).containsExactly(kept.id)
        assertThat(repository.list(TEST_OWNER, ObjectListQuery(search = "Тверская"))).isEmpty()
        assertThat(repository.find(TEST_OWNER, gone.id)).isNull()
        assertThat(repository.findAny(TEST_OWNER, gone.id)).isNotNull()
        assertThat(repository.findTrashed(TEST_OWNER, gone.id)).isNotNull()
        assertThat(repository.findTrashed(TEST_OWNER, kept.id)).isNull()
        val trashed = repository.listTrashed(TEST_OWNER).single()
        assertThat(trashed.record.id).isEqualTo(gone.id)
        assertThat(trashed.deletedAt).isEqualTo(base + 5.minutes)
        assertThat(trashed.photoCount).isEqualTo(1)
    }

    @Test
    fun `trashing twice, or an unknown object, reports false, and restoring brings the object back`() = runTest {
        val record = insert("Тверская, 5")
        assertThat(repository.trash(TEST_OWNER, record.id, base)).isTrue()
        assertThat(repository.trash(TEST_OWNER, record.id, base)).isFalse()
        assertThat(repository.trash(TEST_OWNER, UUID.randomUUID(), base)).isFalse()

        assertThat(repository.restore(TEST_OWNER, record.id)).isTrue()
        assertThat(repository.restore(TEST_OWNER, record.id)).isFalse()

        assertThat(repository.find(TEST_OWNER, record.id)).isNotNull()
        assertThat(repository.listTrashed(TEST_OWNER)).isEmpty()
    }

    @Test
    fun `the trash lists the most recently deleted first`() = runTest {
        val first = insert("А")
        val second = insert("Б")
        repository.trash(TEST_OWNER, first.id, base + 1.minutes)
        repository.trash(TEST_OWNER, second.id, base + 9.minutes)

        assertThat(
            repository.listTrashed(TEST_OWNER).map {
                it.record.id
            },
        ).containsExactly(second.id, first.id).inOrder()
    }

    @Test
    fun `a trashed photo does not count in the list or come up as the photos of the object`() = runTest {
        val record = insert("Тверская, 5")
        val live = UUID.randomUUID()
        val dead = UUID.randomUUID()
        insertPhoto(live, record.id, sortOrder = 1)
        insertPhoto(dead, record.id, sortOrder = 2)
        photos.trash(TEST_OWNER, dead, base)

        assertThat(repository.list(TEST_OWNER, ObjectListQuery()).single().photoCount).isEqualTo(1)
        assertThat(photos.listByObject(record.id).map { it.id }).containsExactly(live)
    }

    private suspend fun insertFor(
        owner: OwnerId,
        address: String,
        minutes: Int = 0,
    ): ObjectRecord {
        val at = base + minutes.minutes
        val record = ObjectRecord(UUID.randomUUID(), owner, fields(address), null, at, at)
        repository.insert(record)
        return record
    }

    @Test
    fun `an account sees and touches only its own objects`() = runTest {
        val mine = insertFor(TEST_OWNER, "Тверская, 5")
        val theirs = insertFor(OTHER_OWNER, "Мира, 3")
        val changed = fields("Чужой, 1")

        assertThat(repository.list(TEST_OWNER, ObjectListQuery()).map { it.record.id }).containsExactly(mine.id)
        assertThat(repository.list(OTHER_OWNER, ObjectListQuery()).map { it.record.id }).containsExactly(theirs.id)
        assertThat(repository.list(TEST_OWNER, ObjectListQuery(search = "мира"))).isEmpty()
        assertThat(repository.find(TEST_OWNER, theirs.id)).isNull()
        assertThat(repository.findAny(TEST_OWNER, theirs.id)).isNull()
        assertThat(repository.update(TEST_OWNER, theirs.id, changed, base)).isFalse()
        assertThat(repository.trash(TEST_OWNER, theirs.id, base)).isFalse()
        assertThat(repository.delete(TEST_OWNER, theirs.id)).isFalse()
        repository.setCover(TEST_OWNER, theirs.id, null)
        repository.setCoordinates(TEST_OWNER, theirs.id, Coordinates(1.0, 2.0))
        repository.touch(TEST_OWNER, theirs.id, base + 1.days)

        assertThat(repository.find(OTHER_OWNER, theirs.id)).isEqualTo(theirs)
    }

    @Test
    fun `the trash of an account holds only its own, and others cannot restore or purge it`() = runTest {
        val mine = insertFor(TEST_OWNER, "Тверская, 5")
        val theirs = insertFor(OTHER_OWNER, "Мира, 3")
        repository.trash(TEST_OWNER, mine.id, base)
        repository.trash(OTHER_OWNER, theirs.id, base)

        assertThat(repository.listTrashed(TEST_OWNER).map { it.record.id }).containsExactly(mine.id)
        assertThat(repository.findTrashed(TEST_OWNER, theirs.id)).isNull()
        assertThat(repository.restore(TEST_OWNER, theirs.id)).isFalse()
        assertThat(repository.delete(TEST_OWNER, theirs.id)).isFalse()
        assertThat(repository.findTrashed(OTHER_OWNER, theirs.id)).isNotNull()
    }

    @Test
    fun `the expiry removes the old trash of every account and nothing else`() = runTest {
        val oldMine = insertFor(TEST_OWNER, "Тверская, 5")
        val oldTheirs = insertFor(OTHER_OWNER, "Мира, 3")
        val recent = insertFor(TEST_OWNER, "Ленина, 1")
        val live = insertFor(OTHER_OWNER, "Арбат, 2")
        repository.trash(TEST_OWNER, oldMine.id, base)
        repository.trash(OTHER_OWNER, oldTheirs.id, base)
        repository.trash(TEST_OWNER, recent.id, base + 10.days)

        val removed = repository.deleteTrashedBefore(base + 5.days)

        assertThat(removed).containsExactly(oldMine.id, oldTheirs.id)
        assertThat(repository.findAny(TEST_OWNER, oldMine.id)).isNull()
        assertThat(repository.findAny(OTHER_OWNER, oldTheirs.id)).isNull()
        assertThat(repository.findTrashed(TEST_OWNER, recent.id)).isNotNull()
        assertThat(repository.find(OTHER_OWNER, live.id)).isNotNull()
    }
}
