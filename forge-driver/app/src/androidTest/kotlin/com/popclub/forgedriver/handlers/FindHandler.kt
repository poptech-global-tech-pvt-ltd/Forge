package com.popclub.forgedriver.handlers

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD

/**
 * GET /present?tag=shop_add_to_cart_button&timeout=3000
 *
 * Returns:
 *   { "present": true,  "bounds": {"left":0,"top":0,"right":100,"bottom":50} }
 *   { "present": false }
 *
 * This is the key speed win: no Appium HTTP hop, direct UiAutomator2 query.
 * Replaces the Selenium polling loop with a single blocking call.
 */
class FindHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        val params  = session.parameters
        val tag     = params["tag"]?.firstOrNull()
            ?: return error("tag param required", gson)
        val timeout = params["timeout"]?.firstOrNull()?.toLongOrNull() ?: 0L

        // Try accessibility ID first (resource-id or content-desc)
        val selector = By.res(tag).or(By.desc(tag))

        val element = if (timeout > 0) {
            device.wait(Until.findObject(selector), timeout)
        } else {
            device.findObject(selector)
        }

        return if (element != null) {
            val bounds = element.visibleBounds
            ok(mapOf(
                "present" to true,
                "bounds"  to mapOf(
                    "left"   to bounds.left,
                    "top"    to bounds.top,
                    "right"  to bounds.right,
                    "bottom" to bounds.bottom,
                    "cx"     to bounds.centerX(),
                    "cy"     to bounds.centerY()
                )
            ), gson)
        } else {
            ok(mapOf("present" to false), gson)
        }
    }

    private fun ok(data: Any, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            "application/json", gson.toJson(data))

    private fun error(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json", gson.toJson(mapOf("error" to msg)))
}
