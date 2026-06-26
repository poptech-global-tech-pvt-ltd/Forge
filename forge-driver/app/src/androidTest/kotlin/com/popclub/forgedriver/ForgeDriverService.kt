package com.popclub.forgedriver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ForgeDriverService — long-running instrumentation test that starts the
 * Forge HTTP server on the device.
 *
 * Start via:
 *   adb shell am instrument -w \
 *     com.popclub.forgedriver.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Port forward (run once per adb session):
 *   adb forward tcp:6790 tcp:6790
 *
 * The server accepts commands from Forge Java (ForgeDriverClient) and
 * executes them via UiAutomator2 directly — no Appium, no WebDriver.
 */
@RunWith(AndroidJUnit4::class)
class ForgeDriverService {

    companion object {
        const val PORT = 6790
    }

    @Test
    fun startServer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)

        println("[ForgeDriver] Starting HTTP server on port $PORT")

        val server = ForgeDriverServer(PORT, device, instrumentation)
        server.start()

        println("[ForgeDriver] Server ready — waiting for commands from Forge")

        // Keep alive indefinitely — Forge controls the lifecycle
        try {
            Thread.sleep(Long.MAX_VALUE)
        } catch (e: InterruptedException) {
            println("[ForgeDriver] Interrupted — shutting down")
            server.stop()
        }
    }
}
