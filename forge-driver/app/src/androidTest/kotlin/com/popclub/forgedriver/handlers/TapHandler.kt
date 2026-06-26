package com.popclub.forgedriver.handlers

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.Gson
import com.popclub.forgedriver.ForgeDriverServer
import fi.iki.elonen.NanoHTTPD

/**
 * POST /tap
 * Body: { "tag": "shop_add_to_cart_button" }
 *   OR  { "x": 540, "y": 960 }
 *   OR  { "text": "Add to Cart" }
 *
 * Finds element by accessibility ID (tag), coordinate, or text — then taps it.
 * Uses UiAutomator2 directly (no Appium, no WebDriver).
 */
class TapHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        val body   = ForgeDriverServer.readBody(session)
        val params = gson.fromJson(body, Map::class.java)

        val tag  = params["tag"]  as? String
        val text = params["text"] as? String
        val x    = (params["x"]  as? Double)?.toInt()
        val y    = (params["y"]  as? Double)?.toInt()

        return when {
            tag != null -> tapByTag(tag, gson)
            text != null -> tapByText(text, gson)
            x != null && y != null -> tapByCoords(x, y, gson)
            else -> errorResponse("Provide tag, text, or x+y", gson)
        }
    }

    private fun tapByTag(tag: String, gson: Gson): NanoHTTPD.Response {
        val selector = By.res(tag).or(By.desc(tag))
        val element  = device.wait(Until.findObject(selector), 5_000)
            ?: return errorResponse("Element not found: $tag", gson)
        element.click()
        println("[Tap] ✓ tapped tag=$tag")
        return ok(mapOf("tapped" to tag), gson)
    }

    private fun tapByText(text: String, gson: Gson): NanoHTTPD.Response {
        val element = device.wait(Until.findObject(By.text(text)), 5_000)
            ?: return errorResponse("Element not found with text: $text", gson)
        element.click()
        println("[Tap] ✓ tapped text=\"$text\"")
        return ok(mapOf("tapped" to text), gson)
    }

    private fun tapByCoords(x: Int, y: Int, gson: Gson): NanoHTTPD.Response {
        device.click(x, y)
        println("[Tap] ✓ tapped ($x, $y)")
        return ok(mapOf("tapped" to "$x,$y"), gson)
    }

    private fun ok(data: Any, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            "application/json", gson.toJson(data))

    private fun errorResponse(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json", gson.toJson(mapOf("error" to msg)))
}
