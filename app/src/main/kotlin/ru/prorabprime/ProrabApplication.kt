package ru.prorabprime

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import ru.prorabprime.data.di.androidDataModule
import ru.prorabprime.data.network.toRequestUrl
import ru.prorabprime.data.sync.AndroidConnectivityMonitor
import ru.prorabprime.data.sync.BackgroundSync
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.usecase.KeepRemindersUseCase
import ru.prorabprime.domain.usecase.ObserveSyncStatusUseCase
import ru.prorabprime.domain.usecase.SyncNowUseCase
import ru.prorabprime.shared.appModules

/**
 * Starts Koin with the shared module list plus the Android data module, and builds Coil's
 * singleton loader: composition-root work that belongs to no feature.
 */
class ProrabApplication :
    Application(),
    SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        val defaults = ServerSettings(BuildConfig.DEFAULT_SERVER_URL, BuildConfig.DEFAULT_API_TOKEN)
        startKoin {
            // Debug builds only: the logger reflects on every definition it prints.
            if (BuildConfig.DEBUG) androidLogger()
            androidContext(this@ProrabApplication)
            modules(appModules + androidDataModule(defaults))
        }
        keepReminders()
        keepInSync()
        prefetchImages()
    }

    /**
     * Keeps the phone's alarms in step with the open tasks for as long as the process lives. A
     * reboot restarts the process through the boot receiver, which sets the remembered alarms
     * again at once, so the alarms do not wait for the server to answer.
     */
    private fun keepReminders() {
        val keep = get<KeepRemindersUseCase>()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { keep.run() }
    }

    /**
     * What the phone made without a signal goes up when one comes: at once if the app is open (the network
     * coming back is noticed), and by the system's job scheduler if the app has been closed by then.
     */
    private fun keepInSync() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val syncNow = get<SyncNowUseCase>()
        AndroidConnectivityMonitor(this) { scope.launch { syncNow() } }.start()
        scope.launch {
            get<ObserveSyncStatusUseCase>()()
                .map { it.pending > 0 }
                .distinctUntilChanged()
                .filter { it }
                .collect { BackgroundSync.schedule(this@ProrabApplication) }
        }
    }

    /** The pictures come down in the background, so the card of an object opens with them without a signal. */
    private fun prefetchImages() {
        val prefetcher = ImagePrefetcher(this, SingletonImageLoader.get(this), get(), get())
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { prefetcher.run() }
    }

    /**
     * Images load through the app's own HttpClient, so they get the server address and token
     * the same way the API does (ADR-0003); [ServerFileMapper] turns a domain path into the
     * placeholder URL that client resolves (ADR-0008).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(ServerFileMapper())
            add(KtorNetworkFetcherFactory(httpClient = { get<HttpClient>() }))
        }
        // In the app's own files, not the cache folder the system may empty: what was seen once stays for the
        // times there is no signal. The server marks its files as never changing, so they are kept for good.
        .diskCache {
            DiskCache.Builder()
                .directory(filesDir.resolve("image-cache").absolutePath.toPath())
                .maxSizeBytes(IMAGE_CACHE_BYTES)
                .build()
        }
        .build()
}

private const val IMAGE_CACHE_BYTES = 1024L * 1024 * 1024

private class ServerFileMapper : Mapper<ServerFilePath, String> {
    override fun map(data: ServerFilePath, options: Options): String = data.toRequestUrl()
}
