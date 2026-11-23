package com.popclub.driver;

import com.popclub.forgedriver.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverClient — talks to the ForgeDriver gRPC server on the Android device.
 *
 * The companion APK exposes a gRPC server on port 7790 (device-side).
 * Port-forward with: adb forward tcp:7790 tcp:7790
 *
 * ForgeDriverManager.start() handles port-forwarding and lifecycle.
 */
public class ForgeDriverClient {

    private static final String HOST      = "localhost";
    private static final int    PORT      = 7790;
    // Per-call deadline for quick operations (tap, type, swipe, isPresent, etc.)
    private static final int    QUICK_S   = 10;

    private final ManagedChannel            channel;
    private final ForgeDriverGrpc.ForgeDriverBlockingStub stub;

    public ForgeDriverClient() {
        this(HOST, PORT);
    }

    public ForgeDriverClient(String host) {
        this(host, PORT);
    }

    public ForgeDriverClient(String host, int port) {
        this.channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();
        this.stub = ForgeDriverGrpc.newBlockingStub(channel);
    }

    /** Returns a stub with a short per-call deadline for quick one-shot operations. */
    private ForgeDriverGrpc.ForgeDriverBlockingStub quick() {
        return stub.withDeadlineAfter(QUICK_S, TimeUnit.SECONDS);
    }

    // ── Health check ──────────────────────────────────────────────────────────

    public boolean isAlive() {
        try {
            PingResponse res = stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                    .ping(PingRequest.newBuilder().build());
            return "ok".equals(res.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    // ── Element presence ──────────────────────────────────────────────────────

    public int[] waitUntilPresent(String tag, long timeoutMillis) {
        try {
            // Use a per-call deadline = requested timeout + 8s overhead so the gRPC
            // call doesn't expire before the server-side poll finishes.
            WaitPresentResponse res = stub
                    .withDeadlineAfter(timeoutMillis + 8_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .waitPresent(
                            WaitPresentRequest.newBuilder()
                                    .setTag(tag)
                                    .setTimeoutMs(timeoutMillis)
                                    .build());
            if (res.getFound()) {
                return new int[]{res.getLeft(), res.getTop(), res.getRight(), res.getBottom()};
            }
        } catch (StatusRuntimeException e) {
            System.out.println("[ForgeDriver] waitUntilPresent error: " + e.getMessage());
        }
        return null;
    }

    public boolean isPresent(String tag) {
        try {
            return quick().isPresent(IsPresentRequest.newBuilder().setTag(tag).build()).getFound();
        } catch (Exception e) {
            return false;
        }
    }

    // ── Tap ──────────────────────────────────────────────────────────────────

    public void tap(String tag) {
        quick().tap(TapRequest.newBuilder().setTag(tag).build());
    }

    public void tapByText(String text) {
        quick().tapByText(TapByTextRequest.newBuilder().setText(text).build());
    }

    public void tapByCoords(int x, int y) {
        quick().tapByCoords(TapByCoordsRequest.newBuilder().setX(x).setY(y).build());
    }

    // ── Type ─────────────────────────────────────────────────────────────────

    public void type(String text) {
        quick().type(TypeRequest.newBuilder().setText(text).build());
    }

    public void clearAndType(String text) {
        quick().clearAndType(TypeRequest.newBuilder().setText(text).build());
    }

    // ── Swipe ────────────────────────────────────────────────────────────────

    public void swipe(String direction) {
        quick().swipe(SwipeRequest.newBuilder().setDirection(direction).build());
    }

    public void swipe(int x1, int y1, int x2, int y2) {
        quick().swipe(SwipeRequest.newBuilder().setX1(x1).setY1(y1).setX2(x2).setY2(y2).build());
    }

    // ── Keys ─────────────────────────────────────────────────────────────────

    public void pressBack()   { quick().pressKey(PressKeyRequest.newBuilder().setKey("back").build()); }
    public void pressHome()   { quick().pressKey(PressKeyRequest.newBuilder().setKey("home").build()); }
    public void pressEnter()  { quick().pressKey(PressKeyRequest.newBuilder().setKey("enter").build()); }
    public void pressSearch() { quick().pressKey(PressKeyRequest.newBuilder().setKey("search").build()); }
    public void pressDelete() { quick().pressKey(PressKeyRequest.newBuilder().setKey("delete").build()); }

    // ── UI source ─────────────────────────────────────────────────────────────

    public String getSource() {
        return quick().getSource(SourceRequest.newBuilder().build()).getXml();
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    public byte[] screenshot() {
        return quick().screenshot(ScreenshotRequest.newBuilder().build()).getPng().toByteArray();
    }

    // ── Search ────────────────────────────────────────────────────────────────

    public List<AccessibilityNode> search(String tag) {
        return quick().searchNodes(SearchRequest.newBuilder().setTag(tag).build()).getNodesList();
    }

    public List<AccessibilityNode> searchByText(String text) {
        return quick().searchNodes(SearchRequest.newBuilder().setText(text).build()).getNodesList();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
