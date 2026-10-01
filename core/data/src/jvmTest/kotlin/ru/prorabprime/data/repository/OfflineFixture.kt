package ru.prorabprime.data.repository

import java.io.File
import ru.prorabprime.data.local.FileBlobStore
import ru.prorabprime.data.local.FilePersistence
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.data.sync.FakeServer
import ru.prorabprime.data.sync.OperationRunner
import ru.prorabprime.data.sync.SyncEngine

/** The phone's side of the app over a [FakeServer]: a copy on disk, a sync engine and the repositories. */
internal class OfflineFixture(
    folder: File,
) {
    val server = FakeServer()
    val db = LocalDb(FilePersistence(folder), FileBlobStore(File(folder, "blobs")), server.clock)
    private var counter = 0
    val ids = IdFactory { "00000000-0000-0000-0000-%012d".format(++counter) }

    private val remote = RemoteApi(server.http.client)
    val engine = SyncEngine(db, remote, OperationRunner(remote, db.blobs), { "http://server" }, server.clock)

    val objects = ObjectsRepositoryImpl(db, engine, remote, server.clock, ids)
    val photos = PhotosRepositoryImpl(db, server.clock, ids)
    val contacts = ContactsRepositoryImpl(db, ids)
    val finance = FinanceRepositoryImpl(db, ids)
    val materials = MaterialsRepositoryImpl(db, ids)
    val tasks = TasksRepositoryImpl(db, ids)
}
