package com.popclub.core;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Captures System.out/System.err for one test into a per-test .log file, while still echoing to the console. */
public class TestLogCapture {

    private static final String LOG_DIR = "reports/logs/";

    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static OutputStream fileStream;
    private static File logFile;

    /** Begin capturing console output for one test into reports/logs/{name}.log */
    public static synchronized void start(String testName) {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));

            String safeName = testName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String fileName = safeName + "_" + System.currentTimeMillis() + ".log";
            logFile = new File(LOG_DIR + fileName);
            fileStream = new BufferedOutputStream(new FileOutputStream(logFile));

            originalOut = System.out;
            originalErr = System.err;

            System.setOut(new PrintStream(new TeeOutputStream(originalOut, fileStream), true));
            System.setErr(new PrintStream(new TeeOutputStream(originalErr, fileStream), true));

        } catch (Exception e) {
            // If capture can't start, leave the streams untouched — never break the test.
            System.out.println("[TestLogCapture] Could not start log capture: " + e.getMessage());
            logFile = null;
        }
    }

    /**
     * Stop capturing, restore the real console streams, and return the saved log
     * file (or {@code null} if capture never started).
     */
    public static synchronized File stop() {
        if (originalOut != null) System.setOut(originalOut);
        if (originalErr != null) System.setErr(originalErr);

        try {
            if (fileStream != null) {
                fileStream.flush();
                fileStream.close();
            }
        } catch (Exception ignored) {
            // Nothing useful to do on close failure.
        } finally {
            fileStream = null;
            originalOut = null;
            originalErr = null;
        }
        return logFile;
    }

    /** Writes every byte to two streams at once (the real console + the log file). */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream console;
        private final OutputStream file;

        TeeOutputStream(OutputStream console, OutputStream file) {
            this.console = console;
            this.file = file;
        }

        @Override
        public void write(int b) throws IOException {
            console.write(b);
            file.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            console.write(buf, off, len);
            file.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            console.flush();
            file.flush();
        }

        /** Only flush — never close the underlying console; the file is closed in stop(). */
        @Override
        public void close() throws IOException {
            console.flush();
            file.flush();
        }
    }
}
