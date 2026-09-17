package com.popclub.ai.app;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

/**
 * TagPatcher — reads a _missing.yaml and:
 *
 *  1. Adds missing const val entries to TestTags.kt (grouped by screen section)
 *  2. Uses Claude Code CLI to read the element screenshot + source files,
 *     identify the exact Composable line, and apply .qaTestTag() automatically
 *  3. Falls back to simple text-match for elements that have stable text
 *  4. Prints clear summary of what was patched and what still needs manual review
 *
 * Usage:
 *   java TagPatcher [path/to/_missing.yaml]   ← explicit yaml
 *   java TagPatcher                            ← auto-picks latest in reports/
 */
public class TagPatcher {

    // ── Paths ──────────────────────────────────────────────────────────────────
    private static final String POPDROID       = "../popdroid";
    private static final String TEST_TAGS_FILE =
        POPDROID + "/core/src/main/java/com/popclub/core/TestTags.kt";

    // Maps snake_case screen prefix → source directory to search for Composables
    private static final Map<String, String> SCREEN_DIRS = new LinkedHashMap<>();
    static {
        SCREEN_DIRS.put("launcher_fresh",  "pop/src/main/java/com/popclub/android");
        SCREEN_DIRS.put("login",           "pop/src/main/java/com/popclub/android");
        SCREEN_DIRS.put("home",            "feature/home/src/main/java");
        SCREEN_DIRS.put("cart",            "feature/ecom/src/main/java/com/pop/ecom/cart");
        SCREEN_DIRS.put("checkout",        "feature/ecom/src/main/java/com/pop/ecom/cart");
        SCREEN_DIRS.put("shop_payment",    "feature/ecom/src/main/java/com/pop/ecom/cart/payment");
        SCREEN_DIRS.put("product_details", "feature/ecom/src/main/java/com/pop/ecom");
        SCREEN_DIRS.put("product_list",    "feature/ecom/src/main/java/com/pop/ecom");
        SCREEN_DIRS.put("search",          "feature/ecom/src/main/java/com/pop/ecom/search");
        SCREEN_DIRS.put("add_address",     "feature/ecom/src/main/java/com/pop/ecom/address");
        SCREEN_DIRS.put("address",         "feature/ecom/src/main/java/com/pop/ecom/address");
        SCREEN_DIRS.put("order_details",   "feature/ecom/src/main/java/com/pop/ecom/presentation/ui/orders");
        SCREEN_DIRS.put("orders",          "feature/ecom/src/main/java/com/pop/ecom/presentation/ui/orders");
        SCREEN_DIRS.put("wishlist",        "feature/ecom/src/main/java/com/pop/ecom/wishlist");
        SCREEN_DIRS.put("profile",         "feature/home/src/main/java");
        SCREEN_DIRS.put("rewards",         "feature/rewards/src/main/java");
        SCREEN_DIRS.put("upi",             "feature/upi/src/main/java");
    }

    // ── Entry point ────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        String yamlPath = args.length > 0 ? args[0] : findLatestMissingYaml();
        if (yamlPath == null) {
            System.out.println("No _missing.yaml found. Run the recorder first.");
            System.exit(1);
        }
        System.out.println("Patching from: " + yamlPath);
        System.out.println();

        List<MissingEntry> entries = parseMissingYaml(yamlPath);
        if (entries.isEmpty()) {
            System.out.println("No missing tags found in YAML.");
            return;
        }

        System.out.println("Found " + entries.size() + " missing tag(s)\n");

        int addedToTags  = addToTestTags(entries);
        int patchedFiles = applyToComposables(entries, yamlPath);

