package com.popclub.forgedriver.handlers

import android.app.Instrumentation
import android.graphics.Bitmap
import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * GET /screenshot
 *
 * Returns device screenshot as base64-encoded PNG.
 * Forge MCP / Forge UI polls this to show the live device screen.
 */
class ScreenshotHandler(
    private val device: UiDevice,
    private val instrumentation: Instrumentation
) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        return try {
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
                ?: return error("Screenshot failed", gson)

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            val base64 = Base64.getEncoder().encodeToString(out.toByteArray())

            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/json",
                gson.toJson(mapOf(
                    "screenshot" to base64,
                    "width"      to bitmap.width,
                    "height"     to bitmap.height
                ))
            )
        } catch (e: Exception) {
            error(e.message ?: "Screenshot error", gson)
        }
    }

    private fun error(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json",
            gson.toJson(mapOf("error" to msg))
        )
}
