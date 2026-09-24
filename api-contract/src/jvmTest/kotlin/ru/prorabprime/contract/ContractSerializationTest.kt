package ru.prorabprime.contract

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ContractSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a summary survives a round trip`() {
        val dto = ObjectSummaryDto(
            id = "7f1c",
            title = null,
            address = "ул. Ленина, 1",
            status = ObjectStatusDto.IN_PROGRESS,
            coverThumbUrl = "/files/7f1c/a_thumb.jpg",
            photoCount = 3,
            createdAt = Instant.parse("2026-09-25T10:00:00Z"),
            updatedAt = Instant.parse("2026-09-25T11:30:00Z"),
        )

        val decoded = json.decodeFromString<ObjectSummaryDto>(json.encodeToString(dto))

        assertThat(decoded).isEqualTo(dto)
    }

    @Test
    fun `timestamps are ISO-8601 strings on the wire`() {
        val dto = PhotoDto(
            id = "p1",
            url = "/files/o1/p1.jpg",
            thumbUrl = "/files/o1/p1_thumb.jpg",
            width = 2048,
            height = 1536,
            createdAt = Instant.parse("2026-09-25T10:00:00Z"),
        )

        val createdAt = json.parseToJsonElement(json.encodeToString(dto)).jsonObject["createdAt"]

        assertThat(createdAt?.jsonPrimitive?.content).isEqualTo("2026-09-25T10:00:00Z")
    }

    @Test
    fun `status is sent by its constant name`() {
        val body = ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.PAUSED)

        val status = json.parseToJsonElement(json.encodeToString(body)).jsonObject["status"]

        assertThat(status?.jsonPrimitive?.content).isEqualTo("PAUSED")
    }

    @Test
    fun `an error without field errors decodes with an empty list`() {
        val decoded = json.decodeFromString<ErrorDto>("""{"code":"NOT_FOUND","message":"no such object"}""")

        assertThat(decoded).isEqualTo(ErrorDto(ErrorCode.NOT_FOUND, "no such object"))
    }

    @Test
    fun `sort parameters use the documented wire names`() {
        assertThat(SortFieldDto.entries.map { it.wireName }).containsExactly("address", "created", "updated")
        assertThat(SortOrderDto.entries.map { it.wireName }).containsExactly("asc", "desc")
    }
}