        System.out.println("\n── Summary ───────────────────────────────────────");
        System.out.println("  TestTags.kt  : +" + addedToTags + " constants added");
        System.out.println("  Composables  : " + patchedFiles + " element(s) auto-tagged");
        int remaining = (int) entries.stream().filter(e -> !e.autoPatched).count();
        if (remaining > 0) {
            System.out.println("  Manual review: " + remaining + " element(s):");
            for (MissingEntry e : entries) {
                if (!e.autoPatched) {
                    Matcher cm = Pattern.compile("const val (\\w+)").matcher(e.suggestion != null ? e.suggestion : "");
                    String name = cm.find() ? cm.group(1) : "?";
                    System.out.println("    - " + name + "  (screen: " + e.screen
                            + ", element: " + e.element + ")");
                }
            }
        }
        System.out.println("──────────────────────────────────────────────────");
    }

    // ── Parse _missing.yaml ───────────────────────────────────────────────────

    static List<MissingEntry> parseMissingYaml(String path) throws Exception {
        List<MissingEntry> list = new ArrayList<>();
        String currentScreen = "unknown";
        MissingEntry current = null;

        for (String line : Files.readAllLines(Paths.get(path))) {
            String t = line.trim();
            Matcher sm = Pattern.compile("#\\s*──+\\s*(\\S+)\\s*──+").matcher(t);
            if (sm.find()) { currentScreen = sm.group(1); continue; }

            if (t.startsWith("- element:")) {
                current = new MissingEntry();
                current.screen = currentScreen;
                current.element = t.substring("- element:".length()).trim();
                list.add(current);
            } else if (current != null) {
                if (t.startsWith("class:"))      current.cls        = t.substring("class:".length()).trim();
                if (t.startsWith("bounds:"))     current.bounds     = t.substring("bounds:".length()).trim();
                if (t.startsWith("suggestion:")) current.suggestion = t.substring("suggestion:".length()).trim();
                if (t.startsWith("screenshot:")) current.screenshot = t.substring("screenshot:".length()).trim();
            }
        }

        list.removeIf(e -> e.suggestion == null || !e.suggestion.startsWith("const val"));
        return list;
    }

    // ── Add constants to TestTags.kt ──────────────────────────────────────────

    static int addToTestTags(List<MissingEntry> entries) throws Exception {
        Path tagsFile = Paths.get(TEST_TAGS_FILE);
        if (!Files.exists(tagsFile)) {
            System.err.println("  ERROR: TestTags.kt not found at " + tagsFile.toAbsolutePath());
            return 0;
        }

        String content = Files.readString(tagsFile);
        int added = 0;

        Map<String, List<MissingEntry>> byScreen = new LinkedHashMap<>();
        for (MissingEntry e : entries) {
            String prefix = screenPrefix(e.suggestion);
            byScreen.computeIfAbsent(prefix, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<MissingEntry>> group : byScreen.entrySet()) {
            String screenPrefix  = group.getKey();
            String sectionHeader = "// ─── " + toTitleCase(screenPrefix) + " ";

            StringBuilder toAdd = new StringBuilder();
            for (MissingEntry e : group.getValue()) {
                String constLine = "    " + e.suggestion.trim();
                if (!content.contains(constLine.trim())) {
                    toAdd.append(constLine).append("\n");
                    added++;
                }
            }
            if (toAdd.length() == 0) continue;

            int insertAt = content.lastIndexOf(sectionHeader);
            if (insertAt >= 0) {
                int nextSection = content.indexOf("\n    // ─", insertAt + sectionHeader.length());
                int insertPos   = nextSection > 0 ? nextSection : content.lastIndexOf("}");
                content = content.substring(0, insertPos) + toAdd + content.substring(insertPos);
            } else {
                int closingBrace = content.lastIndexOf("}");
                String newSection = "\n    // ─── " + toTitleCase(screenPrefix)
                        + " ──────────────────────────────────────────────────────\n"
                        + toAdd;
                content = content.substring(0, closingBrace) + newSection + content.substring(closingBrace);
            }
            System.out.println("  TestTags.kt +" + group.getValue().size() + " tags for: " + screenPrefix);
        }

        if (added > 0) Files.writeString(tagsFile, content);
        return added;
    }

    // ── Apply .qaTestTag() to Composables ─────────────────────────────────────

    static int applyToComposables(List<MissingEntry> entries, String yamlPath) throws Exception {
        // Resolve the session directory (where screenshots live)
        Path sessionDir = Paths.get(yamlPath).getParent();
        int patched = 0;

        for (MissingEntry e : entries) {
            if (e.suggestion == null || !e.suggestion.startsWith("const val")) continue;

            Matcher cm = Pattern.compile("const val (\\w+)\\s*=\\s*\"([^\"]+)\"").matcher(e.suggestion);
            if (!cm.find()) continue;
            String constName = cm.group(1);
            String tagValue  = cm.group(2);
            String qaTag     = ".qaTestTag(TestTags." + constName + ")";

            List<Path> files = findComposableFiles(e.screen);
            if (files.isEmpty()) {
                System.out.println("  ✗ No source dir found for screen: " + e.screen
                        + " → " + constName);
                continue;
            }

            System.out.println("\n  ── " + constName + " (" + e.screen + ") ──");

            // ── Strategy 1: simple text match (fast, no AI needed) ────────────
            boolean usedSimple = false;
            if (e.element != null && !e.element.isBlank() && !e.element.equals("(unknown)")) {
                List<MatchLocation> matches = findTextInFiles(files, e.element);
                if (matches.size() == 1) {
                    MatchLocation loc = matches.get(0);
                    if (!loc.line.contains("qaTestTag")) {
                        String patched2 = patchLine(loc.line, qaTag);
                        if (patched2 != null) {
                            applyLinePatch(loc.file, loc.lineNumber, loc.line, patched2);
                            System.out.println("  ✓ Text-match → " + loc.file.getFileName()
                                    + ":" + loc.lineNumber);
                            e.autoPatched = true;
                            patched++;
                            usedSimple = true;
                        }
                    } else {
                        System.out.println("  ✓ Already tagged: " + loc.file.getFileName()
                                + ":" + loc.lineNumber);
                        e.autoPatched = true;
                        usedSimple = true;
                    }
                } else if (matches.size() > 1) {
                    System.out.println("  ~ Text-match ambiguous (" + matches.size()
                            + " hits) — asking Claude...");
                }
            }

            if (usedSimple) continue;

            // ── Strategy 2: ask Claude (screenshot + source files) ────────────
            if (!isClaudeAvailable()) {
                System.out.println("  ✗ Claude not available — manual: add "
                        + qaTag + " to " + e.screen + " Composable");
                continue;
            }

            // Resolve screenshot absolute path
            Path screenshotPath = null;
            if (e.screenshot != null && !e.screenshot.isBlank()) {
                Path candidate = sessionDir.resolve(e.screenshot);
                if (Files.exists(candidate)) screenshotPath = candidate.toAbsolutePath();
            }

            ClaudePatch cp = askClaudeForPatch(e, constName, tagValue, files, screenshotPath);

            if (cp != null && cp.file != null && cp.originalLine != null && cp.patchedLine != null) {
                Path target = Paths.get(cp.file);
                if (!Files.exists(target)) {
                    System.out.println("  ✗ Claude returned non-existent file: " + cp.file);
                    continue;
                }
                List<String> lines = Files.readAllLines(target);
                // Find the line (use line number hint or search for original_line)
                int lineIdx = -1;
                if (cp.lineNumber > 0 && cp.lineNumber <= lines.size()
                        && lines.get(cp.lineNumber - 1).trim().equals(cp.originalLine.trim())) {
                    lineIdx = cp.lineNumber - 1;
                } else {
                    // Line number may be off — search for exact content
                    for (int i = 0; i < lines.size(); i++) {
                        if (lines.get(i).trim().equals(cp.originalLine.trim())) {
                            lineIdx = i;
                            break;
                        }
                    }
                }

                if (lineIdx >= 0) {
                    // Preserve original indentation
                    String indent = lines.get(lineIdx).replaceAll("\\S.*", "");
                    lines.set(lineIdx, indent + cp.patchedLine.trim());
                    Files.writeString(target, String.join("\n", lines) + "\n");
                    System.out.println("  ✓ Claude-patch → "
                            + target.getFileName() + ":" + (lineIdx + 1));
                    e.autoPatched = true;
                    patched++;
                } else {
                    System.out.println("  ✗ Claude patch line not found in file: "
                            + target.getFileName());
                    System.out.println("    Expected: " + cp.originalLine);
                }
            } else if (cp != null && cp.error != null) {
                System.out.println("  ✗ Claude: " + cp.error);
            } else {
                System.out.println("  ✗ Claude returned no patch — manual: add "
                        + qaTag + " in " + e.screen + " Composable");
            }
        }
        return patched;
    }

    // ── Ask Claude to find + patch the Composable line ────────────────────────

    /**
     * Sends a structured prompt to Claude Code CLI with:
     *  - The element screenshot (annotated)
     *  - The screen name and tag to add
     *  - All Composable source files for that screen
     *
     * Claude reads the screenshot and source files, finds the exact line,
     * and returns JSON with the file path + original/patched line content.
     */
    private static ClaudePatch askClaudeForPatch(MissingEntry e, String constName,
                                                  String tagValue, List<Path> sourceFiles,
                                                  Path screenshotPath) {
        try {
            // Build a list of source files to give Claude (limit to most relevant)
            List<Path> relevantFiles = sourceFiles.stream()
                    .filter(p -> !p.getFileName().toString().contains("Test"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .limit(20)
                    .collect(Collectors.toList());

            StringBuilder prompt = new StringBuilder();
            prompt.append("You are patching an Android Jetpack Compose codebase to add test accessibility tags.\n\n");

            // Screenshot context
            if (screenshotPath != null && Files.exists(screenshotPath)) {
                prompt.append("STEP 1: Use the Read tool to view this element screenshot:\n");
                prompt.append(screenshotPath.toAbsolutePath()).append("\n\n");
            }

            // Element info
            prompt.append("ELEMENT TO TAG:\n");
            prompt.append("  Screen      : ").append(e.screen).append("\n");
            prompt.append("  Element text: ").append(e.element != null ? e.element : "(none)").append("\n");
            prompt.append("  Class       : ").append(e.cls != null ? e.cls : "(unknown)").append("\n");
            prompt.append("  Bounds      : ").append(e.bounds != null ? e.bounds : "(unknown)").append("\n");
            prompt.append("  Tag to add  : TestTags.").append(constName).append(" = \"").append(tagValue).append("\"\n\n");

            // Source files
            prompt.append("STEP 2: Read these Kotlin source files to find the Composable that renders this element:\n");
            for (Path f : relevantFiles) {
                prompt.append("  ").append(f.toAbsolutePath()).append("\n");
            }

            prompt.append("\nFINDING THE RIGHT LINE:\n");
            prompt.append("Look for a Composable (Box, Column, Row, Image, Text, Button, etc.) that matches:\n");
            prompt.append("- The element class (").append(e.cls != null ? e.cls : "any").append(")\n");
            prompt.append("- The element text (").append(e.element != null ? e.element : "none — use screenshot").append(")\n");
            prompt.append("- Has a Modifier or modifier parameter where .qaTestTag() can be added\n\n");

            prompt.append("HOW TO ADD THE TAG:\n");
            prompt.append("The tag is added using `.qaTestTag(TestTags.").append(constName).append(")` on a Modifier.\n");
            prompt.append("Examples:\n");
            prompt.append("  Modifier.fillMaxWidth()  →  Modifier.fillMaxWidth().qaTestTag(TestTags.").append(constName).append(")\n");
            prompt.append("  modifier = Modifier.clip(...)  →  modifier = Modifier.clip(...).qaTestTag(TestTags.").append(constName).append(")\n");
            prompt.append("  If no Modifier exists on the Composable, add: modifier = Modifier.qaTestTag(TestTags.").append(constName).append(")\n\n");

            prompt.append("STEP 3: Reply with ONLY this JSON (no other text, no markdown):\n");
            prompt.append("{\n");
            prompt.append("  \"file\": \"/absolute/path/to/File.kt\",\n");
            prompt.append("  \"line_number\": 145,\n");
            prompt.append("  \"original_line\": \"exact content of the line to modify (copy it exactly)\",\n");
            prompt.append("  \"patched_line\": \"exact content with .qaTestTag(TestTags.").append(constName).append(") added\"\n");
            prompt.append("}\n\n");
            prompt.append("If you cannot find the right location with high confidence, reply:\n");
            prompt.append("{\"error\": \"reason why not found\"}\n");

            // Collect all directories Claude needs access to
            Set<String> dirs = new LinkedHashSet<>();
            dirs.add(Paths.get(POPDROID).toAbsolutePath().toString());
            if (screenshotPath != null)
                dirs.add(screenshotPath.getParent().toAbsolutePath().toString());

            List<String> cmd = new ArrayList<>(Arrays.asList(
                "claude", "--print",
                "--allowedTools", "Read",
                "--permission-mode", "bypassPermissions",
                "--no-session-persistence"
            ));
            for (String dir : dirs) {
                cmd.add("--add-dir");
                cmd.add(dir);
            }
            cmd.add("-p");
            cmd.add(prompt.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            pb.redirectInput(new File("/dev/null"));
            pb.environment().remove("ANTHROPIC_API_KEY");
            Process p = pb.start();

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append("\n");
            }
            int exitCode = p.waitFor();

            if (exitCode != 0) {
                String err = out.toString().trim().lines().findFirst().orElse("unknown error");
                System.err.println("    [Claude error] " + err);
                return null;
            }

            return parseClaudePatch(out.toString());

        } catch (Exception ex) {
            System.err.println("    [Claude error] " + ex.getMessage());
            return null;
        }
    }

    // ── Parse Claude's JSON response ──────────────────────────────────────────

    private static ClaudePatch parseClaudePatch(String response) {
        // Extract the first {...} JSON block from Claude's response
        Matcher jsonMatcher = Pattern.compile("\\{[^{}]*\\}", Pattern.DOTALL).matcher(response);
        if (!jsonMatcher.find()) return null;
        String json = jsonMatcher.group();

        ClaudePatch cp = new ClaudePatch();

        // Check for error field
        Matcher errM = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (errM.find()) { cp.error = errM.group(1); return cp; }

        Matcher fileM    = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        Matcher lineNumM = Pattern.compile("\"line_number\"\\s*:\\s*(\\d+)").matcher(json);
        Matcher origM    = Pattern.compile("\"original_line\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        Matcher patchM   = Pattern.compile("\"patched_line\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);

        if (fileM.find())    cp.file         = fileM.group(1).replace("\\\"", "\"");
        if (lineNumM.find()) cp.lineNumber    = Integer.parseInt(lineNumM.group(1));
        if (origM.find())    cp.originalLine  = origM.group(1).replace("\\\"", "\"").replace("\\n", "\n");
        if (patchM.find())   cp.patchedLine   = patchM.group(1).replace("\\\"", "\"").replace("\\n", "\n");

        return (cp.file != null && cp.originalLine != null && cp.patchedLine != null) ? cp : null;
    }

    // ── Find Composable files for a screen ───────────────────────────────────

    private static List<Path> findComposableFiles(String screen) throws Exception {
        String dir = null;
        for (Map.Entry<String, String> entry : SCREEN_DIRS.entrySet()) {
            if (screen.startsWith(entry.getKey())) { dir = entry.getValue(); break; }
        }
        if (dir == null) dir = "";

        Path base = Paths.get(POPDROID, dir);
        if (!Files.exists(base)) return Collections.emptyList();

        return Files.walk(base)
                .filter(p -> p.toString().endsWith(".kt"))
                .filter(p -> !p.toString().contains("/test/"))
                .filter(p -> !p.toString().contains("Test.kt"))
                .collect(Collectors.toList());
    }

    // ── Simple text match (fast path, no AI) ─────────────────────────────────

    private static List<MatchLocation> findTextInFiles(List<Path> files, String text) throws Exception {
        List<MatchLocation> matches = new ArrayList<>();
        String escaped = Pattern.quote(text);
        Pattern p = Pattern.compile(
                "(?:Text\\s*\\(\\s*\"|text\\s*=\\s*\"|\")" + escaped + "\"");

        for (Path file : files) {
            List<String> lines = Files.readAllLines(file);
            for (int i = 0; i < lines.size(); i++) {
                if (p.matcher(lines.get(i)).find()) {
                    matches.add(new MatchLocation(file, i + 1, lines.get(i)));
                }
            }
        }
        return matches;
    }

    // ── Patch a line to add .qaTestTag() ─────────────────────────────────────

    private static String patchLine(String line, String qaTag) {
        if (line.contains("Modifier.") && !line.contains("qaTestTag")) {
            return line.replaceFirst("(Modifier(?:\\.[a-zA-Z0-9_]+\\([^)]*\\))*)", "$1" + qaTag);
        }
        if (line.matches(".*Text\\s*\\(\"[^\"]+\"\\s*\\).*")) {
            return line.replaceFirst("(Text\\s*\\(\"[^\"]+\")(\\s*\\))",
                    "$1, modifier = Modifier" + qaTag + "$2");
        }
        return null;
    }

    private static void applyLinePatch(Path file, int lineNumber,
                                       String original, String replacement) throws Exception {
        List<String> lines = new ArrayList<>(Files.readAllLines(file));
        lines.set(lineNumber - 1, replacement);
        Files.writeString(file, String.join("\n", lines) + "\n");
    }

    // ── Auto-find latest _missing.yaml (searches both recorded/ and maestro/) ─

    private static String findLatestMissingYaml() throws Exception {
        List<Path> candidates = new ArrayList<>();
        for (String dir : Arrays.asList("reports/recorded", "reports/maestro")) {
            Path base = Paths.get(dir);
            if (!Files.exists(base)) continue;
            Files.walk(base, 2)
                    .filter(p -> p.getFileName().toString().endsWith("_missing.yaml"))
                    .forEach(candidates::add);
        }
        return candidates.stream()
                .max(Comparator.comparing(p -> p.getFileName().toString()))
                .map(Path::toString)
                .orElse(null);
    }

    // ── Claude CLI availability ───────────────────────────────────────────────

    private static boolean isClaudeAvailable() {
        try {
            Process p = new ProcessBuilder("which", "claude").start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String screenPrefix(String suggestion) {
        Matcher m = Pattern.compile("const val ([A-Z]+)_").matcher(suggestion);
        if (m.find()) return m.group(1).toLowerCase();
        return "other";
    }

    private static String toTitleCase(String s) {
        return Arrays.stream(s.split("_"))
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    static class MissingEntry {
        String screen, element, cls, bounds, suggestion, screenshot;
        boolean autoPatched = false;
    }

    static class MatchLocation {
        Path file; int lineNumber; String line;
        MatchLocation(Path f, int n, String l) { file = f; lineNumber = n; line = l; }
    }

    static class ClaudePatch {
        String file, originalLine, patchedLine, error;
        int lineNumber;
    }
}
