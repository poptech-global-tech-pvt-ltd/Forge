package com.popclub.ai.app;

import com.popclub.parser.XmlElementParser;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/**
 * Maestro-format recorder — same ADB recording pipeline as Recorder.java,
 * but outputs Maestro-compatible YAML that can be run directly with:
 *   maestro test flow.yaml
 *
 * Output format (Maestro native):
 *   appId: com.popclub.android
 *   ---
 *   - tapOn:
 *       id: "home_search_bar"        # accessibilityId / qaTestTag
 *   - tapOn:
 *       text: "Add to cart"          # text fallback (when no tag)
 *   - tapOn:
 *       point: "50%,80%"             # last resort: percentage coordinates
 *   - swipe:
 *       direction: UP
 *   - inputText: "yoga bar"
 *
 * Usage:
 *   ./run-maestro-recorder.sh [device-udid]
 *   Type  exit + ENTER  to stop and save.
 */
public class MaestroRecorder {

    private static final String REPORTS_DIR = "reports/maestro";
    private static final String APP_ID      = "com.popclub.android";

    // ── Touch state ───────────────────────────────────────────────────────────
    private static final AtomicInteger touchX    = new AtomicInteger(-1);
    private static final AtomicInteger touchY    = new AtomicInteger(-1);
    private static final AtomicInteger startX    = new AtomicInteger(-1);
    private static final AtomicInteger startY    = new AtomicInteger(-1);
    private static final AtomicLong    touchDown = new AtomicLong(0);
    private static volatile boolean    tracking  = false;

    // ── Text input detection ──────────────────────────────────────────────────
    private static volatile boolean watchingText       = false;
    private static volatile long    lastTouchMillis    = 0;
    private static volatile String  pendingTextLocator = null;

    // ── Screen calibration ────────────────────────────────────────────────────
    private static int screenW = 1080;
    private static int screenH = 2400;
    private static int maxRawX = 1080;
    private static int maxRawY = 2400;

    // ── Session state ─────────────────────────────────────────────────────────
    private static String udid;
    private static String sessionDir;
    private static volatile String currentScreen = "unknown_screen";

    /**
     * Each entry is a ready-to-write YAML block for one Maestro step.
     * e.g.  "- tapOn:\n    id: \"home_search\"\n"
     */
    private static final List<String>             steps       = new ArrayList<>();
    private static final List<Map<String, String>> missingTags = new ArrayList<>();

