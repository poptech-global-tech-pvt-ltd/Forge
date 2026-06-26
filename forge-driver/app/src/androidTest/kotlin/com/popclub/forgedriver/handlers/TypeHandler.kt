package com.popclub.forgedriver.handlers

import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.popclub.forgedriver.ForgeDriverServer
import fi.iki.elonen.NanoHTTPD

/**
 * POST /type
 * Body: { "text": "hello world" }
 *
 * Types text into the currently focused field using UiDevice.
 */
class TypeHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        val body   = ForgeDriverServer.readBody(session)
        val params = gson.fromJson(body, Map::class.java)
        val text   = params["text"] as? String
            ?: return error("text field required", gson)

        device.setOrientationNatural()  // ensure keyboard appears correctly
        device.waitForIdle(1_000)

        // Clear first if requested
        val clear = params["clear"] as? Boolean ?: false
        if (clear) {
            repeat(50) { device.pressDelete() }
        }

        device.typeText(text)
        println("[Type] ✓ typed \"$text\"")
        return ok(mapOf("typed" to text), gson)
    }

    private fun ok(data: Any, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            "application/json", gson.toJson(data))

    private fun error(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json", gson.toJson(mapOf("error" to msg)))
}
