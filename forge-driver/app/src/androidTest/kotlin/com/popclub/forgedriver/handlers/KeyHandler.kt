package com.popclub.forgedriver.handlers

import android.view.KeyEvent
import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.popclub.forgedriver.ForgeDriverServer
import fi.iki.elonen.NanoHTTPD

/**
 * POST /key
 * Body: { "key": "back" }
 * Keys: back, home, enter, search, tab, delete, recent
 */
class KeyHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        val body   = ForgeDriverServer.readBody(session)
        val params = gson.fromJson(body, Map::class.java)
        val key    = (params["key"] as? String)?.lowercase()
            ?: return error("key field required", gson)

        val pressed = when (key) {
            "back"   -> device.pressBack()
            "home"   -> device.pressHome()
            "enter"  -> device.pressEnter()
            "search" -> device.pressKeyCode(KeyEvent.KEYCODE_SEARCH)
            "tab"    -> device.pressKeyCode(KeyEvent.KEYCODE_TAB)
            "delete" -> device.pressDelete()
            "recent" -> device.pressRecentApps()
            else     -> return error("Unknown key: $key", gson)
        }

        println("[Key] ✓ pressed $key")
        return ok(mapOf("key" to key, "pressed" to pressed), gson)
    }

    private fun ok(data: Any, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            "application/json", gson.toJson(data))

    private fun error(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json", gson.toJson(mapOf("error" to msg)))
}
