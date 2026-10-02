package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.AccountDto
import ru.prorabprime.contract.ContactCreatedDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.contract.ErrorCode
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.ObjectCreatedDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.contract.PhotoNoteRequestDto
import ru.prorabprime.contract.ReceiptRequestDto
import ru.prorabprime.contract.RotatePhotoRequestDto
import ru.prorabprime.contract.SetCoverRequestDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.contract.TrashDto
import ru.prorabprime.server.ApiJson
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.configure
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.di.configModule
import ru.prorabprime.server.di.databaseModule
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.LocalFileStorage
import ru.prorabprime.server.storage.TestImages
import ru.prorabprime.server.testConfig

/**
 * The heart of the accounts (ADR-0021): two accounts on one server, through the real routes, services and
 * PostgreSQL. Alice makes one of everything; Bob then asks for every one of those by its id, and must find
 * nothing, change nothing, and see nothing of it in any list.
 */
class AccountIsolationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var dataSource: HikariDataSource
    private lateinit var bobToken: String
    private lateinit var accounts: AccountService

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private inline fun <reified T> json(value: T): String = ApiJson.encodeToString(value)

    /** What Alice made: the id of each thing, and the address of the object's files. */
    private class World(
        val objectId: String,
        val contactId: String,
        val paymentId: String,
        val extraWorkId: String,
        val materialId: String,
        val taskId: String,
        val photo: PhotoDto,
        val receipt: PhotoDto,
    )

    private fun twoAccounts(
        appConfig: AppConfig = testConfig,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        environment { config = MapApplicationConfig() }
        val database = Database.connect(dataSource)
        val storage = module { single<FileStorage> { LocalFileStorage(folder.root.toPath(), Dispatchers.IO) } }
        application {
            configure(appConfig, listOf(configModule(appConfig), storage, databaseModule(database), serviceModule))
            accounts = inject<AccountService>().value
            runBlocking {
                accounts.registerEnvToken(TEST_TOKEN)
                bobToken = accounts.createUser("bob").getOrThrow().token
            }
        }
        startApplication()
        block(createClient { install(ContentNegotiation) { json(ApiJson) } })
    }

    private suspend fun HttpClient.upload(
        token: String,
        objectId: String,
        query: String = "",
    ): HttpResponse = submitFormWithBinaryData(
        url = "/api/objects/$objectId/photos$query",
        formData = formData {
            append(
                "file",
                TestImages.jpeg(800, 600),
                Headers.build {
                    append(HttpHeaders.ContentType, "image/jpeg")
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                },
            )
        },
    ) { bearerAuth(token) }

    private suspend inline fun <reified T> HttpClient.create(
        token: String,
        path: String,
        request: T,
    ): HttpResponse = post(path) {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    /** Alice's world: one of every kind of thing there is. */
    private suspend fun HttpClient.makeWorld(): World {
        val objectId = create(
            TEST_TOKEN,
            "/api/objects",
            ObjectRequestDto(title = "Кухня", address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id
        val contactId = create(
            TEST_TOKEN,
            "/api/objects/$objectId/contacts",
            ContactRequestDto(name = "Иван", phone = "+7 900 000-00-00", role = ContactRoleDto.CLIENT),
        ).body<ContactCreatedDto>().id
        val paymentId = create(
            TEST_TOKEN,
            "/api/objects/$objectId/payments",
            PaymentRequestDto(PaymentSideDto.CLIENT, 100_000, PaymentMethodDto.CASH, "2026-09-25"),
        ).body<IdDto>().id
        val extraWorkId = create(
            TEST_TOKEN,
            "/api/objects/$objectId/extra-works",
            ExtraWorkRequestDto(title = "Штробы", amountKopecks = 500_000),
        ).body<IdDto>().id
        val materialId = create(
            TEST_TOKEN,
            "/api/objects/$objectId/materials",
            MaterialRequestDto(title = "Плитка"),
        ).body<IdDto>().id
        val taskId = create(
            TEST_TOKEN,
            "/api/tasks",
            TaskRequestDto(title = "Позвонить Ивану", day = "2026-09-25"),
        ).body<IdDto>().id
        put("/api/objects/$objectId/finance/terms") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(FinanceTermsDto(clientTotalKopecks = 1_000_000))
        }
        val photo = upload(TEST_TOKEN, objectId).body<PhotoDto>()
        val receipt = upload(TEST_TOKEN, objectId, "?kind=receipt").body<PhotoDto>()
        return World(objectId, contactId, paymentId, extraWorkId, materialId, taskId, photo, receipt)
    }

    private suspend fun HttpClient.get(token: String, path: String): HttpResponse = get(path) { bearerAuth(token) }

    private suspend fun HttpClient.send(
        token: String,
        method: String,
        path: String,
        body: String? = null,
    ): HttpResponse {
        val configure: HttpRequestBuilder.() -> Unit = {
            bearerAuth(token)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (method) {
            "PUT" -> put(path, configure)
            "DELETE" -> delete(path, configure)
            "POST" -> post(path, configure)
            else -> get(path, configure)
        }
    }

    /** Every request Bob can make about something of Alice's, by its id. */
    private fun World.attacks(): List<Triple<String, String, String?>> {
        val request = json(ObjectRequestDto(title = "Моё", address = "Чужой, 1", status = ObjectStatusDto.DONE))
        return listOf(
            Triple("GET", "/api/objects/$objectId", null),
            Triple("PUT", "/api/objects/$objectId", request),
            Triple("DELETE", "/api/objects/$objectId", null),
            Triple("POST", "/api/objects/$objectId/geocode", null),
            Triple("POST", "/api/objects/$objectId/contacts", json(ContactRequestDto(name = "Чужой"))),
            Triple("PUT", "/api/contacts/$contactId", json(ContactRequestDto(name = "Чужой"))),
            Triple("DELETE", "/api/contacts/$contactId", null),
            Triple("GET", "/api/objects/$objectId/finance", null),
            Triple("PUT", "/api/objects/$objectId/finance/terms", json(FinanceTermsDto(clientTotalKopecks = 1))),
            Triple("GET", "/api/objects/$objectId/payments/history", null),
            Triple(
                "POST",
                "/api/objects/$objectId/payments",
                json(PaymentRequestDto(PaymentSideDto.CLIENT, 1, PaymentMethodDto.CASH, "2026-09-25")),
            ),
            Triple(
                "PUT",
                "/api/payments/$paymentId",
                json(PaymentRequestDto(PaymentSideDto.CLIENT, 1, PaymentMethodDto.CASH, "2026-09-25")),
            ),
            Triple("DELETE", "/api/payments/$paymentId", null),
            Triple(
                "POST",
                "/api/objects/$objectId/extra-works",
                json(ExtraWorkRequestDto(title = "Чужое", amountKopecks = 1)),
            ),
            Triple(
                "PUT",
                "/api/extra-works/$extraWorkId",
                json(ExtraWorkRequestDto(title = "Чужое", amountKopecks = 1)),
            ),
            Triple("DELETE", "/api/extra-works/$extraWorkId", null),
            Triple("GET", "/api/objects/$objectId/materials", null),
            Triple("POST", "/api/objects/$objectId/materials", json(MaterialRequestDto(title = "Чужое"))),
            Triple("POST", "/api/objects/$objectId/materials/defaults", null),
            Triple("PUT", "/api/materials/$materialId", json(MaterialRequestDto(title = "Чужое"))),
            Triple("DELETE", "/api/materials/$materialId", null),
            Triple("PUT", "/api/objects/$objectId/cover", json(SetCoverRequestDto(photo.id))),
            Triple("DELETE", "/api/photos/${photo.id}", null),
            Triple("PUT", "/api/photos/${photo.id}/note", json(PhotoNoteRequestDto("чужая заметка"))),
            Triple(
                "POST",
                "/api/photos/${photo.id}/rotate",
                json(RotatePhotoRequestDto(1, UUID.randomUUID().toString())),
            ),
            Triple("PUT", "/api/photos/${receipt.id}/receipt", json(ReceiptRequestDto(amountKopecks = 1))),
            Triple(
                "PUT",
                "/api/tasks/$taskId",
                json(TaskRequestDto(title = "Чужая", day = "2026-09-26", done = true)),
            ),
            Triple("DELETE", "/api/tasks/$taskId", null),
            Triple("GET", photo.url, null),
            Triple("GET", photo.thumbUrl, null),
            Triple("GET", receipt.url, null),
        )
    }

    private suspend fun HttpClient.snapshot(world: World): List<Any> = listOf(
        get(TEST_TOKEN, "/api/objects/${world.objectId}").body<ObjectDetailsDto>(),
        get(TEST_TOKEN, "/api/objects/${world.objectId}/finance").body<FinanceDto>(),
        get(TEST_TOKEN, "/api/tasks").body<List<TaskDto>>(),
        get(TEST_TOKEN, "/api/objects").body<List<ObjectSummaryDto>>(),
    )

    @Test
    fun `bob finds nothing of alice by any id, and changes nothing`() = twoAccounts { client ->
        val world = client.makeWorld()
        val before = client.snapshot(world)

        for ((method, path, body) in world.attacks()) {
            val response = client.send(bobToken, method, path, body)
            assertWithMessage("$method $path").that(response.status).isEqualTo(HttpStatusCode.NotFound)
        }

        assertThat(client.snapshot(world)).isEqualTo(before)
    }

    @Test
    fun `alice can still do all of it, so the refusals above are about bob`() = twoAccounts { client ->
        val world = client.makeWorld()

        for ((method, path, _) in world.attacks().filter { it.first == "GET" }) {
            assertWithMessage(
                "$method $path",
            ).that(client.send(TEST_TOKEN, method, path).status).isEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun `bob sees none of alice's things in any list or search`() = twoAccounts { client ->
        client.makeWorld()

        assertThat(client.get(bobToken, "/api/objects").body<List<ObjectSummaryDto>>()).isEmpty()
        assertThat(client.get(bobToken, "/api/objects?search=%D0%A2%D0%B2%D0%B5%D1%80").body<List<ObjectSummaryDto>>())
            .isEmpty()
        assertThat(client.get(bobToken, "/api/tasks").body<List<TaskDto>>()).isEmpty()
        assertThat(client.get(bobToken, "/api/tasks?open=true&from=2026-01-01").body<List<TaskDto>>()).isEmpty()
        val trash = client.get(bobToken, "/api/trash").body<TrashDto>()
        assertThat(trash.objects).isEmpty()
        assertThat(trash.photos).isEmpty()
    }

    @Test
    fun `what is in alice's trash is out of bob's reach`() = twoAccounts { client ->
        val world = client.makeWorld()
        client.delete("/api/photos/${world.photo.id}") { bearerAuth(TEST_TOKEN) }
        val spare = client.create(
            TEST_TOKEN,
            "/api/objects",
            ObjectRequestDto(address = "Ленина, 1", status = ObjectStatusDto.PLANNED),
        ).body<ObjectCreatedDto>().id
        client.delete("/api/objects/$spare") { bearerAuth(TEST_TOKEN) }

        val attacks = listOf(
            "POST" to "/api/trash/photos/${world.photo.id}/restore",
            "DELETE" to "/api/trash/photos/${world.photo.id}",
            "POST" to "/api/trash/objects/$spare/restore",
            "DELETE" to "/api/trash/objects/$spare",
        )
        for ((method, path) in attacks) {
            assertWithMessage("$method $path").that(client.send(bobToken, method, path).status)
                .isEqualTo(HttpStatusCode.NotFound)
        }
        // Emptying the trash is emptying one's own.
        assertThat(client.delete("/api/trash") { bearerAuth(bobToken) }.status).isEqualTo(HttpStatusCode.NoContent)

        val trash = client.get(TEST_TOKEN, "/api/trash").body<TrashDto>()
        assertThat(trash.objects.map { it.id }).containsExactly(spare)
        assertThat(trash.photos.map { it.id }).containsExactly(world.photo.id)
        // The files of what is in the trash are still served to their owner, and only to them.
        val thumb = client.get(TEST_TOKEN, world.photo.thumbUrl)
        assertThat(thumb.status).isEqualTo(HttpStatusCode.OK)
        assertThat(client.get(bobToken, world.photo.thumbUrl).status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `an id that belongs to another account is a conflict and leaves it alone`() = twoAccounts { client ->
        val world = client.makeWorld()
        val before = client.snapshot(world)
        val bobsObject = client.create(
            bobToken,
            "/api/objects",
            ObjectRequestDto(address = "Мира, 3", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id

        val taken = listOf(
            client.create(
                bobToken,
                "/api/objects",
                ObjectRequestDto(id = world.objectId, address = "Мой", status = ObjectStatusDto.DONE),
            ),
            client.create(bobToken, "/api/tasks", TaskRequestDto(title = "Моя", day = "2026-09-25", id = world.taskId)),
            client.create(
                bobToken,
                "/api/objects/$bobsObject/contacts",
                ContactRequestDto(name = "Мой", id = world.contactId),
            ),
            client.create(
                bobToken,
                "/api/objects/$bobsObject/payments",
                PaymentRequestDto(PaymentSideDto.CLIENT, 1, PaymentMethodDto.CASH, "2026-09-25", id = world.paymentId),
            ),
            client.create(
                bobToken,
                "/api/objects/$bobsObject/extra-works",
                ExtraWorkRequestDto(title = "Моё", amountKopecks = 1, id = world.extraWorkId),
            ),
            client.create(
                bobToken,
                "/api/objects/$bobsObject/materials",
                MaterialRequestDto(title = "Моё", id = world.materialId),
            ),
            client.upload(bobToken, bobsObject, "?id=${world.photo.id}"),
        )

        taken.forEachIndexed { index, response ->
            assertWithMessage("attempt $index").that(response.status).isEqualTo(HttpStatusCode.Conflict)
        }
        assertThat(client.snapshot(world)).isEqualTo(before)
        // And Bob's object is still his, untouched by what failed beside it.
        assertThat(client.get(bobToken, "/api/objects/$bobsObject").status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `each account has its own objects and tasks side by side`() = twoAccounts { client ->
        val world = client.makeWorld()
        val bobsObject = client.create(
            bobToken,
            "/api/objects",
            ObjectRequestDto(address = "Мира, 3", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id
        client.create(bobToken, "/api/tasks", TaskRequestDto(title = "Его задача", day = "2026-09-25"))

        assertThat(client.get(TEST_TOKEN, "/api/objects").body<List<ObjectSummaryDto>>().map { it.id })
            .containsExactly(world.objectId)
        assertThat(client.get(bobToken, "/api/objects").body<List<ObjectSummaryDto>>().map { it.id })
            .containsExactly(bobsObject)
        assertThat(client.get(TEST_TOKEN, "/api/tasks").body<List<TaskDto>>().map { it.title })
            .containsExactly("Позвонить Ивану")
        assertThat(client.get(bobToken, "/api/tasks").body<List<TaskDto>>().map { it.title })
            .containsExactly("Его задача")
    }

    @Test
    fun `each account is told its own name and how much of its room it has used`() = twoAccounts { client ->
        val world = client.makeWorld()

        val alice = client.get(TEST_TOKEN, "/api/account").body<AccountDto>()
        val bob = client.get(bobToken, "/api/account").body<AccountDto>()

        assertThat(alice.name).isEqualTo("owner")
        assertThat(alice.usedBytes).isGreaterThan(0L)
        assertThat(alice.limitBytes).isEqualTo(1024L * 1024 * 1024)
        assertThat(bob).isEqualTo(AccountDto("bob", 0L, 1024L * 1024 * 1024))
        // The trash keeps its files, so it keeps its room.
        client.delete("/api/photos/${world.photo.id}") { bearerAuth(TEST_TOKEN) }
        assertThat(client.get(TEST_TOKEN, "/api/account").body<AccountDto>().usedBytes).isEqualTo(alice.usedBytes)
    }

    @Test
    fun `a photo past the room of the account is refused with its own code`() = twoAccounts(
        testConfig.copy(accountQuotaBytes = 1),
    ) { client ->
        val objectId = client.create(
            TEST_TOKEN,
            "/api/objects",
            ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id

        val refused = client.upload(TEST_TOKEN, objectId)

        assertThat(refused.status).isEqualTo(HttpStatusCode.InsufficientStorage)
        assertThat(refused.body<ErrorDto>().code).isEqualTo(ErrorCode.QUOTA_EXCEEDED)
        assertThat(client.get(TEST_TOKEN, "/api/account").body<AccountDto>().usedBytes).isEqualTo(0L)
    }

    @Test
    fun `the room is counted per account`() = twoAccounts(testConfig.copy(accountQuotaBytes = 20_000)) { client ->
        val aliceObject = client.create(
            TEST_TOKEN,
            "/api/objects",
            ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id
        val bobObject = client.create(
            bobToken,
            "/api/objects",
            ObjectRequestDto(address = "Мира, 3", status = ObjectStatusDto.IN_PROGRESS),
        ).body<ObjectCreatedDto>().id

        // Fill Alice's room, whatever a photo weighs, then Bob must still be able to upload.
        while (client.upload(TEST_TOKEN, aliceObject).status == HttpStatusCode.Created) Unit

        assertThat(client.upload(TEST_TOKEN, aliceObject).status).isEqualTo(HttpStatusCode.InsufficientStorage)
        assertThat(client.upload(bobToken, bobObject).status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun `a token that was revoked opens nothing`() = twoAccounts { client ->
        assertThat(client.get(bobToken, "/api/objects").status).isEqualTo(HttpStatusCode.OK)

        runBlocking { accounts.revoke("bob").getOrThrow() }

        assertThat(client.get(bobToken, "/api/objects").status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.get(TEST_TOKEN, "/api/objects").status).isEqualTo(HttpStatusCode.OK)
    }
}
