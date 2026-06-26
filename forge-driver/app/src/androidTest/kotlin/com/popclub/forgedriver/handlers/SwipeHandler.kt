package com.popclub.forgedriver.handlers

import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.popclub.forgedriver.ForgeDriverServer
import fi.iki.elonen.NanoHTTPD

/**
 * POST /swipe
 * Body: { "direction": "up" }          — swipe screen in direction
 *   OR  { "direction": "down" }
 *   OR  { "x1": 540, "y1": 1500, "x2": 540, "y2": 500, "steps": 20 }
 */
class SwipeHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        val body      = ForgeDriverServer.readBody(session)
        val params    = gson.fromJson(body, Map::class.java)
        val direction = params["direction"] as? String
        val x1        = (params["x1"] as? Double)?.toInt()
        val y1        = (params["y1"] as? Double)?.toInt()
        val x2        = (params["x2"] as? Double)?.toInt()
        val y2        = (params["y2"] as? Double)?.toInt()
        val steps     = (params["steps"] as? Double)?.toInt() ?: 20

        val w = device.displayWidth
        val h = device.displayHeight

        val swiped = when {
            direction != null -> when (direction.lowercase()) {
                "up"    -> device.swipe(w/2, h*3/4, w/2, h/4, steps)
                "down"  -> device.swipe(w/2, h/4, w/2, h*3/4, steps)
                "left"  -> device.swipe(w*3/4, h/2, w/4, h/2, steps)
                "right" -> device.swipe(w/4, h/2, w*3/4, h/2, steps)
                else    -> false
            }
            x1 != null && y1 != null && x2 != null && y2 != null ->
                device.swipe(x1, y1, x2, y2, steps)
            else -> return error("Provide direction or x1,y1,x2,y2", gson)
        }

        println("[Swipe] ✓ ${direction ?: "($x1,$y1)→($x2,$y2)"}")
        return ok(mapOf("swiped" to swiped), gson)
    }

    private fun ok(data: Any, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK,
            "application/json", gson.toJson(data))

    private fun error(msg: String, gson: Gson) =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR,
            "application/json", gson.toJson(mapOf("error" to msg)))
}
