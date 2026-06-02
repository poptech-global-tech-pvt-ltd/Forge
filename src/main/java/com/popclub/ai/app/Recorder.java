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
 * Passive recorder — screenshot + AI vision + best-selector strategy.
 *
 * On every tap:
 *   1. Takes a screenshot
 *   2. Crops the tapped element
 *   3. Checks accessibilityId → resource-id → text (best-selector)
 *   4. If no tag: sends cropped image to Claude → suggests a tag name
 *
 * Usage:
 *   ./run-recorder.sh 10BDCM0YJZ00043
 *   Type  exit + ENTER  to stop and save.
 *
 * Set ANTHROPIC_API_KEY env var to enable AI tag suggestions.
 */
public class Recorder {

    private static final String REPORTS_DIR = "reports/recorded";

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

    private static final List<Map<String, String>>   steps       = new ArrayList<>();
    private static final List<Map<String, String>>   missingTags = new ArrayList<>();

    // Single-thread executor so taps are processed in order but never block the getevent thread
    private static final ExecutorService tapExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tap-processor");
        t.setDaemon(true);
        return t;
    });

    // ── main ──────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        udid = args.length > 0 ? args[0].trim() : detectDevice();

        String testName = "test_" + LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        sessionDir = REPORTS_DIR + "/" + testName;
        Files.createDirectories(Paths.get(sessionDir + "/elements"));

        detectScreenSize();
        detectInputMaxValues();

        boolean aiEnabled = isClaudeAvailable();
        System.out.println("Device  : " + udid);
        System.out.println("Screen  : " + screenW + "x" + screenH);
        System.out.println("AI vision: " + (aiEnabled ? "✓ enabled (claude CLI)" : "✗ claude CLI not found"));
        System.out.println("\n── Recording started ─────────────────────────────");
        System.out.println("  Use the device normally.");
        System.out.println("  Type  exit + ENTER  to stop and save.");
        System.out.println("──────────────────────────────────────────────────\n");

        // Background: screen name poller (every 3s)
        Thread screenPoller = new Thread(Recorder::pollScreen);
        screenPoller.setDaemon(true);
        screenPoller.start();

        // Background: touch event stream
        Thread eventThread = new Thread(Recorder::listenEvents);
        eventThread.setDaemon(true);
        eventThread.start();

        // Background: flush typed text after idle
        Thread textFlusher = new Thread(Recorder::watchTextInput);
        textFlusher.setDaemon(true);
        textFlusher.start();

        // Save YAML on Ctrl+C or kill — not just on clean "exit"
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { saveYaml(testName); } catch (Exception ignored) {}
        }));

        // Wait for exit
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            String input = reader.readLine();
            if (input == null || "exit".equalsIgnoreCase(input.trim())) break;
        }

        saveYaml(testName);
        System.out.println("Test    → " + sessionDir + "/" + testName + ".yaml  (" + steps.size() + " steps)");
        if (!missingTags.isEmpty())
            System.out.println("Missing → " + sessionDir + "/" + testName + "_missing.yaml  (" + missingTags.size() + " tags)");
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
            // [ timestamp] /dev/input/eventX: EV_TYPE  CODE  VALUE
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
        // Capture screen name at the moment of touch so it doesn't drift while we process
        String screenAtTap = currentScreen;
        // Dispatch off the getevent thread — screenshot + UI dump + Claude can take ~10s
        // and we must not block the event stream or subsequent taps will be lost
        tapExecutor.submit(() -> {
            try {
                if (dx > 60 || Math.abs(dy) > 60) recordScroll(x, y, sx, sy, dx, dy, durationMs);
                else                               recordTap(x, y, durationMs, screenAtTap);
            } catch (Exception e) {
                System.err.println("  [record error] " + e.getMessage());
            }
        });
    }

    // ── Record scroll ─────────────────────────────────────────────────────────

    private static void recordScroll(int x, int y, int sx, int sy,
                                     int dx, int dy, long dur) {
        String action = dur < 300 ? "swipe" : "scroll";
        String dir    = Math.abs(dy) >= dx
                ? (dy < 0 ? "up" : "down")
                : (x > sx ? "right" : "left");

        addStep("action", action, "direction", dir);
        System.out.println("  ↕  " + action + " " + dir);
    }

    // ── Record tap ────────────────────────────────────────────────────────────

    private static void recordTap(int x, int y, long durationMs, String screenAtTap) throws Exception {
        flushPendingText();
        Thread.sleep(300);

        // 1. Take screenshot
        Path screenshot = takeScreenshot();

        // 2. Dump UI
        String xml = getPageSource();
        if (xml == null || xml.isBlank()) {
            System.out.println("  ? tap (" + x + "," + y + ") — no UI dump");
            Files.deleteIfExists(screenshot);
            return;
        }

        List<Map<String, String>> elements = XmlElementParser.parse(xml);
        Map<String, String> hit = findSmallestAt(elements, x, y);

        String action = durationMs > 600 ? "longPress" : "click";
        Map<String, String> step = new LinkedHashMap<>();
        step.put("action", action);

        if (hit != null) {
            String tag    = hit.getOrDefault("accessibilityId", "").trim();
            String text   = hit.getOrDefault("text", "").trim();
            String cls    = hit.getOrDefault("class", "").trim();
            String bounds = hit.getOrDefault("bounds", "").trim();
            int[]  b      = parseBounds(bounds);

            // ── Build locator list (Maestro-style: try all, first match wins) ──
            // In Compose: accessibilityId works, uiautomator text works, resourceId does NOT.
            List<String[]> locatorList = new ArrayList<>(); // [type, value]
            String suggestedTag  = null;
            String pendingLocKey = null;

            if (!tag.isEmpty()) {
                QaTagAnalyzer.TagStatus status = QaTagAnalyzer.checkElement(screenAtTap, tag, text, cls);
                if (status.state == QaTagAnalyzer.TagStatus.State.GOOD) {
                    locatorList.add(new String[]{"accessibilityId", tag});
                    System.out.println("  ✓ " + action + " → tag: " + tag);
                    pendingLocKey = "accessibilityId: " + tag;
                } else {
                    // Bad tag — still usable but flag it; ask AI for better name
                    locatorList.add(new String[]{"accessibilityId", tag});
                    System.out.println("  ⚠ " + action + " → bad tag: \"" + tag + "\"");
                    System.out.println("    " + status.message);
                    String aiTag = null;
                    if (b != null) {
                        String nearby = getNearbyText(elements, b, x, y);
                        aiTag = askClaude(screenshot, screenAtTap, text, cls, nearby, b);
                    }
                    String suggestion = (aiTag != null)
                            ? "const val " + aiTag.toUpperCase() + " = \"" + aiTag + "\""
                            : status.message;
                    if (aiTag != null)
                        System.out.println("    AI → rename to: const val " + aiTag.toUpperCase() + " = \"" + aiTag + "\"");
                    trackMissing(tag, text, cls, bounds, suggestion, screenshot, b);
                    pendingLocKey = "accessibilityId: " + tag;
                }
            } else {
                // No tag — ask AI first (full annotated screenshot for full context)
                // then fall back to rule-based suggestion
                if (b != null) {
                    String nearby = getNearbyText(elements, b, x, y);
                    suggestedTag = askClaude(screenshot, screenAtTap, text, cls, nearby, b);
                    if (suggestedTag != null)
                        System.out.println("    AI → add to TestTags.kt: const val "
                                + suggestedTag.toUpperCase() + " = \"" + suggestedTag + "\"");
                }

                // Rule-based fallback if AI is disabled or returned nothing
                if (suggestedTag == null) {
                    QaTagAnalyzer.TagStatus ruleStatus = QaTagAnalyzer.checkElement(screenAtTap, "", text, cls);
                    if (ruleStatus.message != null)
                        suggestedTag = extractTagFromSuggestion(ruleStatus.message);
                }

                // Build locator chain: placeholder accessibilityId → uiautomator text → point
                if (suggestedTag != null) {
                    locatorList.add(new String[]{"accessibilityId", suggestedTag});
                }

                // uiAutomator text — works in Compose via accessibility tree
                if (!text.isEmpty() && !isDynamicText(text)) {
                    locatorList.add(new String[]{"uiautomator", text});
                    pendingLocKey = "uiautomator: " + text;
                    System.out.println("  ~ " + action + " → text: \"" + text
                            + "\"  (no tag — add: " + suggestedTag + ")");
                } else {
                    // Coordinates — absolute last resort
                    locatorList.add(new String[]{"point", x + "," + y});
                    pendingLocKey = "point: " + x + "," + y;
                    System.out.println("  ✗ " + action + " → (" + x + "," + y
                            + ") — no tag, no text. Add: " + suggestedTag);
                }

                // Track as missing with AI or rule-based suggestion
                String suggestion = (suggestedTag != null)
                        ? "const val " + suggestedTag.toUpperCase() + " = \"" + suggestedTag + "\""
                        : "(could not determine)";
                trackMissing(tag, text, cls, bounds, suggestion, screenshot, b);
            }

            // Write locators list to step YAML
            if (!locatorList.isEmpty()) {
                step.put("_locators_", buildLocatorYaml(locatorList, suggestedTag));
            }

            // EditText → watch for typed text
            if (cls.contains("EditText")) {
                watchingText       = true;
                pendingTextLocator = pendingLocKey;
                lastTouchMillis    = System.currentTimeMillis();
                Files.deleteIfExists(screenshot);
                return;
            }
        } else {
            step.put("x", String.valueOf(x));
            step.put("y", String.valueOf(y));
            System.out.println("  ? " + action + " → (" + x + "," + y + ") — element not found in hierarchy");
        }

        Files.deleteIfExists(screenshot);
        steps.add(step);
    }

    // ── Claude vision API ─────────────────────────────────────────────────────

    /**
     * Uses the local `claude` CLI (Claude Code) to suggest a tag name.
     * Annotates the full screenshot with a red rectangle, saves it to a temp file,
     * then asks Claude to read it via its Read tool — no API key required,
     * uses Claude Code's existing authentication.
     *
     * @param screenshotFull  full-screen PNG
     * @param screenName      activity/screen name for context
     * @param elementText     visible text of the tapped element (may be empty)
     * @param cls             element class
     * @param nearbyContext   comma-separated nearby element text
     * @param elementBounds   [x1,y1,x2,y2] for the red highlight box
     */
    private static String askClaude(Path screenshotFull, String screenName,
                                    String elementText, String cls,
                                    String nearbyContext, int[] elementBounds) {
        try {
            // Draw red highlight on a copy of the full screenshot
            Path annotated = annotateScreenshot(screenshotFull, elementBounds);
            Path imageFile  = (annotated != null) ? annotated : screenshotFull;

            // Build prompt — ask Claude to read the image file and suggest a tag
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

            // --add-dir so Claude's Read tool can access the image (temp files are outside project root)
            String imageDir = imageFile.getParent().toAbsolutePath().toString();

            // Run claude non-interactively:
            //   --permission-mode bypassPermissions  → no "allow Read?" prompts
            //   --add-dir <imageDir>                 → allow reading the temp screenshot
            //   --no-session-persistence             → don't save this to history
            List<String> cmd = new ArrayList<>(Arrays.asList(
                "claude", "--print",
                "--allowedTools", "Read",
                "--permission-mode", "bypassPermissions",
                "--add-dir", imageDir,
                "--no-session-persistence",
                "-p", prompt.toString()
            ));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);          // merge stderr so we can log it on failure
            pb.redirectInput(new File("/dev/null")); // don't wait for stdin
            pb.environment().remove("ANTHROPIC_API_KEY"); // force OAuth auth, ignore stale API key
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append(" ");
            }
            int exitCode = p.waitFor();

            if (annotated != null) Files.deleteIfExists(annotated);

            if (exitCode == 0) {
                // Extract just the tag — strip any surrounding prose Claude might add
                String raw = out.toString().trim();
                Matcher m = Pattern.compile("\\b([a-z][a-z0-9]{2,}(?:_[a-z0-9]+)+)\\b").matcher(raw);
                if (m.find()) return m.group(1);
            } else {
                // Print first line of error so we know what went wrong
                String errLine = out.toString().trim().lines().findFirst().orElse("unknown error");
                System.err.println("    [AI error] claude exited " + exitCode + ": " + errLine);
            }
        } catch (Exception e) {
            System.err.println("    [AI error] " + e.getMessage());
        }
        return null;
    }

    /**
     * Draws a red rectangle around {@code bounds} on a copy of the screenshot.
     * Returns null if drawing fails (caller should fall back to unannotated screenshot).
     */
    private static Path annotateScreenshot(Path screenshot, int[] bounds) {
        if (screenshot == null || bounds == null) return null;
        try {
            BufferedImage img = ImageIO.read(screenshot.toFile());
            if (img == null) return null;

            // Work on a copy so we don't mutate the original
            BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(img, 0, 0, null);

            // Red highlight box — 3px border, semi-transparent fill
            int x1 = Math.max(0, bounds[0]);
            int y1 = Math.max(0, bounds[1]);
            int x2 = Math.min(img.getWidth(),  bounds[2]);
            int y2 = Math.min(img.getHeight(), bounds[3]);
            int w  = x2 - x1;
            int h  = y2 - y1;
            if (w > 0 && h > 0) {
                g.setColor(new Color(255, 0, 0, 60));   // translucent red fill
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

    /**
     * Collects text from elements near the tapped element to give Claude
     * screen context (section headers, labels, button text around it).
     */
    private static String getNearbyText(List<Map<String, String>> elements, int[] targetBounds,
                                        int tapX, int tapY) {
        if (elements == null || targetBounds == null) return "";
        int cx = (targetBounds[0] + targetBounds[2]) / 2;
        int cy = (targetBounds[1] + targetBounds[3]) / 2;
        int radius = 400; // pixels — pick up section headers / nearby labels

        List<String> nearby = new ArrayList<>();
        for (Map<String, String> el : elements) {
            String txt = el.getOrDefault("text", "").trim();
            if (txt.isEmpty() || isDynamicText(txt) || txt.length() > 50) continue;
            int[] b = parseBounds(el.getOrDefault("bounds", ""));
            if (b == null) continue;
            int ecx = (b[0] + b[2]) / 2;
            int ecy = (b[1] + b[3]) / 2;
            int dist = (int) Math.sqrt((double)(ecx - cx) * (ecx - cx) + (double)(ecy - cy) * (ecy - cy));
            if (dist > 0 && dist <= radius) nearby.add(txt);
        }
        // Deduplicate and cap at 10 items
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(nearby));
        if (unique.size() > 10) unique = unique.subList(0, 10);
        return String.join(", ", unique);
    }

    private static String parseClaudeTextResponse(String json) {
        // Simple extraction: "text": "value"
        Matcher m = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        List<String> texts = new ArrayList<>();
        while (m.find()) texts.add(m.group(1));
        // Last text block is the assistant reply
        return texts.isEmpty() ? "" : texts.get(texts.size() - 1);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ── Screenshot & crop ─────────────────────────────────────────────────────

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

    private static Path cropElement(Path screenshot, int[] bounds) {
        try {
            BufferedImage img = ImageIO.read(screenshot.toFile());
            if (img == null) return null;
            int x = Math.max(0, bounds[0]);
            int y = Math.max(0, bounds[1]);
            int w = Math.min(bounds[2] - bounds[0], img.getWidth()  - x);
            int h = Math.min(bounds[3] - bounds[1], img.getHeight() - y);
            if (w <= 0 || h <= 0) return null;
            BufferedImage cropped = img.getSubimage(x, y, w, h);
            Path out = Files.createTempFile("element_", ".png");
            ImageIO.write(cropped, "PNG", out.toFile());
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Dynamic text filter ───────────────────────────────────────────────────

    /** Returns true if the text is price / timer / counter — not stable for locators */
    private static boolean isDynamicText(String text) {
        if (text == null || text.isBlank()) return true;
        // Mostly digits (counters, prices, OTP)
        long digits = text.chars().filter(Character::isDigit).count();
        if (digits > text.length() * 0.5) return true;
        // Price patterns: ₹480, $10, 20% off
        if (text.matches(".*[₹$€£¥].*") || text.matches(".*\\d+%.*")) return true;
        // Timer pattern: 00:24, 1:30
        if (text.matches("\\d{1,2}:\\d{2}.*")) return true;
        // Too long (dynamic descriptions)
        if (text.length() > 40) return true;
        return false;
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
                Map<String, String> step = new LinkedHashMap<>();
                step.put("action", "enterText");
                if (pendingTextLocator != null) {
                    String[] kv = pendingTextLocator.split(": ", 2);
                    if (kv.length == 2) step.put(kv[0], kv[1]);
                }
                step.put("value", typedText);
                steps.add(step);
                System.out.println("  ✓ enterText → \"" + typedText + "\"");
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

        // Save element screenshot
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

    // ── Locator helpers ───────────────────────────────────────────────────────

    /**
     * Builds the inline YAML block for locators.
     * Stored as a single string in the step map under "_locators_" key,
     * then written verbatim during saveYaml.
     *
     * Output example:
     *   locators:
     *     - type: accessibilityId
     *       value: home_search_bar     # add to TestTags.kt
     *     - type: uiautomator
     *       value: Search
     */
    private static String buildLocatorYaml(List<String[]> locators, String suggestedTag) {
        StringBuilder sb = new StringBuilder();
        sb.append("locators:\n");
        for (int i = 0; i < locators.size(); i++) {
            String[] loc = locators.get(i);
            sb.append("    - type: ").append(loc[0]).append("\n");
            sb.append("      value: ").append(loc[1]);
            // Annotate the suggested accessibilityId so devs know to add the tag
            if (i == 0 && "accessibilityId".equals(loc[0]) && suggestedTag != null
                    && loc[1].equals(suggestedTag)) {
                sb.append("  # add to TestTags.kt");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /** Extracts the tag string from "Add to TestTags.kt: const val HOME_FOO = \"home_foo\"" */
    private static String extractTagFromSuggestion(String suggestion) {
        if (suggestion == null) return null;
        Matcher m = Pattern.compile("\"([a-z][a-z0-9_]+)\"").matcher(suggestion);
        return m.find() ? m.group(1) : null;
    }

    // ── YAML save ─────────────────────────────────────────────────────────────

    private static void saveYaml(String testName) throws Exception {
        // Test YAML
        Path out = Paths.get(sessionDir, testName + ".yaml");
        StringBuilder sb = new StringBuilder();
        sb.append("# Recorded : ").append(testName).append("\n");
        sb.append("# Date     : ")
          .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
          .append("\n\n");
        sb.append("steps:\n");
        for (Map<String, String> step : steps) {
            sb.append("  - action: ").append(step.get("action")).append("\n");
            for (Map.Entry<String, String> e : step.entrySet()) {
                if ("action".equals(e.getKey())) continue;
                if ("_locators_".equals(e.getKey())) {
                    // Write locator block indented under the step
                    for (String line : e.getValue().split("\n"))
                        sb.append("    ").append(line).append("\n");
                } else {
                    sb.append("    ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
                }
            }
        }
        Files.writeString(out, sb.toString());

        // Missing tags YAML
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
                    // Only track our app — ignore Android launcher / system UI
                    if (!line.contains("com.popclub.android")) continue;
                    // Format: ActivityRecord{... com.popclub.android/.SomeActivity t144 d0}
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

    /**
     * Converts an Activity class name to a readable snake_case screen name for Claude context.
     * e.g. "LauncherClassic" → "home", "CheckoutActivity" → "checkout",
     *      "UpiPaymentFragment" → "upi_payment"
     */
    private static String activityToScreenName(String className) {
        // Strip package if present
        int dot = className.lastIndexOf('.');
        if (dot >= 0) className = className.substring(dot + 1);
        // Strip common suffixes
        className = className.replaceAll("(?i)(Activity|Fragment|Screen|Classic|Page|View)$", "");
        if (className.isEmpty()) return "home";
        // Special-case well-known names
        if (className.equalsIgnoreCase("Launcher") || className.equalsIgnoreCase("Main")
                || className.equalsIgnoreCase("Home")) return "home";
        // PascalCase → snake_case
        String snake = className.replaceAll("([A-Z])", "_$1").toLowerCase()
                .replaceAll("^_", "").replaceAll("_+", "_");
        return snake.isEmpty() ? "home" : snake;
    }

    // ── Claude CLI check ─────────────────────────────────────────────────────

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

    // ── Step helper ───────────────────────────────────────────────────────────

    private static void addStep(String... kv) {
        Map<String, String> step = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) step.put(kv[i], kv[i + 1]);
        steps.add(step);
    }
}
