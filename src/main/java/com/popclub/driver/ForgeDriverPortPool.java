package com.popclub.driver;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe pool of gRPC port numbers for parallel ForgeDriver instances.
 *
 * Each device gets its own port so parallel test threads don't collide.
 * Range is configured via forge.port.range in cloud.properties (default 7790-7799).
 *
 * On the STF Mac, socat must listen on every port in the range:
 *   for p in $(seq 7790 7799); do socat TCP-LISTEN:$p,fork,reuseaddr TCP:localhost:$p &amp; done
 */
public class ForgeDriverPortPool {

    private static final BlockingQueue<Integer> pool;

    static {
        String range = com.popclub.android.cloud.CloudConfig.getForgePortRange();
        pool = new LinkedBlockingQueue<>();
        String[] parts = range.split("-");
        int start = Integer.parseInt(parts[0].trim());
        int end   = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : start;
        for (int p = start; p <= end; p++) {
            pool.offer(p);
        }
        System.out.println("[ForgePortPool] Initialized with ports " + start + "-" + end
                + " (" + pool.size() + " slots)");
    }

    /**
     * Acquire a free port, blocking up to timeoutMs.
     * @throws RuntimeException if no port becomes available in time
     */
    public static int acquire(long timeoutMs) throws InterruptedException {
        Integer port = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (port == null) {
            throw new RuntimeException("[ForgePortPool] No free port available after "
                    + timeoutMs + "ms. Increase forge.port.range or reduce parallelism.");
        }
        System.out.println("[ForgePortPool] Acquired port " + port
                + " (" + pool.size() + " remaining)");
        return port;
    }

    /** Return a port to the pool after the test finishes. */
    public static void release(int port) {
        pool.offer(port);
        System.out.println("[ForgePortPool] Released port " + port
                + " (" + pool.size() + " available)");
    }

    public static int available() {
        return pool.size();
    }
}
