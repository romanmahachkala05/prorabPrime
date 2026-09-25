package ru.prorabprime

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import io.ktor.client.HttpClient
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import ru.prorabprime.data.di.androidDataModule
import ru.prorabprime.data.network.toRequestUrl
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings
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
        .build()
}

private class ServerFileMapper : Mapper<ServerFilePath, String> {
    override fun map(data: ServerFilePath, options: Options): String = data.toRequestUrl()
}
