package ru.prorabprime.web

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.map.Mapper
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.Options
import io.ktor.client.HttpClient
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.Koin
import org.koin.core.context.startKoin
import ru.prorabprime.data.di.webDataModule
import ru.prorabprime.data.network.toRequestUrl
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.shared.App
import ru.prorabprime.shared.appModules

/**
 * Starts Koin with the shared module list plus the browser's data module, and hands the page to
 * Compose: composition-root work that belongs to no feature. The server serves this page, so the
 * server the app talks to is the address it came from; the token is typed into the settings once.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koin = startKoin {
        modules(appModules + webDataModule(ServerSettings(window.location.origin, "")))
    }.koin
    ComposeViewport(document.body!!) {
        WebApp(koin)
    }
}

@Composable
private fun WebApp(koin: Koin) {
    // Images load through the app's own HttpClient, as on Android (ADR-0003, ADR-0008).
    setSingletonImageLoaderFactory { context -> imageLoader(context, koin) }
    App()
}

private fun imageLoader(context: coil3.PlatformContext, koin: Koin): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(ServerFileMapper())
        add(KtorNetworkFetcherFactory(httpClient = { koin.get<HttpClient>() }))
    }
    .build()

private class ServerFileMapper : Mapper<ServerFilePath, String> {
    override fun map(data: ServerFilePath, options: Options): String = data.toRequestUrl()
}
