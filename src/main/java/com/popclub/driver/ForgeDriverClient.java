package com.popclub.driver;

import com.popclub.forgedriver.proto.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * ForgeDriverClient — gRPC client for the ForgeDriver companion APK.
 *
 * Connects to the gRPC server running on device via adb forward tcp:6790.
 * Binary protobuf protocol: ~10x faster than HTTP/JSON.
 *
 * Usage: single instance reused per test run (managed by ForgeDriverManager).
 */
public class ForgeDriverClient {

    private static final String HOST = "localhost";
    private static final int    PORT = 6790;

    private final ManagedChannel channel;
    private final ForgeDriverGrpc.ForgeDriverBlockingStub stub;

    public ForgeDriverClient() {
        this.channel = ManagedChannelBuilder
            .forAddress(HOST, PORT)
            .usePlaintext()
            .build();
        this.stub = ForgeDriverGrpc.newBlockingStub(channel);
    }

    public void shutdown() {
        try { channel.shutdown().awaitTermination(5, TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Returns true if the gRPC server is reachable. */
    public boolean isAlive() {
        try {
            stub.withDeadlineAfter(1, TimeUnit.SECONDS)
                .ping(PingRequest.newBuilder().build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Wait until element with tag is present, returns bounds map or null. */
    public Map<String, Integer> waitUntilPresent(String tag, long timeoutMs) {
        WaitPresentResponse r = stub.withDeadlineAfter(timeoutMs + 2000, TimeUnit.MILLISECONDS)
            .waitPresent(WaitPresentRequest.newBuilder()
                .setTag(tag).setTimeoutMs(timeoutMs).build());
        if (!r.getFound()) return null;
        return Map.of("left", r.getLeft(), "top", r.getTop(),
                      "right", r.getRight(), "bottom", r.getBottom());
    }

    public boolean isPresent(String tag) {
        return stub.isPresent(IsPresentRequest.newBuilder().setTag(tag).build()).getFound();
    }

    public void tap(String tag) {
        ActionResponse r = stub.tap(TapRequest.newBuilder().setTag(tag).build());
        if (!r.getSuccess()) throw new RuntimeException("tap failed: " + r.getError());
    }

    public void tapByText(String text) {
        ActionResponse r = stub.tapByText(TapByTextRequest.newBuilder().setText(text).build());
        if (!r.getSuccess()) throw new RuntimeException("tapByText failed: " + r.getError());
    }

    public void tapByCoords(int x, int y) {
        ActionResponse r = stub.tapByCoords(TapByCoordsRequest.newBuilder().setX(x).setY(y).build());
        if (!r.getSuccess()) throw new RuntimeException("tapByCoords failed: " + r.getError());
    }

    public void type(String text) {
        ActionResponse r = stub.type(TypeRequest.newBuilder().setText(text).build());
        if (!r.getSuccess()) throw new RuntimeException("type failed: " + r.getError());
    }

    public void clearAndType(String text) {
        ActionResponse r = stub.clearAndType(TypeRequest.newBuilder().setText(text).build());
        if (!r.getSuccess()) throw new RuntimeException("clearAndType failed: " + r.getError());
    }

    public void swipe(String direction) {
        ActionResponse r = stub.swipe(SwipeRequest.newBuilder().setDirection(direction).build());
        if (!r.getSuccess()) throw new RuntimeException("swipe failed: " + r.getError());
    }

    public void swipe(int x1, int y1, int x2, int y2) {
        ActionResponse r = stub.swipe(SwipeRequest.newBuilder()
            .setX1(x1).setY1(y1).setX2(x2).setY2(y2).build());
        if (!r.getSuccess()) throw new RuntimeException("swipe failed: " + r.getError());
    }

    public void pressBack()   { pressKey("back"); }
    public void pressHome()   { pressKey("home"); }
    public void pressEnter()  { pressKey("enter"); }
    public void pressSearch() { pressKey("search"); }
    public void pressDelete() { pressKey("delete"); }

    private void pressKey(String key) {
        ActionResponse r = stub.pressKey(PressKeyRequest.newBuilder().setKey(key).build());
        if (!r.getSuccess()) throw new RuntimeException("pressKey failed: " + r.getError());
    }

    public String getSource() {
        return stub.getSource(SourceRequest.newBuilder().build()).getXml();
    }

    public byte[] screenshot() {
        return stub.screenshot(ScreenshotRequest.newBuilder().build()).getPng().toByteArray();
    }
}
