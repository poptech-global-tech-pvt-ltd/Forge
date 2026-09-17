package com.popclub.core;

import com.popclub.android.driver.AppiumDriverManager;
import com.popclub.android.driver.DeviceInfo;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Wraps System.out so every line is prefixed with "[SERIAL] " for the current thread.
 * Install once at suite start: System.setOut(new DeviceAwarePrintStream(System.out));
 * Threads that have no device assigned print without a prefix.
 */
public class DeviceAwarePrintStream extends PrintStream {

    private final PrintStream delegate;
    // Track whether we're at the start of a new line (need to emit the prefix next)
    private final ThreadLocal<Boolean> needsPrefix = ThreadLocal.withInitial(() -> true);

    public DeviceAwarePrintStream(PrintStream delegate) {
        super(delegate, true);
        this.delegate = delegate;
    }

    private String prefix() {
        DeviceInfo d = AppiumDriverManager.deviceInfo.get();
        return (d != null) ? "[" + d.udid + "] " : "";
    }

    @Override
    public void println(String x) {
        delegate.println(prefix() + (x != null ? x : "null"));
    }

    @Override
    public void println(Object x) {
        delegate.println(prefix() + x);
    }

    @Override
    public void println() {
        delegate.println();
    }

    @Override
    public void println(boolean x)  { delegate.println(prefix() + x); }
    @Override
    public void println(char x)     { delegate.println(prefix() + x); }
    @Override
    public void println(int x)      { delegate.println(prefix() + x); }
    @Override
    public void println(long x)     { delegate.println(prefix() + x); }
    @Override
    public void println(float x)    { delegate.println(prefix() + x); }
    @Override
    public void println(double x)   { delegate.println(prefix() + x); }
    @Override
    public void println(char[] x)   { delegate.println(prefix() + new String(x)); }

    @Override
    public PrintStream printf(String format, Object... args) {
        return delegate.printf(prefix() + format, args);
    }

    @Override
    public PrintStream format(String format, Object... args) {
        return delegate.format(prefix() + format, args);
    }
}
