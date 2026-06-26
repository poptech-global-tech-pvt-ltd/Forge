package com.popclub.forgedriver

import android.app.Instrumentation
import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.popclub.forgedriver.handlers.*
import fi.iki.elonen.NanoHTTPD

/**
 * NanoHTTPD-based HTTP server running on the device.
 * Routes incoming requests to the appropriate handler.
 *
 * Endpoints:
 *   POST /tap          — tap element by tag or x,y coordinates
 *   POST /type         — type text into focused field
 *   POST /swipe        — swipe in a direction or between points
 *   POST /key          — press back / home / enter / search
 *   GET  /present      — check if element is present (?tag=xxx)
 *   GET  /source       — full UI tree as XML string
 *   GET  /screenshot   — PNG screenshot as base64
 *   GET  /ping         — health check
 */
class ForgeDriverServer(
    port: Int,
    private val device: UiDevice,
    private val instrumentation: Instrumentation
) : NanoHTTPD(port) {

    private val gson = Gson()

    private val tapHandler        = TapHandler(device)
    private val typeHandler       = TypeHandler(device)
    private val swipeHandler      = SwipeHandler(device)
    private val keyHandler        = KeyHandler(device)
    private val findHandler       = FindHandler(device)
    private val sourceHandler     = SourceHandler(device)
    private val screenshotHandler = ScreenshotHandler(device, instrumentation)

    override fun serve(session: IHTTPSession): Response {
        val uri    = session.uri
        val method = session.method

        println("[ForgeDriver] $method $uri")

        return try {
            when {
                method == Method.POST && uri == "/tap"        -> tapHandler.handle(session, gson)
                method == Method.POST && uri == "/type"       -> typeHandler.handle(session, gson)
                method == Method.POST && uri == "/swipe"      -> swipeHandler.handle(session, gson)
                method == Method.POST && uri == "/key"        -> keyHandler.handle(session, gson)
                method == Method.GET  && uri == "/present"    -> findHandler.handle(session, gson)
                method == Method.GET  && uri == "/source"     -> sourceHandler.handle(session, gson)
                method == Method.GET  && uri == "/screenshot" -> screenshotHandler.handle(session, gson)
                method == Method.GET  && uri == "/ping"       -> ok(mapOf("status" to "ok", "port" to 6790))
                else -> error404()
            }
        } catch (e: Exception) {
            println("[ForgeDriver] ERROR $uri: ${e.message}")
            errorResponse(e.message ?: "Unknown error")
        }
    }

    // ── Response helpers ──────────────────────────────────────────────────────

    fun ok(data: Any): Response =
        newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(data))

    fun errorResponse(message: String): Response =
        newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "application/json",
            gson.toJson(mapOf("error" to message)))

    private fun error404(): Response =
        newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
            gson.toJson(mapOf("error" to "Not found")))

    // ── Shared: read POST body ────────────────────────────────────────────────

    companion object {
        fun readBody(session: IHTTPSession): String {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            val buf = ByteArray(contentLength)
            session.inputStream.read(buf, 0, contentLength)
            return String(buf)
        }
    }
}
