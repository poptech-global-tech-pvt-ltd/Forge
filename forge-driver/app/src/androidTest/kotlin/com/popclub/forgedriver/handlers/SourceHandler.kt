package com.popclub.forgedriver.handlers

import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import java.io.StringWriter

/**
 * GET /source
 *
 * Returns the full UI tree as XML — same as what Appium's /source endpoint
 * returns, but directly from UiAutomator2 with no proxy hop.
 *
 * Used by Forge's batch element detection (WaitUtil) to search for multiple
 * elements in one call instead of firing one request per locator.
 */
class SourceHandler(private val device: UiDevice) {

    fun handle(session: NanoHTTPD.IHTTPSession, gson: Gson): NanoHTTPD.Response {
        return try {
            val writer = StringWriter()
            device.dumpWindowHierarchy(writer)
            val xml = writer.toString()
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                "application/xml",
                xml
            )
        } catch (e: Exception) {
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.INTERNAL_ERROR,
                "application/json",
                gson.toJson(mapOf("error" to e.message))
            )
        }
    }
}
