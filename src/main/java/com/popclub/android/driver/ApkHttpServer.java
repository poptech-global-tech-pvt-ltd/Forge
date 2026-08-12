package com.popclub.android.driver;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.Executors;

/**
 * Minimal HTTP server that exposes the Forge resources directory so a remote
 * Appium server (running on the STF Mac) can download the APK.
 *
 * Usage:
 *   String url = ApkHttpServer.getApkUrl("pop-qaDebug.apk");
 *   // → "http://192.168.1.42:18765/pop-qaDebug.apk"
 *
 * The server starts once on the first call and reuses the same port for the
 * lifetime of the JVM process.
 */
public class ApkHttpServer {

    private static volatile HttpServer server;
    private static volatile int serverPort;
    private static volatile String serverHost;

    private static final String RESOURCES_DIR =
            System.getProperty("user.dir") + "/src/main/resources";

    /** Returns the HTTP URL for the given filename under src/main/resources/. */
    public static String getApkUrl(String filename) {
        ensureStarted();
        return "http://" + serverHost + ":" + serverPort + "/" + filename;
    }

    private static synchronized void ensureStarted() {
        if (server != null) return;

        try {
            serverHost = detectLocalIp();
            HttpServer s = HttpServer.create(new InetSocketAddress(0), 0);
            serverPort = s.getAddress().getPort();

            s.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                // Strip leading slash and prevent directory traversal
                String filename = path.replaceFirst("^/+", "").replace("..", "");
                File file = new File(RESOURCES_DIR, filename);

                if (!file.exists() || !file.isFile()) {
                    byte[] msg = ("Not found: " + filename).getBytes();
                    exchange.sendResponseHeaders(404, msg.length);
                    try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
                    return;
                }

                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody();
                     FileInputStream fis = new FileInputStream(file)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
                }
            });

            s.setExecutor(Executors.newFixedThreadPool(4));
            s.start();
            server = s;

            System.out.println("[ApkServer] Serving " + RESOURCES_DIR +
                    " at http://" + serverHost + ":" + serverPort + "/");
        } catch (Exception e) {
            throw new RuntimeException("[ApkServer] Failed to start HTTP server: " + e.getMessage(), e);
        }
    }

    private static String detectLocalIp() throws Exception {
        // Prefer en0 (Wi-Fi / Ethernet on Mac)
        NetworkInterface en0 = NetworkInterface.getByName("en0");
        if (en0 != null && en0.isUp()) {
            Enumeration<InetAddress> addrs = en0.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                    return addr.getHostAddress();
                }
            }
        }
        // Fallback: first non-loopback IPv4
        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
            NetworkInterface iface = ifaces.nextElement();
            if (!iface.isUp() || iface.isLoopback()) continue;
            Enumeration<InetAddress> addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (!addr.isLoopbackAddress() && addr.getHostAddress().contains(".")) {
                    return addr.getHostAddress();
                }
            }
        }
        throw new RuntimeException("[ApkServer] Cannot detect local IP for APK HTTP server");
    }
}
