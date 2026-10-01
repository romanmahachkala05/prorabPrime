package ru.prorabprime.data.sync

import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlin.time.Instant
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.SideSummaryDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.network.ApiJson

/** A server with one object, whose answers the test can change between syncs. */
class FakeServer {
    val at: Instant = Instant.parse("2026-10-01T10:00:00Z")
    val later: Instant = Instant.parse("2026-10-01T11:00:00Z")
    val objectId = "11111111-1111-1111-1111-111111111111"

    var details = ObjectDetailsDto(
        id = objectId,
        title = "Кухня",
        address = "Ленина, 1",
        status = ObjectStatusDto.IN_PROGRESS,
        clientPhone = "+7 900",
        photos = emptyList(),
        createdAt = at,
        updatedAt = at,
    )
    var objects: List<ObjectDetailsDto> = listOf(details)
    var tasks = listOf(TaskDto("t1", "Позвонить", "2026-10-01"))
    var materials = listOf(MaterialDto("m1", "Ламинат", MaterialStatusDto.CHOSEN))

    /** No signal at all. */
    var offline = false

    /** The status every write gets, when set. */
    var writeStatus: HttpStatusCode? = null

    private val finance = FinanceDto(
        terms = FinanceTermsDto(clientTotalKopecks = 1_000),
        client = SideSummaryDto(agreedKopecks = 1_000, paidKopecks = 0, remainingKopecks = 1_000),
        crew = SideSummaryDto(paidKopecks = 0),
        extrasAgreedKopecks = 0,
        extrasPendingKopecks = 0,
        payments = emptyList(),
        extraWorks = emptyList(),
    )

    val http = TestHttp { request -> answer(request) }

    val writes get() = http.requests.filter { it.method != HttpMethod.Get }

    fun summaries() = objects.map {
        ObjectSummaryDto(
            id = it.id,
            title = it.title,
            address = it.address,
            status = it.status,
            photoCount = 0,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt,
        )
    }

    private fun <T> MockRequestHandleScope.body(serializer: KSerializer<T>, value: T) =
        json(ApiJson.encodeToString(serializer, value))

    private fun MockRequestHandleScope.answer(request: HttpRequestData): HttpResponseData {
        if (offline) throw IOException("no signal")
        if (request.method != HttpMethod.Get) return respond("", writeStatus ?: HttpStatusCode.NoContent)
        val path = request.url.encodedPath
        return when {
            path == "/api/objects" -> body(ListSerializer(ObjectSummaryDto.serializer()), summaries())

            path.startsWith("/api/objects/") && path.count { it == '/' } == 3 ->
                objects.find { path.endsWith(it.id) }
                    ?.let { body(ObjectDetailsDto.serializer(), it) }
                    ?: respond("", HttpStatusCode.NotFound)

            path.endsWith("/finance") -> body(FinanceDto.serializer(), finance)

            path.endsWith("/payments/history") -> json("[]")

            path.endsWith("/materials") -> body(ListSerializer(MaterialDto.serializer()), materials)

            path == "/api/tasks" -> body(ListSerializer(TaskDto.serializer()), tasks)

            else -> respond("", HttpStatusCode.NotFound)
        }
    }
}