    // Single-thread executor — screenshot + UI dump + Claude can take ~10s;
    // dispatching off the getevent thread ensures taps are never lost.
    private static final ExecutorService tapExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tap-processor");
        t.setDaemon(true);
        return t;
    });

    // ── main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // Args: [udid] [flow-name]
        // e.g.  10BDCM0YJZ00043 shop_add_to_cart
        String udidArg     = null;
        String flowNameArg = null;
        for (String a : args) {
            if (a.contains(":") || a.matches("[A-Z0-9]{15,}")) udidArg = a.trim();
            else if (!a.isBlank()) flowNameArg = a.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        }
        udid = udidArg != null ? udidArg : detectDevice();

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        // Use caller-supplied name if given, otherwise timestamp
        String testName = (flowNameArg != null && !flowNameArg.isBlank())
                ? flowNameArg + "_" + timestamp
                : "maestro_" + timestamp;
        sessionDir = REPORTS_DIR + "/" + testName;
        Files.createDirectories(Paths.get(sessionDir + "/elements"));

        detectScreenSize();
        detectInputMaxValues();

        boolean aiEnabled = isClaudeAvailable();
        System.out.println("Device   : " + udid);
        System.out.println("Screen   : " + screenW + "x" + screenH);
        System.out.println("Flow name: " + testName);
        System.out.println("Format   : Maestro YAML  (run with: maestro test <file.yaml>)");
        System.out.println("AI vision: " + (aiEnabled ? "✓ enabled (claude CLI)" : "✗ claude CLI not found"));
        System.out.println("\n── Recording started ─────────────────────────────");
        System.out.println("  Use the device normally.");
        System.out.println("  Commands while recording:");
        System.out.println("    exit    → stop and save");
        System.out.println("    assert  → add assertVisible for current screen elements");
        System.out.println("──────────────────────────────────────────────────\n");

        Thread screenPoller = new Thread(MaestroRecorder::pollScreen);
        screenPoller.setDaemon(true);
        screenPoller.start();

        Thread eventThread = new Thread(MaestroRecorder::listenEvents);
        eventThread.setDaemon(true);
        eventThread.start();

        Thread textFlusher = new Thread(MaestroRecorder::watchTextInput);
        textFlusher.setDaemon(true);
        textFlusher.start();

        // Save YAML on Ctrl+C or kill — not just on clean "exit"
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { saveYaml(testName); } catch (Exception ignored) {}
        }));

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String input = reader.readLine();
            if (input == null) break;
            String cmd = input.trim().toLowerCase();
            if ("exit".equals(cmd)) break;
            if ("assert".equals(cmd)) {
                recordAssertions();
            }
        }

        saveYaml(testName);
        System.out.println("Flow     → " + sessionDir + "/" + testName + ".yaml  (" + steps.size() + " steps)");
        System.out.println("Run with → maestro test " + sessionDir + "/" + testName + ".yaml");
        if (!missingTags.isEmpty())
            System.out.println("Missing  → " + sessionDir + "/" + testName + "_missing.yaml  (" + missingTags.size() + " tags)");
    }

    // ── Screen name polling ───────────────────────────────────────────────────

    private static void pollScreen() {
        while (true) {
            try {
                Thread.sleep(3000);
                String name = resolveActivity();
                if (!name.equals("unknown_screen") && !name.equals(currentScreen)) {
                    currentScreen = name;
                    System.out.println("  ── screen: " + name + " ──");
                }
            } catch (InterruptedException ignored) {}
        }
    }

    // ── Touch event stream ────────────────────────────────────────────────────

    private static void listenEvents() {
        try {
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", udid, "shell", "getevent", "-lt");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            System.out.println("  [getevent stream started]");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) parseEvent(line);
            }
        } catch (Exception e) {
            System.err.println("[getevent error] " + e.getMessage());
        }
    }

    private static void parseEvent(String line) {
        try {
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 6) return;
            String code  = parts[4];
            String value = parts[5];

            switch (code) {
                case "BTN_TOUCH":
                    if ("DOWN".equals(value)) {
                        tracking = true;
                        touchDown.set(System.currentTimeMillis());
                        startX.set(-1); startY.set(-1);
                        lastTouchMillis = System.currentTimeMillis();
                    } else if ("UP".equals(value) && tracking) {
                        tracking = false;
                        long dur = System.currentTimeMillis() - touchDown.get();
                        onTouchUp(touchX.get(), touchY.get(), startX.get(), startY.get(), dur);
                    }
                    break;
                case "ABS_MT_POSITION_X":
                    int rx = Integer.parseInt(value, 16);
                    touchX.set(rx);
                    if (startX.get() == -1) startX.set(rx);
                    lastTouchMillis = System.currentTimeMillis();
                    break;
                case "ABS_MT_POSITION_Y":
                    int ry = Integer.parseInt(value, 16);
                    touchY.set(ry);
                    if (startY.get() == -1) startY.set(ry);
                    lastTouchMillis = System.currentTimeMillis();
                    break;
            }
        } catch (Exception ignored) {}
    }

    private static void onTouchUp(int rawX, int rawY, int rawSX, int rawSY, long durationMs) {
        int x  = rawToScreenX(rawX),  y  = rawToScreenY(rawY);
        int sx = rawToScreenX(rawSX), sy = rawToScreenY(rawSY);
        int dx = Math.abs(x - sx),    dy = y - sy;
        String screenAtTap = currentScreen;
        tapExecutor.submit(() -> {
            try {
                if (dx > 60 || Math.abs(dy) > 60) recordSwipe(x, y, sx, sy, dx, dy, durationMs);
                else                               recordTap(x, y, durationMs, screenAtTap);
            } catch (Exception e) {
                System.err.println("  [record error] " + e.getMessage());
            }
        });
    }

    // ── Record assertions for current screen ─────────────────────────────────

    /**
     * Dumps the current UI, finds all elements with qaTestTags (accessibilityId),
     * and adds Maestro assertVisible steps for each — giving full screen coverage.
     * Type  assert + ENTER  while recording to trigger.
     */
    private static void recordAssertions() {
        tapExecutor.submit(() -> {
            try {
                System.out.println("  [assert] Dumping UI for assertions...");
                String xml = getPageSource();
                if (xml == null || xml.isBlank()) {
                    System.out.println("  [assert] No UI dump — skipping");
                    return;
                }
                List<Map<String, String>> elements = XmlElementParser.parse(xml);

                int count = 0;
                StringBuilder comment = new StringBuilder("# assertions for screen: ")
                        .append(currentScreen).append("\n");
                List<String> assertSteps = new ArrayList<>();

                for (Map<String, String> el : elements) {
                    String tag  = el.getOrDefault("accessibilityId", "").trim();
                    String text = el.getOrDefault("text", "").trim();

                    if (!tag.isEmpty()) {
                        // Best: assert by accessibilityId (qaTestTag)
                        assertSteps.add("- assertVisible:\n    id: \"" + tag + "\"\n");
                        count++;
                    } else if (!text.isEmpty() && !isDynamicText(text)) {
                        // Fallback: assert by visible text
                        assertSteps.add("- assertVisible:\n    text: \"" + escapeYaml(text) + "\"\n");
                        count++;
                    }
                }

                if (assertSteps.isEmpty()) {
                    System.out.println("  [assert] No assertable elements found on screen");
                    return;
                }

                // Deduplicate
                List<String> unique = new ArrayList<>(new LinkedHashSet<>(assertSteps));
                steps.add(comment.toString());
                steps.addAll(unique);
                System.out.println("  ✓ assert → added " + unique.size()
                        + " assertVisible steps for screen: " + currentScreen);

            } catch (Exception e) {
                System.err.println("  [assert error] " + e.getMessage());
            }
        });
    }

    // ── Record swipe/scroll → Maestro swipe ──────────────────────────────────

    private static void recordSwipe(int x, int y, int sx, int sy,
                                    int dx, int dy, long dur) {
        String dir = Math.abs(dy) >= dx
                ? (dy < 0 ? "UP" : "DOWN")
                : (x > sx ? "RIGHT" : "LEFT");

        // Maestro swipe with start point as percentage of screen
        int pctX = (int) Math.round((double) sx / screenW * 100);
        int pctY = (int) Math.round((double) sy / screenH * 100);
        String step = "- swipe:\n"
                + "    direction: " + dir + "\n"
                + "    from:\n"
                + "      x: " + pctX + "%\n"
                + "      y: " + pctY + "%\n";
        steps.add(step);
        System.out.println("  ↕  swipe " + dir.toLowerCase());
    }

    // ── Record tap → Maestro tapOn ───────────────────────────────────────────

    private static void recordTap(int x, int y, long durationMs, String screenAtTap) throws Exception {
        flushPendingText();
        Thread.sleep(300);

        Path screenshot = takeScreenshot();
        String xml = getPageSource();
        if (xml == null || xml.isBlank()) {
            System.out.println("  ? tap (" + x + "," + y + ") — no UI dump");
            Files.deleteIfExists(screenshot);
            return;
        }

        List<Map<String, String>> elements = XmlElementParser.parse(xml);
        Map<String, String> hit = findSmallestAt(elements, x, y);

        boolean isLongPress = durationMs > 600;
        // Maestro uses longPress instead of tapOn for long-press
        String tapCmd = isLongPress ? "longPressOn" : "tapOn";

        if (hit != null) {
            String tag    = hit.getOrDefault("accessibilityId", "").trim();
            String text   = hit.getOrDefault("text", "").trim();
            String cls    = hit.getOrDefault("class", "").trim();
            String bounds = hit.getOrDefault("bounds", "").trim();
            int[]  b      = parseBounds(bounds);

            String step;

            if (!tag.isEmpty()) {
                QaTagAnalyzer.TagStatus status = QaTagAnalyzer.checkElement(screenAtTap, tag, text, cls);
                if (status.state == QaTagAnalyzer.TagStatus.State.GOOD) {
                    // ✓ Good tag — use id
                    step = "- " + tapCmd + ":\n    id: \"" + tag + "\"\n";
                    System.out.println("  ✓ " + tapCmd + " → id: " + tag);
                } else {
                    // ⚠ Bad tag — still use it but flag + ask AI for better name
                    step = "- " + tapCmd + ":\n    id: \"" + tag
                            + "\"  # ⚠ rename tag — see _missing.yaml\n";
                    System.out.println("  ⚠ " + tapCmd + " → bad tag: \"" + tag + "\"");
                    String aiTag = null;
                    if (b != null) {
                        String nearby = getNearbyText(elements, b, x, y);
                        aiTag = askClaude(screenshot, screenAtTap, text, cls, nearby, b);
                    }
                    String suggestion = (aiTag != null)
                            ? "const val " + aiTag.toUpperCase() + " = \"" + aiTag + "\""
                            : status.message;
                    if (aiTag != null)
                        System.out.println("    AI → rename to: const val "
                                + aiTag.toUpperCase() + " = \"" + aiTag + "\"");
                    trackMissing(tag, text, cls, bounds, suggestion, screenshot, b);
                }
                // Watch for text input after tapping an EditText
                if (cls.contains("EditText")) {
                    watchingText       = true;
                    pendingTextLocator = "id: " + tag;
                    lastTouchMillis    = System.currentTimeMillis();
                    Files.deleteIfExists(screenshot);
                    return;
                }
            } else {
                // No tag — ask Claude for a suggestion
                String suggestedTag = null;
                if (b != null) {
                    String nearby = getNearbyText(elements, b, x, y);
                    suggestedTag = askClaude(screenshot, screenAtTap, text, cls, nearby, b);
                    if (suggestedTag != null)
                        System.out.println("    AI → add to TestTags.kt: const val "
                                + suggestedTag.toUpperCase() + " = \"" + suggestedTag + "\"");
                }

                if (suggestedTag == null) {
                    QaTagAnalyzer.TagStatus ruleStatus = QaTagAnalyzer.checkElement(screenAtTap, "", text, cls);
                    if (ruleStatus.message != null)
                        suggestedTag = extractTagFromSuggestion(ruleStatus.message);
                }

                String suggestion = (suggestedTag != null)
                        ? "const val " + suggestedTag.toUpperCase() + " = \"" + suggestedTag + "\""
                        : "(could not determine)";
                trackMissing(tag, text, cls, bounds, suggestion, screenshot, b);

                if (!text.isEmpty() && !isDynamicText(text)) {
                    // Use text locator — works in Maestro
                    String comment = suggestedTag != null
                            ? "  # TODO: add " + suggestedTag + " to TestTags.kt"
                            : "  # TODO: add qaTestTag to this element";
                    step = "- " + tapCmd + ":\n    text: \"" + escapeYaml(text) + "\"" + comment + "\n";
                    System.out.println("  ~ " + tapCmd + " → text: \"" + text + "\""
                            + (suggestedTag != null ? "  (add tag: " + suggestedTag + ")" : ""));

                    if (cls.contains("EditText")) {
                        watchingText       = true;
                        pendingTextLocator = "text: " + text;
                        lastTouchMillis    = System.currentTimeMillis();
                        Files.deleteIfExists(screenshot);
                        return;
                    }
                } else {
                    // Last resort: percentage point coordinates
                    int pctX = (int) Math.round((double) x / screenW * 100);
                    int pctY = (int) Math.round((double) y / screenH * 100);
                    String comment = suggestedTag != null
                            ? "  # TODO: add " + suggestedTag + " to TestTags.kt"
                            : "  # TODO: add qaTestTag to this element";
                    step = "- tapOn:\n    point: \"" + pctX + "%," + pctY + "%\"" + comment + "\n";
                    System.out.println("  ✗ " + tapCmd + " → point: " + pctX + "%," + pctY + "%"
                            + (suggestedTag != null ? "  (add tag: " + suggestedTag + ")" : ""));
                }
            }

            steps.add(step);
        } else {
            // Element not found in hierarchy — use percentage coords
            int pctX = (int) Math.round((double) x / screenW * 100);
            int pctY = (int) Math.round((double) y / screenH * 100);
            String step = "- tapOn:\n    point: \"" + pctX + "%," + pctY + "%\"  # element not found\n";
            steps.add(step);
            System.out.println("  ? " + tapCmd + " → point: " + pctX + "%," + pctY + "% — element not found in hierarchy");
        }

        Files.deleteIfExists(screenshot);
    }

    // ── Claude vision ─────────────────────────────────────────────────────────

    private static String askClaude(Path screenshotFull, String screenName,
                                    String elementText, String cls,
                                    String nearbyContext, int[] elementBounds) {
        try {
            Path annotated = annotateScreenshot(screenshotFull, elementBounds);
            Path imageFile  = (annotated != null) ? annotated : screenshotFull;

            StringBuilder prompt = new StringBuilder();
            prompt.append("Use the Read tool to view this image: ")
                  .append(imageFile.toAbsolutePath())
                  .append("\n\n")
                  .append("This is a full-screen screenshot of the '").append(screenName)
                  .append("' screen of POP Club (mobile shopping app). ")
                  .append("A UI element is highlighted with a RED rectangle — that is the element to name. ");
            if (!elementText.isEmpty())
                prompt.append("The highlighted element shows text: \"").append(elementText).append("\". ");
            if (!nearbyContext.isEmpty())
                prompt.append("Nearby visible text on screen: ").append(nearbyContext).append(". ");
            prompt.append("\nBased on the full screen context and the highlighted element, ")
                  .append("suggest a snake_case test tag name with a screen/section prefix ")
                  .append("(e.g. home_search_bar, checkout_delivery_date_label, cart_checkout_button, ")
                  .append("profile_edit_name_input, product_add_to_cart_button). ")
                  .append("\nReply with ONLY the tag name — no explanation, no const val, just the tag.");

            String imageDir = imageFile.getParent().toAbsolutePath().toString();

            List<String> cmd = new ArrayList<>(Arrays.asList(
                "claude", "--print",
                "--allowedTools", "Read",
                "--permission-mode", "bypassPermissions",
                "--add-dir", imageDir,
                "--no-session-persistence",
                "-p", prompt.toString()
            ));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectInput(new File("/dev/null"));
            pb.environment().remove("ANTHROPIC_API_KEY");
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append(" ");
            }
            int exitCode = p.waitFor();

            if (annotated != null) Files.deleteIfExists(annotated);

            if (exitCode == 0) {
                String raw = out.toString().trim();
                Matcher m = Pattern.compile("\\b([a-z][a-z0-9]{2,}(?:_[a-z0-9]+)+)\\b").matcher(raw);
                if (m.find()) return m.group(1);
            } else {
                String errLine = out.toString().trim().lines().findFirst().orElse("unknown error");
                System.err.println("    [AI error] claude exited " + exitCode + ": " + errLine);
            }
        } catch (Exception e) {
            System.err.println("    [AI error] " + e.getMessage());
        }
        return null;
    }

    private static Path annotateScreenshot(Path screenshot, int[] bounds) {
        if (screenshot == null || bounds == null) return null;
        try {
            BufferedImage img = ImageIO.read(screenshot.toFile());
            if (img == null) return null;
            BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(img, 0, 0, null);
            int x1 = Math.max(0, bounds[0]),  y1 = Math.max(0, bounds[1]);
            int x2 = Math.min(img.getWidth(), bounds[2]), y2 = Math.min(img.getHeight(), bounds[3]);
            int w = x2 - x1, h = y2 - y1;
            if (w > 0 && h > 0) {
                g.setColor(new Color(255, 0, 0, 60));
                g.fillRect(x1, y1, w, h);
                g.setColor(Color.RED);
                g.setStroke(new BasicStroke(4f));
                g.drawRect(x1, y1, w, h);
            }
            g.dispose();
            Path out = Files.createTempFile("annotated_", ".png");
            ImageIO.write(copy, "PNG", out.toFile());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getNearbyText(List<Map<String, String>> elements, int[] targetBounds,
                                        int tapX, int tapY) {
        if (elements == null || targetBounds == null) return "";
        int cx = (targetBounds[0] + targetBounds[2]) / 2;
        int cy = (targetBounds[1] + targetBounds[3]) / 2;
        int radius = 400;
        List<String> nearby = new ArrayList<>();
        for (Map<String, String> el : elements) {
            String txt = el.getOrDefault("text", "").trim();
            if (txt.isEmpty() || isDynamicText(txt) || txt.length() > 50) continue;
            int[] b = parseBounds(el.getOrDefault("bounds", ""));
            if (b == null) continue;
            int ecx = (b[0] + b[2]) / 2, ecy = (b[1] + b[3]) / 2;
            int dist = (int) Math.sqrt((double)(ecx-cx)*(ecx-cx) + (double)(ecy-cy)*(ecy-cy));
            if (dist > 0 && dist <= radius) nearby.add(txt);
        }
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(nearby));
        if (unique.size() > 10) unique = unique.subList(0, 10);
        return String.join(", ", unique);
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    private static Path takeScreenshot() {
        try {
            Path tmp = Files.createTempFile("screen_", ".png");
            ProcessBuilder pb = new ProcessBuilder("adb", "-s", udid, "exec-out", "screencap", "-p");
            pb.redirectOutput(tmp.toFile());
            pb.redirectErrorStream(false);
            Process p = pb.start();
            p.waitFor();
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Text input detection ──────────────────────────────────────────────────

    private static void watchTextInput() {
        while (true) {
            try {
                Thread.sleep(300);
                if (watchingText && System.currentTimeMillis() - lastTouchMillis >= 1500)
                    flushPendingText();
            } catch (InterruptedException ignored) {}
        }
    }

    private static synchronized void flushPendingText() {
        if (!watchingText) return;
        watchingText = false;
        try {
            String xml = getPageSource();
            if (xml == null) return;
            List<Map<String, String>> elements = XmlElementParser.parse(xml);
            String typedText = null;
            for (Map<String, String> el : elements) {
                if (!el.getOrDefault("class", "").contains("EditText")) continue;
                String txt = el.getOrDefault("text", "").trim();
                if (!txt.isEmpty()) { typedText = txt; break; }
            }
            if (typedText != null) {
                // Maestro inputText command
                String step = "- inputText: \"" + escapeYaml(typedText) + "\"\n";
                steps.add(step);
                System.out.println("  ✓ inputText → \"" + typedText + "\"");
            }
        } catch (Exception e) {
            System.err.println("  [text flush error] " + e.getMessage());
        } finally {
            pendingTextLocator = null;
        }
    }

    // ── Hit test ──────────────────────────────────────────────────────────────

    private static Map<String, String> findSmallestAt(List<Map<String, String>> elements,
                                                       int x, int y) {
        Map<String, String> best     = null;
        int                 bestArea = Integer.MAX_VALUE;
        for (Map<String, String> el : elements) {
            int[] b = parseBounds(el.getOrDefault("bounds", ""));
            if (b == null) continue;
            if (x >= b[0] && y >= b[1] && x <= b[2] && y <= b[3]) {
                int area = (b[2] - b[0]) * (b[3] - b[1]);
                if (area < bestArea) { bestArea = area; best = el; }
            }
        }
        return best;
    }

    private static int[] parseBounds(String bounds) {
        try {
            String[] p = bounds.replace("[","").replace("]",",").split(",");
            return new int[]{Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                             Integer.parseInt(p[2].trim()), Integer.parseInt(p[3].trim())};
        } catch (Exception e) { return null; }
    }

    // ── Page source ───────────────────────────────────────────────────────────

    private static String getPageSource() throws Exception {
        adb("shell", "uiautomator", "dump", "/sdcard/wd.xml");
        Path tmp = Files.createTempFile("wd_", ".xml");
        adb("pull", "/sdcard/wd.xml", tmp.toAbsolutePath().toString());
        String xml = Files.readString(tmp);
        Files.deleteIfExists(tmp);
        int s = xml.indexOf("<hierarchy"), e = xml.lastIndexOf("</hierarchy>");
        return (s >= 0 && e >= 0) ? xml.substring(s, e + "</hierarchy>".length()) : null;
    }

    // ── Missing tag tracker ───────────────────────────────────────────────────

    private static void trackMissing(String tag, String text, String cls, String bounds,
                                     String suggestion, Path elementImage, int[] b) {
        for (Map<String, String> m : missingTags)
            if (suggestion.equals(m.get("suggestion"))) return;
        String imgPath = "";
        if (elementImage != null && Files.exists(elementImage)) {
            try {
                String fname = "element_" + (missingTags.size() + 1) + ".png";
                Path dest = Paths.get(sessionDir, "elements", fname);
                Files.copy(elementImage, dest, StandardCopyOption.REPLACE_EXISTING);
                imgPath = "elements/" + fname;
            } catch (Exception ignored) {}
        }
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("screen",     currentScreen);
        entry.put("element",    text.isEmpty() ? (tag.isEmpty() ? "(unknown)" : tag) : text);
        entry.put("class",      cls);
        entry.put("bounds",     bounds);
        entry.put("suggestion", suggestion);
        if (!imgPath.isEmpty()) entry.put("screenshot", imgPath);
        missingTags.add(entry);
    }

    // ── YAML save ─────────────────────────────────────────────────────────────

    private static void saveYaml(String testName) throws Exception {
        Path out = Paths.get(sessionDir, testName + ".yaml");
        StringBuilder sb = new StringBuilder();

        // Maestro flow header
        sb.append("# Recorded  : ").append(testName).append("\n");
        sb.append("# Date      : ")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
          .append("\n");
        sb.append("# Run with  : maestro test ").append(out.getFileName()).append("\n\n");
        sb.append("appId: ").append(APP_ID).append("\n");
        sb.append("---\n");

        // Write each step (already formatted as Maestro YAML blocks)
        for (String step : steps) {
            sb.append(step);
        }

        Files.writeString(out, sb.toString());

        // Missing tags YAML (same format as Recorder.java — used by TagPatcher)
        if (!missingTags.isEmpty()) {
            Path mp = Paths.get(sessionDir, testName + "_missing.yaml");
            StringBuilder ms = new StringBuilder();
            ms.append("# Missing test tags — add to Popdroid: TestTags.kt\n");
            ms.append("# Recording: ").append(testName).append("\n\n");
            ms.append("missing:\n");
            String lastScreen = "";
            for (Map<String, String> m : missingTags) {
                String screen = m.get("screen");
                if (!screen.equals(lastScreen)) {
                    ms.append("\n  # ── ").append(screen).append(" ──\n");
                    lastScreen = screen;
                }
                ms.append("  - element:    ").append(m.get("element")).append("\n");
                ms.append("    class:      ").append(m.get("class")).append("\n");
                ms.append("    bounds:     ").append(m.get("bounds")).append("\n");
                ms.append("    suggestion: ").append(m.get("suggestion")).append("\n");
                if (m.containsKey("screenshot"))
                    ms.append("    screenshot: ").append(m.get("screenshot")).append("\n");
            }
            Files.writeString(mp, ms.toString());
        }
    }

    // ── Dynamic text filter ───────────────────────────────────────────────────

    private static boolean isDynamicText(String text) {
        if (text == null || text.isBlank()) return true;
        long digits = text.chars().filter(Character::isDigit).count();
        if (digits > text.length() * 0.5) return true;
        if (text.matches(".*[₹$€£¥].*") || text.matches(".*\\d+%.*")) return true;
        if (text.matches("\\d{1,2}:\\d{2}.*")) return true;
        if (text.length() > 40) return true;
        return false;
    }

    // ── YAML helpers ──────────────────────────────────────────────────────────

    private static String escapeYaml(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractTagFromSuggestion(String suggestion) {
        if (suggestion == null) return null;
        Matcher m = Pattern.compile("\"([a-z][a-z0-9_]+)\"").matcher(suggestion);
        return m.find() ? m.group(1) : null;
    }

    // ── Coordinate mapping ────────────────────────────────────────────────────

    private static int rawToScreenX(int r) { return (int)((long) r * screenW / maxRawX); }
    private static int rawToScreenY(int r) { return (int)((long) r * screenH / maxRawY); }

    // ── Screen & input calibration ────────────────────────────────────────────

    private static void detectScreenSize() {
        try {
            String[] p = adbOutput("shell", "wm", "size")
                    .replaceAll("(?i).*size:", "").trim().split("[x\\s]+");
            screenW = Integer.parseInt(p[0].trim());
            screenH = Integer.parseInt(p[1].trim());
        } catch (Exception ignored) {}
    }

    private static void detectInputMaxValues() {
        try {
            String out = adbOutput("shell", "getevent", "-p");
            boolean inX = false, inY = false;
            for (String line : out.split("\n")) {
                if      (line.contains("ABS_MT_POSITION_X")) { inX = true;  inY = false; }
                else if (line.contains("ABS_MT_POSITION_Y")) { inY = true;  inX = false; }
                else                                         { inX = false; inY = false; }
                if ((inX || inY) && line.contains("max")) {
                    String max = extractMax(line);
                    if (max != null) {
                        if (inX) maxRawX = Integer.parseInt(max);
                        if (inY) maxRawY = Integer.parseInt(max);
                    }
                }
            }
        } catch (Exception ignored) { maxRawX = screenW; maxRawY = screenH; }
    }

    private static String extractMax(String line) {
        try {
            String[] p = line.trim().split("[,\\s]+");
            for (int i = 0; i < p.length - 1; i++)
                if ("max".equals(p[i])) return p[i + 1];
        } catch (Exception ignored) {}
        return null;
    }

    // ── Activity / screen name ────────────────────────────────────────────────

    private static String resolveActivity() {
        try {
            String out = adbOutput("shell", "dumpsys", "activity", "activities");
            for (String line : out.split("\n")) {
                if (line.contains("Resumed:") || line.contains("ResumedActivity:")) {
                    if (!line.contains("com.popclub.android")) continue;
                    int slash = line.lastIndexOf('/');
                    int space = line.indexOf(' ', slash);
                    if (slash >= 0 && space > slash) {
                        String cls = line.substring(slash + 1, space).trim()
                                .replaceAll("^\\.", "");
                        return activityToScreenName(cls);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "unknown_screen";
    }

    private static String activityToScreenName(String className) {
        int dot = className.lastIndexOf('.');
        if (dot >= 0) className = className.substring(dot + 1);
        className = className.replaceAll("(?i)(Activity|Fragment|Screen|Classic|Page|View)$", "");
        if (className.isEmpty()) return "home";
        if (className.equalsIgnoreCase("Launcher") || className.equalsIgnoreCase("Main")
                || className.equalsIgnoreCase("Home")) return "home";
        String snake = className.replaceAll("([A-Z])", "_$1").toLowerCase()
                .replaceAll("^_", "").replaceAll("_+", "_");
        return snake.isEmpty() ? "home" : snake;
    }

    // ── Claude CLI check ──────────────────────────────────────────────────────

    private static boolean isClaudeAvailable() {
        try {
            Process p = new ProcessBuilder("which", "claude").start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    // ── Device detection ──────────────────────────────────────────────────────

    private static String detectDevice() throws Exception {
        String out = adbOutput("devices");
        for (String line : out.split("\n"))
            if (line.endsWith("\tdevice")) return line.split("\t")[0].trim();
        throw new RuntimeException("No ADB device connected.");
    }

    // ── ADB helpers ───────────────────────────────────────────────────────────

    private static void adb(String... cmd) throws Exception { adbOutput(cmd); }

    private static String adbOutput(String... cmd) throws Exception {
        List<String> full = new ArrayList<>();
        full.add("adb"); full.add("-s"); full.add(udid);
        Collections.addAll(full, cmd);
        ProcessBuilder pb = new ProcessBuilder(full);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
        }
        p.waitFor();
        return sb.toString();
    }
}
