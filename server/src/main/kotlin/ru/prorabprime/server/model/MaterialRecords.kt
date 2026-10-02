package ru.prorabprime.server.model

import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.contract.MaterialStatusDto

/** The editable fields of a material, already trimmed and validated. */
data class MaterialFields(
    val title: String,
    val status: MaterialStatusDto,
)

data class MaterialRecord(
    val id: UUID,
    val objectId: UUID,
    val fields: MaterialFields,
    val sortOrder: Int,
    val createdAt: Instant,
)
