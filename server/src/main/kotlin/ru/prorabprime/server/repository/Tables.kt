package ru.prorabprime.server.repository

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestamp

/** Mirrors `V1__init.sql` and later migrations; Flyway owns the schema, these only read and write it. */
object ObjectsTable : Table("objects") {
    val id = javaUUID("id")
    val title = varchar("title", 200).nullable()
    val address = varchar("address", 500)
    val status = varchar("status", 20)
    val clientName = varchar("client_name", 200).nullable()
    val clientPhone = varchar("client_phone", 50).nullable()
    val notes = text("notes").nullable()
    val coverPhotoId = javaUUID("cover_photo_id").nullable()
    val searchText = text("search_text")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object PhotosTable : Table("photos") {
    val id = javaUUID("id")
    val objectId = javaUUID("object_id")
    val fileName = varchar("file_name", 255)
    val thumbFileName = varchar("thumb_file_name", 255)
    val contentType = varchar("content_type", 100)
    val sizeBytes = long("size_bytes")
    val width = integer("width")
    val height = integer("height")
    val sortOrder = integer("sort_order")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
