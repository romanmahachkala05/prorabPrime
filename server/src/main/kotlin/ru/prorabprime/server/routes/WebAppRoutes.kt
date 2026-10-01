package ru.prorabprime.server.routes

import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import io.ktor.util.logging.KtorSimpleLogger
import java.io.File

private val log = KtorSimpleLogger("ru.prorabprime.server.routes.WebAppRoutes")

/**
 * Serves the built web app (`./gradlew :web:wasmJsBrowserDistribution`) from [webDir] at `/`, with
 * `index.html` for the root. The page and the API share an origin, so the browser needs no CORS.
 * Nothing in these files is secret: the token is typed into the app, not baked into it.
 */
fun Route.webAppRoutes(webDir: String?) {
    if (webDir == null) return
    val directory = File(webDir)
    if (!directory.isDirectory) {
        log.warn("WEB_DIR $webDir is not a directory; the web app is not served")
        return
    }
    // `index.html` answers the root only: any other missing path stays a 404, API typos included.
    staticFiles("/", directory)
}
