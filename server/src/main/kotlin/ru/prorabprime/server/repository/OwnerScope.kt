package ru.prorabprime.server.repository

import java.util.UUID
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.select
import ru.prorabprime.server.model.OwnerId

/**
 * Keeps only the rows of objects that belong to [owner], live or in the trash. How everything that hangs
 * on an object (photos, contacts, payments, ...) is filtered by whose it is (ADR-0021).
 */
internal fun Column<UUID>.ownedBy(owner: OwnerId): Op<Boolean> =
    this inSubQuery ObjectsTable.select(ObjectsTable.id).where { ObjectsTable.ownerId eq owner.value }
