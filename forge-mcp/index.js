#!/usr/bin/env node
/**
 * Forge MCP Server
 *
 * forge_get_hierarchy — dumps live Android screen UI, extracts every
 * interactable element, and maps them to Forge YAML locator strategies:
 *
 *   Tier 1 ✅  REGISTERED tag     → element: cart_checkout_button
 *   Tier 2 ⚠️  UNREGISTERED tag   → locator: some_tag  (add to elements.yaml)
 *   Tier 3 💬  Text only           → text: "Add to Cart"
 *   Tier 4 📍  No tag / no text    → x: 540  y: 1200  (coordinate tap, last resort)
 *              + suggested testTag → const val SUGGEST_TAG = "screen_element_name"
 *
 * NOTE: Resource-id (Tier 4 in View system) is intentionally omitted.
 * POP is a Jetpack Compose app — Compose does not generate resource-ids.
 * Only testTag() (content-desc) and text() semantics exist in the hierarchy.
 */

import { McpServer }          from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z }                  from "zod";
import { spawnSync, spawn }   from "child_process";
import { readFileSync, readdirSync, appendFileSync, existsSync, mkdirSync, writeFileSync, copyFileSync, renameSync } from "fs";
import { join, dirname, basename }  from "path";
import { fileURLToPath }      from "url";
import { parseStringPromise } from "xml2js";
import { parse as parseYaml, stringify as stringifyYaml } from "yaml";

const __dirname    = dirname(fileURLToPath(import.meta.url));
const FORGE_ROOT   = join(__dirname, "..");
const ELEMENTS_DIR = join(FORGE_ROOT, "src/test/resources/testdata/elements");

// ── ADB helper ────────────────────────────────────────────────────────────────

function adb(...args) {
  const r = spawnSync("adb", args, { encoding: "utf8", timeout: 15000 });
  if (r.error) throw new Error("adb error: " + r.error.message);
  return r.stdout || "";
}

function detectDevice() {
  for (const line of adb("devices").split("\n")) {
    if (line.endsWith("\tdevice")) return line.split("\t")[0].trim();
  }
  throw new Error("No ADB device connected.");
}

// ── Load known tags from elements/*.yaml ──────────────────────────────────────
// Returns Map: accessibilityId value → { key, feature }

function loadKnownTags() {
  const map = new Map();
  try {
    const files = readdirSync(ELEMENTS_DIR).filter(f => f.endsWith(".yaml"));
    for (const file of files) {
      const feature = file.replace(".yaml", "");
      const raw = readFileSync(join(ELEMENTS_DIR, file), "utf8");
      let currentKey = null, inAndroid = false, isAccId = false;
      for (const line of raw.split("\n")) {
        const topKey = line.match(/^([a-zA-Z_][a-zA-Z0-9_]*):\s*$/);
        if (topKey) { currentKey = topKey[1]; inAndroid = false; isAccId = false; continue; }
        if (line.match(/^\s{2}android:/)) { inAndroid = true;  continue; }
        if (line.match(/^\s{2}ios:/))     { inAndroid = false; continue; }
        if (inAndroid && line.includes("type: accessibilityId")) { isAccId = true; continue; }
        if (inAndroid && isAccId && line.includes("value:")) {
          const val = line.replace(/.*value:\s*/, "").replace(/#.*/, "").trim();
          if (val && currentKey) map.set(val, { key: currentKey, feature });
          isAccId = false;
        }
      }
    }
  } catch (_) {}
  return map;
}

// ── Guess which elements/*.yaml a tag belongs to ──────────────────────────────
// Uses the tag's prefix to match a known feature file.
// e.g. "cart_checkout_button" → shop.yaml  (cart is part of shop flow)
//      "home_search_bar"      → home.yaml
//      "profile_edit_name"    → profile.yaml

const FEATURE_PREFIXES = {
  home:        "home",
  shop:        "shop",
  cart:        "shop",
  product:     "shop",
  pdp:         "shop",
  search:      "shop",
  checkout:    "shop",
  order:       "shop",
  wishlist:    "shop",
  profile:     "profile",
  account:     "profile",
  rewards:     "rewards",
  reward:      "rewards",
  cashback:    "rewards",
  login:       "login",
  otp:         "login",
  onboarding:  "login",
  billpay:     "billpay",
  bill:        "billpay",
  upi:         "upi",
  credit:      "credit_card",
  card:        "credit_card",
  emi:         "credit_card",
  common:      "common",
};

function guessFeature(tag) {
  const prefix = tag.split("_")[0].toLowerCase();
  return FEATURE_PREFIXES[prefix] || "common";
}

// ── Append a tag to elements/<feature>.yaml ───────────────────────────────────
// Writes the standard 5-line block at the end of the file.
// Returns { feature, filePath, elementKey, alreadyExists }

function registerElement(tag, featureOverride) {
  const feature  = featureOverride || guessFeature(tag);
  const filePath = join(ELEMENTS_DIR, `${feature}.yaml`);
  const elementKey = tag; // element key = tag name (convention in Forge)

  if (!existsSync(filePath)) {
    return { error: `Feature file not found: ${feature}.yaml` };
  }

  // Check if already registered (tag value appears in file)
  const existing = readFileSync(filePath, "utf8");
  if (existing.includes(`value: ${tag}`)) {
    return { feature, filePath, elementKey, alreadyExists: true };
  }

  // Append to file
  const block = `\n${elementKey}:\n  android:\n    - type: accessibilityId\n      value: ${tag}\n  ios:\n\n`;
  appendFileSync(filePath, block, "utf8");

  return { feature, filePath, elementKey, alreadyExists: false };
}

// ── Parse UI hierarchy XML ────────────────────────────────────────────────────
// Returns every visible, interactable leaf node with all attributes.

async function parseHierarchy(xml) {
  const parsed = await parseStringPromise(xml, { explicitArray: true });
  const results = [];

  function walk(node) {
    if (!node) return;
    const a = node.$ || {};

    const tag       = (a["content-desc"] || "").trim();
    const text      = (a["text"]         || "").trim();
    const cls       = (a["class"]        || "").split(".").pop();
    const bounds    = (a["bounds"]       || "").trim();
    const clickable = a["clickable"] === "true";
    const displayed = a["displayed"] !== "false";
    const focusable = a["focusable"]  === "true";

    // Jetpack Compose: no resource-ids. Only content-desc (testTag) and text exist.
    // Include visible elements that have a tag, text, or are interactable.
    if (displayed && (tag || text || clickable || focusable)) {
      results.push({ tag, text, cls, bounds, clickable, focusable });
    }

    for (const key of Object.keys(node)) {
      if (key === "$") continue;
      const children = node[key];
      if (Array.isArray(children)) children.forEach(walk);
    }
  }

  walk(parsed);
  return results;
}

// ── Centre point from bounds string "[x1,y1][x2,y2]" ─────────────────────────

function centrePoint(bounds) {
  try {
    const nums = bounds.replace(/[\[\]]/g, " ").trim().split(/[\s,]+/).map(Number);
    return { x: Math.round((nums[0] + nums[2]) / 2), y: Math.round((nums[1] + nums[3]) / 2) };
  } catch (_) { return null; }
}

// ── Suggest a snake_case tag from available signals ───────────────────────────

function suggestTag(screen, text, cls) {
  const prefix = screen !== "unknown" ? screen.split("_")[0] : "screen";
  let base = text;
  if (!base) {
    // derive from class
    base = cls.replace(/([A-Z])/g, "_$1").toLowerCase().replace(/^_/, "");
  }
  // snake_case + clean
  const snake = base
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_|_$/g, "")
    .slice(0, 40);
  const clsHint = cls.includes("Button") ? "button"
                : cls.includes("Text")   ? "label"
                : cls.includes("Edit")   ? "input"
                : cls.includes("Image")  ? "image" : "element";
  return `${prefix}_${snake}_${clsHint}`.replace(/_+/g, "_");
}

// ── Dynamic text filter ───────────────────────────────────────────────────────

function isDynamic(text) {
  if (!text || text.length > 50) return true;
  const digits = (text.match(/\d/g) || []).length;
  if (digits > text.length * 0.5) return true;
  if (/[₹$€£¥]/.test(text)) return true;
  if (/\d+%/.test(text)) return true;
  if (/\d{1,2}:\d{2}/.test(text)) return true;
  return false;
}

// ── Screen name from activity ─────────────────────────────────────────────────

function getScreenName(deviceId) {
  try {
    const dump = adb("-s", deviceId, "shell", "dumpsys", "activity", "activities");
    for (const line of dump.split("\n")) {
      if ((line.includes("Resumed:") || line.includes("ResumedActivity:")) &&
           line.includes("com.popclub.android")) {
        const slash = line.lastIndexOf("/");
        const space = line.indexOf(" ", slash);
        if (slash >= 0 && space > slash) {
          return line.substring(slash + 1, space).trim()
            .replace(/\.(Activity|Fragment|Screen|Page|View)$/, "")
            .replace(/^.*\./, "")
            .replace(/([A-Z])/g, "_$1").toLowerCase()
            .replace(/^_/, "").replace(/_+/g, "_");
        }
      }
    }
  } catch (_) {}
  return "unknown";
}

// ── MCP Server ────────────────────────────────────────────────────────────────

const server = new McpServer({ name: "forge-mcp", version: "1.5.0" });

server.tool(
  "forge_get_hierarchy",

  "Dump the live Android screen UI (Jetpack Compose app) and return every " +
  "element mapped to its best Forge YAML locator strategy across 4 tiers. " +
  "NOTE: resource-id is NOT used — POP is a Jetpack Compose app and Compose " +
  "does not generate resource-ids. Only testTag() (content-desc) and text work. " +
  "Tier 1 = registered element key (element:), " +
  "Tier 2 = unregistered qaTestTag / content-desc (locator:), " +
  "Tier 3 = visible text (text:), " +
  "Tier 4 = no tag AND no text → coordinate tap (x:/y:) + suggested testTag() to add. " +
  "Always call this before writing a Forge YAML test.",

  {
    udid: z.string().optional().describe("Device UDID — auto-detected if omitted"),
  },

  async ({ udid }) => {
    // ── Connect ───────────────────────────────────────────────────────────────
    let deviceId;
    try { deviceId = udid || detectDevice(); }
    catch (e) { return { content: [{ type: "text", text: `❌ ${e.message}` }] }; }

    // ── Dump ──────────────────────────────────────────────────────────────────
    try { adb("-s", deviceId, "shell", "uiautomator", "dump", "/sdcard/wd.xml"); }
    catch (e) { return { content: [{ type: "text", text: `❌ uiautomator dump failed: ${e.message}` }] }; }

    let xml;
    try {
      const r = spawnSync("adb", ["-s", deviceId, "shell", "cat", "/sdcard/wd.xml"],
        { encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: 10000 });
      xml = r.stdout || "";
      if (!xml.includes("<hierarchy")) throw new Error("Empty XML");
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Failed to read UI dump: ${e.message}` }] };
    }

    // ── Parse ─────────────────────────────────────────────────────────────────
    let elements;
    try { elements = await parseHierarchy(xml); }
    catch (e) { return { content: [{ type: "text", text: `❌ Parse error: ${e.message}` }] }; }

    const knownTags = loadKnownTags();
    const screen    = getScreenName(deviceId);

    // ── Categorise into 4 tiers (Compose app — no resource-ids) ─────────────
    const tier1 = []; // registered testTag → element:
    const tier2 = []; // unregistered testTag → locator:
    const tier3 = []; // visible text only → text:
    const tier4 = []; // no tag AND no text → x/y coords + suggested testTag

    const seenTags  = new Set();
    const seenTexts = new Set();

    for (const el of elements) {
      if (el.tag) {
        if (seenTags.has(el.tag)) continue;
        seenTags.add(el.tag);
        const info = knownTags.get(el.tag);
        if (info) tier1.push({ ...el, elementKey: info.key, feature: info.feature });
        else      tier2.push(el);

      } else if (el.text && !isDynamic(el.text)) {
        if (seenTexts.has(el.text)) continue;
        seenTexts.add(el.text);
        tier3.push(el);

      } else if (el.clickable || el.focusable) {
        // No testTag, no visible text — Compose element with no identification at all.
        // Coordinate tap only. Suggest a testTag to add in TestTags.kt.
        const pt = centrePoint(el.bounds);
        if (pt) {
          const suggestion = suggestTag(screen, el.text, el.cls);
          tier4.push({ ...el, ...pt, suggestion });
        }
      }
    }

    // ── Format output ─────────────────────────────────────────────────────────
    const L = [];
    const line  = (s = "") => L.push(s);
    const rule  = () => L.push("─".repeat(62));
    const bold  = (s) => { line(s); rule(); };

    line(`📱 Screen: ${screen}   Device: ${deviceId}`);
    line(`   T1: ${tier1.length} registered  T2: ${tier2.length} unregistered  T3: ${tier3.length} text-only  T4: ${tier4.length} no-tag (coords)`);
    line();

    // ── Tier 1 ────────────────────────────────────────────────────────────────
    bold("✅ TIER 1 — REGISTERED  →  element: <key>  (ready to use)");
    if (!tier1.length) {
      line("  (none on this screen)");
    } else {
      for (const r of tier1) {
        line(`  element: ${r.elementKey}          # feature: ${r.feature}`);
        if (r.text) line(`    visible text: "${r.text}"`);
        line(`    tag: ${r.tag}   cls: ${r.cls}`);
        line();
      }
    }
    line();

    // ── Tier 2 — auto-register into elements/*.yaml ──────────────────────────
    bold("⚠️  TIER 2 — UNREGISTERED TAG  →  auto-registering into elements/*.yaml");
    if (!tier2.length) {
      line("  (none)");
    } else {
      for (const u of tier2) {
        const result = registerElement(u.tag);
        if (result.error) {
          line(`  ❌ ${u.tag}  →  ${result.error}`);
          line(`     Use: locator: ${u.tag}  in YAML for now`);
        } else if (result.alreadyExists) {
          line(`  ✅ ${u.tag}  →  already in ${result.feature}.yaml (reload elements)`);
          line(`     element: ${result.elementKey}`);
        } else {
          line(`  ✅ ${u.tag}  →  added to ${result.feature}.yaml`);
          line(`     element: ${result.elementKey}          # feature: ${result.feature}`);
        }
        if (u.text) line(`     visible text: "${u.text}"`);
        line(`     cls: ${u.cls}`);
        line();
      }
      line("  ℹ️  Newly registered elements are available in the NEXT test run.");
      line("     Forge reloads elements.yaml at test start — no restart needed.");
    }
    line();

    // ── Tier 3 ────────────────────────────────────────────────────────────────
    bold("💬 TIER 3 — TEXT ONLY  →  text: \"...\"  (no qaTestTag on element)");
    if (!tier3.length) {
      line("  (none)");
    } else {
      for (const t of tier3) {
        line(`  text: "${t.text}"   cls: ${t.cls}`);
        line(`  # YAML:  - action: tap`);
        line(`  #          text: "${t.text}"`);
        line();
      }
    }
    line();

    // ── Tier 4 ────────────────────────────────────────────────────────────────
    bold("📍 TIER 4 — NO TAG / NO TEXT  →  x/y coordinates  (Compose: add testTag()!)");
    if (!tier4.length) {
      line("  (none — all interactable elements have locators ✅)");
    } else {
      line("  These Compose elements have NO testTag() modifier and NO visible text.");
      line("  They can only be tapped by coordinates — fragile, breaks on different screen sizes.");
      line("  ACTION: Ask the Android dev to add Modifier.testTag(TestTags.X) to each.");
      line();
      for (const c of tier4) {
        line(`  cls: ${c.cls}`);
        line(`  bounds: ${c.bounds}`);
        line(`  # Forge YAML (last resort):`);
        line(`  #   - action: tap`);
        line(`  #       x: ${c.x}`);
        line(`  #       y: ${c.y}`);
        line(`  # Suggested testTag to add in TestTags.kt:`);
        line(`  #   const val ${c.suggestion.toUpperCase()} = "${c.suggestion}"`);
        line(`  # Then add Modifier.testTag(TestTags.${c.suggestion.toUpperCase()}) in the Composable`);
        line();
      }
    }
    line();

    // ── Summary for test writing ──────────────────────────────────────────────
    rule();
    line("📝 WRITE YOUR FORGE YAML USING:");
    line();
    const examples = [];
    if (tier1.length) {
      const r = tier1[0];
      examples.push(`# Tap a registered element (best):\n- action: tap\n  element: ${r.elementKey}`);
    }
    if (tier2.length) {
      const u = tier2[0];
      examples.push(`# Tap an unregistered tag (works, add to yaml later):\n- action: tap\n  locator: ${u.tag}`);
    }
    if (tier3.length) {
      const t = tier3[0];
      examples.push(`# Tap by visible text:\n- action: tap\n  text: "${t.text}"`);
    }
    if (tier4.length) {
      const c = tier4[0];
      examples.push(`# Coordinate tap — last resort, add testTag() to Composable to fix:\n- action: tap\n  x: ${c.x}\n  y: ${c.y}`);
    }
    examples.forEach(ex => { line(ex); line(); });

    if (tier4.length > 0) {
      rule();
      line(`⚠️  ACTION NEEDED: ${tier4.length} element(s) have no testTag() and no visible text.`);
      line("   This is a Jetpack Compose app — resource-id does NOT work.");
      line("   The only fix is adding Modifier.testTag(TestTags.X) in the Composable.");
      line("   Share the suggested tag names above with the Android dev.");
    }

    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── Device control helpers ────────────────────────────────────────────────────

function deviceId() {
  try { return detectDevice(); } catch (_) { return null; }
}

// Resolve a locator to the best Forge YAML representation:
// testTag → registered element key → unregistered locator → x/y
function resolveLocator(tag, text, x, y) {
  if (tag) {
    const knownTags = loadKnownTags();
    const info = knownTags.get(tag);
    if (info) return { type: "element", value: info.key };
    // auto-register and use element key
    const reg = registerElement(tag);
    if (!reg.error) return { type: "element", value: reg.elementKey };
    return { type: "locator", value: tag };
  }
  if (text && !isDynamic(text)) return { type: "text", value: text };
  return { type: "coords", x, y };
}

// ── Screenshot helper (shared by all action tools) ───────────────────────────

function captureScreen(adbBase) {
  const r = spawnSync("adb", [...adbBase, "exec-out", "screencap", "-p"],
    { timeout: 10000, encoding: "buffer" });
  if (r.error || !r.stdout?.length) return null;
  return { type: "image", data: r.stdout.toString("base64"), mimeType: "image/png" };
}

// ── forge_device_screenshot ───────────────────────────────────────────────────

server.tool(
  "forge_device_screenshot",

  "Take a screenshot of the live Android device screen and return it as a " +
  "base64 PNG image so you can visually see what is on screen. " +
  "Call this to understand the current app state before navigating or scanning.",

  {},

  async () => {
    const id  = deviceId();
    const img = captureScreen(id ? ["-s", id] : []);
    if (!img) return { content: [{ type: "text", text: "❌ Screenshot failed" }] };
    return { content: [img] };
  }
);

// ── forge_device_tap ──────────────────────────────────────────────────────────

server.tool(
  "forge_device_tap",

  "Tap an element on the Android device. Prefer passing a testTag (content-desc) " +
  "or element key — falls back to x/y coordinates only if no tag exists. " +
  "Use this to navigate the app to the right screen before scanning.",

  {
    tag    : z.string().optional().describe("qaTestTag / content-desc of element to tap"),
    element: z.string().optional().describe("Registered element key from elements.yaml"),
    text   : z.string().optional().describe("Visible text of element to tap"),
    x      : z.number().optional().describe("X coordinate (last resort)"),
    y      : z.number().optional().describe("Y coordinate (last resort)"),
  },

  async ({ tag, element, text, x, y }) => {
    const id = deviceId();
    const adbBase = id ? ["-s", id] : [];

    // If element key given, look up its accessibilityId to find coords via dump
    let tapX = x, tapY = y;
    const resolvedTag = tag || element;

    if (resolvedTag || text) {
      // Dump hierarchy, find element, get centre point
      try { spawnSync("adb", [...adbBase, "shell", "uiautomator", "dump", "/sdcard/wd.xml"], { timeout: 8000 }); } catch (_) {}
      const r = spawnSync("adb", [...adbBase, "shell", "cat", "/sdcard/wd.xml"],
        { encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: 8000 });
      const xml = r.stdout || "";
      const elements = await parseHierarchy(xml);

      const match = elements.find(el => {
        if (resolvedTag) return el.tag === resolvedTag || el.tag?.includes(resolvedTag);
        if (text) return el.text === text || el.text?.toLowerCase().includes(text.toLowerCase());
        return false;
      });

      if (match) {
        const pt = centrePoint(match.bounds);
        if (pt) { tapX = pt.x; tapY = pt.y; }
      }
    }

    if (tapX == null || tapY == null) {
      return { content: [{ type: "text", text: "❌ Could not locate element on screen. Try forge_device_screenshot first to verify the screen state." }] };
    }

    spawnSync("adb", [...adbBase, "shell", "input", "tap", String(Math.round(tapX)), String(Math.round(tapY))],
      { timeout: 5000 });

    await new Promise(r => setTimeout(r, 500)); // settle

    const loc = resolveLocator(resolvedTag, text, tapX, tapY);
    let yamlStep = `- action: tap\n`;
    let warning = null;

    if (loc.type === "element") {
      yamlStep += `  element: ${loc.value}`;
    } else if (loc.type === "locator") {
      yamlStep += `  locator: ${loc.value}`;
    } else if (loc.type === "text") {
      yamlStep += `  text: "${loc.value}"`;
    } else {
      // No testTag, no text — coordinate fallback
      yamlStep += `  x: ${tapX}\n  y: ${tapY}  # ⚠️ NO testTag — add Modifier.testTag() to this Composable`;
      warning = `⚠️  No qaTestTag found at (${tapX}, ${tapY}). Used coordinates — fragile.\n   Ask Android dev to add: Modifier.testTag("<name>") to this element.`;
    }

    const out = [`✅ Tapped (${tapX}, ${tapY})`, ``, `Forge YAML step:`, yamlStep];
    if (warning) out.push(``, warning);

    const content = [{ type: "text", text: out.join("\n") }];
    const img = captureScreen(adbBase);
    if (img) content.push(img);
    return { content };
  }
);

// ── forge_device_type ─────────────────────────────────────────────────────────

server.tool(
  "forge_device_type",
  "Type text into the currently focused field on the device.",
  {
    text   : z.string().describe("Text to type"),
    element: z.string().optional().describe("Tap this element key first to focus it"),
  },
  async ({ text, element }) => {
    const id = deviceId();
    const adbBase = id ? ["-s", id] : [];
    if (element) {
      spawnSync("adb", [...adbBase, "shell", "uiautomator", "dump", "/sdcard/wd.xml"], { timeout: 8000 });
      // tap element first (reuse tap logic via adb)
    }
    const escaped = text.replace(/ /g, "%s").replace(/['"]/g, "");
    spawnSync("adb", [...adbBase, "shell", "input", "text", escaped], { timeout: 5000 });
    const content = [{ type: "text", text: `✅ Typed: "${text}"\n\nForge YAML step:\n- action: enterText\n  value: "${text}"` }];
    const img = captureScreen(adbBase);
    if (img) content.push(img);
    return { content };
  }
);

// ── forge_device_key ──────────────────────────────────────────────────────────

server.tool(
  "forge_device_key",
  "Press a key on the device. Common values: back, home, search, enter.",
  {
    key: z.string().describe("Key name: back | home | search | enter | tab | delete"),
  },
  ({ key }) => {
    const KEY_CODES = { back: 4, home: 3, search: 84, enter: 66, tab: 61, delete: 67 };
    const code = KEY_CODES[key.toLowerCase()];
    if (!code) return { content: [{ type: "text", text: `❌ Unknown key: ${key}. Use: ${Object.keys(KEY_CODES).join(", ")}` }] };
    const id      = deviceId();
    const adbBase = id ? ["-s", id] : [];
    spawnSync("adb", [...adbBase, "shell", "input", "keyevent", String(code)], { timeout: 5000 });
    const content = [{ type: "text", text: `✅ Pressed: ${key}\n\nForge YAML step:\n- action: pressKey\n  value: ${key}` }];
    const img = captureScreen(adbBase);
    if (img) content.push(img);
    return { content };
  }
);

// ── forge_device_swipe ────────────────────────────────────────────────────────

server.tool(
  "forge_device_swipe",
  "Swipe on the device screen. direction: up | down | left | right.",
  {
    direction: z.enum(["up", "down", "left", "right"]).describe("Swipe direction"),
    distance : z.number().optional().describe("Swipe distance in pixels (default 600)"),
  },
  ({ direction, distance = 600 }) => {
    const id      = deviceId();
    const adbBase = id ? ["-s", id] : [];
    const cx = 540, cy = 1200;
    const d = distance;
    const coords = {
      up   : [cx, cy, cx, cy - d],
      down : [cx, cy - d, cx, cy],
      left : [cx + d/2, cy, cx - d/2, cy],
      right: [cx - d/2, cy, cx + d/2, cy],
    }[direction];
    spawnSync("adb", [...adbBase, "shell", "input", "swipe",
      ...coords.map(String), "400"], { timeout: 5000 });
    const content = [{ type: "text", text: `✅ Swiped ${direction}\n\nForge YAML step:\n- action: swipe\n  value: ${direction}` }];
    const img = captureScreen(adbBase);
    if (img) content.push(img);
    return { content };
  }
);

// ── forge_device_scroll_to ────────────────────────────────────────────────────

server.tool(
  "forge_device_scroll_to",
  "Scroll the screen until the element with the given tag or text becomes visible " +
  "(up to 8 swipes). Use direction 'down' to scroll forward (default), 'up' to go back. " +
  "Generates a Forge YAML scrollDown/scrollUp step.",
  {
    tag      : z.string().optional().describe("qaTestTag / content-desc of the target element"),
    text     : z.string().optional().describe("Visible text of the target element"),
    direction: z.enum(["down", "up"]).optional().default("down").describe("Scroll direction"),
    maxSwipes: z.number().optional().default(8).describe("Max swipes before giving up"),
  },
  async ({ tag, text, direction = "down", maxSwipes = 8 }) => {
    const id      = deviceId();
    const adbBase = id ? ["-s", id] : [];
    const cx = 540, cy = 1200, dist = 600;
    const [fromY, toY] = direction === "down" ? [cy, cy - dist] : [cy - dist, cy];

    function findInXml(xml) {
      return xml.split("<node").some(seg => {
        const desc = (seg.match(/content-desc="([^"]*)"/) || [])[1] || "";
        const txt  = (seg.match(/text="([^"]*)"/)         || [])[1] || "";
        if (tag  && (desc === tag  || desc.includes(tag)))  return true;
        if (text && (txt  === text || txt.toLowerCase().includes(text.toLowerCase()))) return true;
        return false;
      });
    }

    for (let i = 0; i < maxSwipes; i++) {
      // Dump and check
      spawnSync("adb", [...adbBase, "shell", "uiautomator", "dump", "/sdcard/wd.xml"], { timeout: 8000 });
      const r = spawnSync("adb", [...adbBase, "shell", "cat", "/sdcard/wd.xml"],
        { encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: 8000 });
      if (findInXml(r.stdout || "")) {
        const label = tag || `"${text}"`;
        const content = [{ type: "text", text:
          `✅ Found ${label} after ${i} swipe(s)\n\nForge YAML step:\n- action: scrollDown\n  element: ${tag || ""}` }];
        const img = captureScreen(adbBase);
        if (img) content.push(img);
        return { content };
      }
      // Swipe
      spawnSync("adb", [...adbBase, "shell", "input", "swipe",
        String(cx), String(fromY), String(cx), String(toY), "400"], { timeout: 5000 });
      await new Promise(r => setTimeout(r, 600));
    }

    const label = tag || `"${text}"`;
    return { content: [{ type: "text", text: `❌ "${label}" not found after ${maxSwipes} swipe(s)` }] };
  }
);

// ── forge_device_assert_text ──────────────────────────────────────────────────

server.tool(
  "forge_device_assert_text",
  "Assert that a specific text string is visible on screen right now. " +
  "Optionally also check that an element with the given tag contains that text. " +
  "Returns PASS or FAIL with a Forge YAML verifyElement step.",
  {
    text   : z.string().describe("Text that must be visible on screen"),
    tag    : z.string().optional().describe("qaTestTag of the element expected to contain this text"),
    element: z.string().optional().describe("Registered element key expected to contain this text"),
  },
  async ({ text, tag, element }) => {
    const id      = deviceId();
    const adbBase = id ? ["-s", id] : [];

    spawnSync("adb", [...adbBase, "shell", "uiautomator", "dump", "/sdcard/wd.xml"], { timeout: 8000 });
    const r = spawnSync("adb", [...adbBase, "shell", "cat", "/sdcard/wd.xml"],
      { encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: 8000 });
    const xml = r.stdout || "";

    // Check if text appears anywhere
    const textFound = xml.split("<node").some(seg => {
      const txt = (seg.match(/text="([^"]*)"/) || [])[1] || "";
      return txt === text || txt.includes(text);
    });

    // Optionally verify it's inside the expected tagged element
    let tagMatch = true;
    if ((tag || element) && textFound) {
      tagMatch = xml.split("<node").some(seg => {
        const desc = (seg.match(/content-desc="([^"]*)"/) || [])[1] || "";
        const txt  = (seg.match(/text="([^"]*)"/)         || [])[1] || "";
        const tagVal = tag || element;
        return (desc === tagVal || desc.includes(tagVal)) && txt.includes(text);
      });
    }

    const pass = textFound && tagMatch;
    const yamlStep = element
      ? `- action: verifyElement\n  element: ${element}\n  text: "${text}"`
      : `- action: tapByText\n  value: "${text}"  # use verifyElement if element key exists`;

    return { content: [{ type: "text", text:
      `${pass ? "✅ PASS" : "❌ FAIL"} — "${text}" ${pass ? "found" : "NOT found"} on screen` +
      (tag || element ? `\nElement check (${tag || element}): ${tagMatch ? "✅ matched" : "❌ not matched"}` : "") +
      `\n\nForge YAML step:\n${yamlStep}` }] };
  }
);

// ── iOS device helpers ────────────────────────────────────────────────────────
// All iOS tools use xcrun simctl / idb — no ADB.

function iosDeviceId() {
  // Prefer a booted simulator
  const r = spawnSync("xcrun", ["simctl", "list", "devices", "booted", "--json"],
    { encoding: "utf8", timeout: 8000 });
  try {
    const json = JSON.parse(r.stdout);
    for (const runtimeDevices of Object.values(json.devices || {})) {
      for (const d of runtimeDevices) {
        if (d.state === "Booted") return { udid: d.udid, name: d.name, type: "simulator" };
      }
    }
  } catch (_) {}
  return null;
}

function xcrun(...args) {
  const r = spawnSync("xcrun", args, { encoding: "utf8", timeout: 15000, maxBuffer: 10 * 1024 * 1024 });
  return r.stdout || "";
}

server.tool(
  "forge_ios_screenshot",
  "Take a screenshot of the booted iOS Simulator and return it as a base64 PNG.",
  {},
  async () => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found. Boot one in Xcode first." }] };
    const tmp = `/tmp/forge_ios_${Date.now()}.png`;
    spawnSync("xcrun", ["simctl", "io", dev.udid, "screenshot", tmp], { timeout: 10000 });
    try {
      const buf = readFileSync(tmp);
      return { content: [{ type: "image", data: buf.toString("base64"), mimeType: "image/png" }] };
    } catch (_) {
      return { content: [{ type: "text", text: "❌ Screenshot failed" }] };
    }
  }
);

server.tool(
  "forge_ios_tap",
  "Tap an element on the booted iOS Simulator by accessibility identifier or x/y coordinates.",
  {
    tag: z.string().optional().describe("Accessibility identifier (testTag) of element to tap"),
    x  : z.number().optional().describe("X coordinate"),
    y  : z.number().optional().describe("Y coordinate"),
  },
  async ({ tag, x, y }) => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found." }] };

    if (tag) {
      // Use idb if available, else fall back to simctl UI interaction
      const idb = spawnSync("idb", ["ui", "tap", "--udid", dev.udid, "--accessibility-id", tag],
        { encoding: "utf8", timeout: 8000 });
      if (!idb.error) {
        return { content: [{ type: "text", text: `✅ Tapped "${tag}"\n\nForge YAML step:\n- action: tap\n  element: ${tag}` }] };
      }
    }

    if (x != null && y != null) {
      spawnSync("xcrun", ["simctl", "io", dev.udid, "sendevent", "--type", "touch",
        "--x", String(Math.round(x)), "--y", String(Math.round(y))], { timeout: 5000 });
      return { content: [{ type: "text", text: `✅ Tapped (${x}, ${y})\n\nForge YAML step:\n- action: tap\n  x: ${x}\n  y: ${y}` }] };
    }

    return { content: [{ type: "text", text: "❌ Provide tag or x/y coordinates" }] };
  }
);

server.tool(
  "forge_ios_type",
  "Type text into the currently focused field on the booted iOS Simulator.",
  {
    text: z.string().describe("Text to type"),
  },
  ({ text }) => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found." }] };
    spawnSync("xcrun", ["simctl", "io", dev.udid, "sendtext", text], { timeout: 8000 });
    return { content: [{ type: "text", text: `✅ Typed: "${text}"\n\nForge YAML step:\n- action: enterText\n  value: "${text}"` }] };
  }
);

server.tool(
  "forge_ios_key",
  "Press a hardware key on the iOS Simulator. Common: home, lock.",
  {
    key: z.string().describe("Key: home | lock | siri | rotate_left | rotate_right"),
  },
  ({ key }) => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found." }] };
    const BUTTONS = { home: "home", lock: "lock", siri: "siri", rotate_left: "rotate_left", rotate_right: "rotate_right" };
    const btn = BUTTONS[key.toLowerCase()];
    if (!btn) return { content: [{ type: "text", text: `❌ Unknown key: ${key}` }] };
    spawnSync("xcrun", ["simctl", "io", dev.udid, "button", btn], { timeout: 5000 });
    return { content: [{ type: "text", text: `✅ Pressed: ${key}` }] };
  }
);

server.tool(
  "forge_ios_swipe",
  "Swipe on the booted iOS Simulator screen.",
  {
    direction: z.enum(["up", "down", "left", "right"]).describe("Swipe direction"),
  },
  ({ direction }) => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found." }] };
    const cx = 200, cy = 400, dist = 300;
    const coords = {
      up   : [cx, cy + dist, cx, cy],
      down : [cx, cy, cx, cy + dist],
      left : [cx + dist, cy, cx, cy],
      right: [cx, cy, cx + dist, cy],
    }[direction];
    // idb swipe — falls back gracefully if not installed
    const idb = spawnSync("idb", ["ui", "swipe", "--udid", dev.udid,
      String(coords[0]), String(coords[1]), String(coords[2]), String(coords[3])],
      { encoding: "utf8", timeout: 8000 });
    if (idb.error) {
      return { content: [{ type: "text", text: `⚠️  idb not found — install with: brew install idb-companion\n(swipe not executed)` }] };
    }
    return { content: [{ type: "text", text: `✅ Swiped ${direction} on iOS Simulator\n\nForge YAML step:\n- action: swipe\n  value: ${direction}` }] };
  }
);

server.tool(
  "forge_ios_get_hierarchy",
  "Dump the accessibility hierarchy of the booted iOS Simulator screen. " +
  "Use this to find element identifiers before tapping.",
  {},
  async () => {
    const dev = iosDeviceId();
    if (!dev) return { content: [{ type: "text", text: "❌ No booted iOS Simulator found." }] };
    // idb gives the richest output
    const r = spawnSync("idb", ["ui", "describe-all", "--udid", dev.udid],
      { encoding: "utf8", timeout: 15000, maxBuffer: 10 * 1024 * 1024 });
    if (r.error || !r.stdout) {
      return { content: [{ type: "text", text: "❌ idb not found or failed. Install: brew install idb-companion" }] };
    }
    // Trim to relevant fields only
    const lines = r.stdout.split("\n").filter(l => /label|identifier|type|frame/.test(l)).slice(0, 100);
    return { content: [{ type: "text", text: lines.join("\n") || "(empty hierarchy)" }] };
  }
);

// ── forge_device_launch ───────────────────────────────────────────────────────

server.tool(
  "forge_device_launch",
  "Launch the POP app on the device (cold start).",
  {},
  async () => {
    const id      = deviceId();
    const adbBase = id ? ["-s", id] : [];
    spawnSync("adb", [...adbBase, "shell", "monkey", "-p",
      "com.popclub.android", "-c", "android.intent.category.LAUNCHER", "1"], { timeout: 8000 });
    await new Promise(r => setTimeout(r, 2000)); // wait for app to load
    const content = [{ type: "text", text: "✅ App launched\n\nForge YAML step:\n- action: launchApp" }];
    const img = captureScreen(adbBase);
    if (img) content.push(img);
    return { content };
  }
);

// ── forge_list_tests ──────────────────────────────────────────────────────────

server.tool(
  "forge_list_tests",
  "List all Forge YAML test files organised by feature subfolder.",
  {
    feature: z.string().optional().describe("Filter by feature: shop | login | home | profile | etc."),
  },
  ({ feature }) => {
    const results = [];
    const base = feature ? join(TESTDATA_ROOT, feature) : TESTDATA_ROOT;

    function walkDir(dir, prefix = "") {
      try {
        for (const f of readdirSync(dir)) {
          const full = join(dir, f);
          const rel  = prefix ? `${prefix}/${f}` : f;
          if (f.endsWith(".yaml") || f.endsWith(".yml")) {
            results.push(rel);
          } else if (existsSync(full) && !f.includes(".")) {
            walkDir(full, rel);
          }
        }
      } catch (_) {}
    }

    walkDir(base, feature || "");

    if (!results.length) return { content: [{ type: "text", text: "No tests found." }] };

    const grouped = {};
    for (const f of results.sort()) {
      const parts = f.split("/");
      const folder = parts.length > 1 ? parts[0] : "(root)";
      if (!grouped[folder]) grouped[folder] = [];
      grouped[folder].push(parts[parts.length - 1]);
    }

    const lines = [];
    for (const [folder, files] of Object.entries(grouped)) {
      lines.push(`\n📁 ${folder}/`);
      files.forEach(f => lines.push(`   ${f}`));
    }

    return { content: [{ type: "text", text: `Tests in androidTests/${feature || ""}:${lines.join("\n")}` }] };
  }
);

// ── forge_validate_test ───────────────────────────────────────────────────────

server.tool(
  "forge_validate_test",
  "Validate that every element: key used in a test YAML exists in the elements files. " +
  "Run this before forge_run_test to catch missing elements early.",
  {
    filename : z.string().describe("Test filename, e.g. shop_add_to_cart_toast.yaml"),
    subfolder: z.string().optional(),
  },
  ({ filename, subfolder }) => {
    const fname = filename.endsWith(".yaml") ? filename : filename + ".yaml";
    const candidates = subfolder
      ? [join(TESTDATA_ROOT, subfolder, fname)]
      : ["shop","login","home","profile","rewards","upi","billpay","card"].map(
          d => join(TESTDATA_ROOT, d, fname)
        ).concat([join(TESTDATA_ROOT, fname)]);

    let content = null, filePath = null;
    for (const p of candidates) {
      if (existsSync(p)) { content = readFileSync(p, "utf8"); filePath = p; break; }
    }
    if (!content) return { content: [{ type: "text", text: `❌ File not found: ${fname}` }] };

    const knownTags = loadKnownTags();
    const allKeys = new Set(knownTags.values().map ? [...knownTags.values()].map(v => v.key) : []);
    // Build key set from all elements files
    const keySet = new Set();
    try {
      for (const f of readdirSync(ELEMENTS_DIR).filter(f => f.endsWith(".yaml"))) {
        const raw = readFileSync(join(ELEMENTS_DIR, f), "utf8");
        for (const m of raw.matchAll(/^([a-zA-Z_][a-zA-Z0-9_]*):/gm)) keySet.add(m[1]);
      }
    } catch (_) {}

    const errors = [];
    const ok = [];
    let stepNum = 0;
    for (const line of content.split("\n")) {
      if (line.match(/^\s*-\s+action:/)) stepNum++;
      const m = line.match(/^\s+element:\s+(\S+)/);
      if (m) {
        const key = m[1].replace(/#.*/, "").trim();
        if (keySet.has(key)) ok.push({ stepNum, key });
        else errors.push({ stepNum, key });
      }
    }

    const L = [];
    L.push(`📋 Validating: ${fname}`);
    L.push(`   ${ok.length} elements OK  |  ${errors.length} missing`);
    L.push("");
    if (errors.length) {
      L.push("❌ Missing elements (not in any elements/*.yaml):");
      errors.forEach(e => L.push(`   Step ${e.stepNum}: element: ${e.key}`));
      L.push("");
      L.push("Fix: add to elements/<feature>.yaml or change to  locator: <tag>");
    } else {
      L.push("✅ All element keys are valid — safe to run.");
    }
    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── forge_run_test ────────────────────────────────────────────────────────────

server.tool(
  "forge_run_test",

  "Run a Forge YAML test file via Maven (mvn test) and return a structured " +
  "pass/fail result. If the test fails, the response includes the failed step " +
  "number, action, element key, and the raw error message so Claude can decide " +
  "whether to call forge_heal_step to fix it. " +
  "testFile must be just the filename, e.g. ts_checkout.yaml",

  {
    testFile: z.string().describe("YAML test filename, e.g. ts_checkout.yaml"),
    device:   z.string().optional().describe("Device serial — auto-detected if omitted"),
  },

  async ({ testFile, device }) => {
    const deviceId = device || (() => {
      try { return detectDevice(); } catch (_) { return null; }
    })();

    const args = [
      "test",
      "-DtestFile=" + testFile,
      "--no-transfer-progress",
      ...(deviceId ? ["-DdeviceSerial=" + deviceId] : []),
    ];

    const L = [];
    const line = s => L.push(s ?? "");

    // ── Async runner — does NOT block the MCP event loop ─────────────────
    function runTestAsync(extraArgs = []) {
      return new Promise((resolve) => {
        let stdout = "", stderr = "";
        const proc = spawn("mvn", [...args, ...extraArgs], {
          cwd     : FORGE_ROOT,
          encoding: "utf8",
        });
        proc.stdout.on("data", d => { stdout += d; });
        proc.stderr.on("data", d => { stderr += d; });
        proc.on("close", code  => resolve({ stdout, stderr, status: code }));
        proc.on("error", err   => resolve({ stdout, stderr, status: -1, error: err }));
        // Hard timeout — kill after 10 min
        setTimeout(() => { try { proc.kill("SIGTERM"); } catch (_) {} }, 10 * 60 * 1000);
      });
    }

    async function restartUiAutomator2(serial) {
      line("⚠️  UiAutomation not connected — restarting UiAutomator2…");
      const adbBase = serial ? ["-s", serial] : [];
      for (const pkg of ["io.appium.uiautomator2.server", "io.appium.uiautomator2.server.test"]) {
        spawnSync("adb", [...adbBase, "shell", "am", "force-stop", pkg], { timeout: 5000 });
      }
      await new Promise(r => setTimeout(r, 3000));  // 3 s settle
      line("   UiAutomator2 restarted — retrying…");
      line();
    }

    line(`▶ mvn ${args.join(" ")}`);
    line(`   cwd: ${FORGE_ROOT}`);
    line();

    let result = await runTestAsync();

    // Detect UiAutomation disconnection and auto-recover (one retry)
    const combinedOut = (result.stdout || "") + (result.stderr || "");
    const isUiAutomationError =
      combinedOut.includes("UiAutomation not connected") ||
      (combinedOut.includes("UiAutomator2") && combinedOut.includes("not connected")) ||
      combinedOut.includes("Failed to connect to UiAutomator2");

    if (isUiAutomationError && result.status !== 0) {
      await restartUiAutomator2(deviceId);
      result = await runTestAsync();
    }

    const stdout = result.stdout || "";
    const stderr = result.stderr || "";
    const exitCode = result.status ?? -1;
    const success  = exitCode === 0;

    // ── Parse step results from stdout ────────────────────────────────────
    const steps = [];
    for (const raw of stdout.split("\n")) {
      const stepM = raw.match(/\[STEP\]\s+\[(\d+)\]\s+(.+)/);
      if (stepM) steps.push({ index: parseInt(stepM[1]), action: stepM[2].trim(), status: "running" });

      const passM = raw.match(/\[PASS\]\s+Step\s+(\d+)/);
      if (passM) {
        const s = steps.find(x => x.index === parseInt(passM[1]));
        if (s) s.status = "passed";
      }

      const failM = raw.match(/\[FAIL\]\s+Step\s+(\d+)\s+failed:\s*(.*)/);
      if (failM) {
        const s = steps.find(x => x.index === parseInt(failM[1]));
        if (s) { s.status = "failed"; s.error = failM[2].trim(); }
        else steps.push({ index: parseInt(failM[1]), action: "?", status: "failed", error: failM[2].trim() });
      }
    }

    const failedSteps = steps.filter(s => s.status === "failed");

    line(success ? "✅ TEST PASSED" : "❌ TEST FAILED");
    line();

    for (const s of steps) {
      const icon = s.status === "passed" ? "✅" : s.status === "failed" ? "❌" : "⏩";
      line(`  ${icon} Step ${s.index}: ${s.action}`);
      if (s.error) line(`       Error: ${s.error}`);
    }

    if (failedSteps.length) {
      line();
      line("── FAILED STEPS (pass these to forge_heal_step) ──");
      for (const s of failedSteps) {
        // Try to extract element name from action string "tap element_name" etc.
        const elMatch = s.action.match(/\b([a-z][a-z0-9_]{3,})\b/);
        line(`  stepIndex: ${s.index}`);
        line(`  action:    ${s.action}`);
        if (elMatch) line(`  element:   ${elMatch[1]}  ← likely broken locator`);
        line(`  error:     ${s.error}`);
        line();
      }
    }

    if (result.error) line(`\nspawn error: ${result.error.message}`);

    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── forge_heal_step ───────────────────────────────────────────────────────────

server.tool(
  "forge_heal_step",

  "Self-heal a failing Forge test step. Give it the broken element key (or " +
  "locator string) and the action, and it will: " +
  "(1) dump the live screen, " +
  "(2) search for the best matching element using fuzzy name + text similarity, " +
  "(3) return the corrected YAML step ready to drop into the test. " +
  "Call this after forge_run_test reports a failed step.",

  {
    brokenElement : z.string().describe("The element key or locator that failed, e.g. cart_checkout_btn"),
    action        : z.string().describe("The action that failed, e.g. tap"),
    stepYaml      : z.string().optional().describe("Full YAML of the failing step for context"),
    udid          : z.string().optional().describe("Device UDID — auto-detected if omitted"),
  },

  async ({ brokenElement, action, stepYaml, udid }) => {
    let deviceId;
    try { deviceId = udid || detectDevice(); }
    catch (e) { return { content: [{ type: "text", text: `❌ ${e.message}` }] }; }

    // Dump current screen
    try { adb("-s", deviceId, "shell", "uiautomator", "dump", "/sdcard/wd.xml"); }
    catch (e) { return { content: [{ type: "text", text: `❌ uiautomator dump failed: ${e.message}` }] }; }

    let xml;
    try {
      const r = spawnSync("adb", ["-s", deviceId, "shell", "cat", "/sdcard/wd.xml"],
        { encoding: "utf8", maxBuffer: 10 * 1024 * 1024, timeout: 10000 });
      xml = r.stdout || "";
      if (!xml.includes("<hierarchy")) throw new Error("Empty XML");
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Failed to read UI dump: ${e.message}` }] };
    }

    let elements;
    try { elements = await parseHierarchy(xml); }
    catch (e) { return { content: [{ type: "text", text: `❌ Parse error: ${e.message}` }] }; }

    const knownTags = loadKnownTags();

    // ── Fuzzy match broken element against screen elements ────────────────
    // Score: tag similarity (Jaccard on word tokens) + text similarity
    function tokenise(s) {
      return (s || "").toLowerCase().replace(/[^a-z0-9]/g, " ").split(/\s+/).filter(Boolean);
    }
    function jaccard(a, b) {
      const sa = new Set(a), sb = new Set(b);
      const inter = [...sa].filter(x => sb.has(x)).length;
      return inter / (sa.size + sb.size - inter || 1);
    }

    const brokenTokens = tokenise(brokenElement);
    const candidates = [];

    for (const el of elements) {
      if (!el.tag && !el.text) continue;

      const tagTokens  = tokenise(el.tag);
      const textTokens = tokenise(el.text);
      const tagScore   = jaccard(brokenTokens, tagTokens);
      const textScore  = jaccard(brokenTokens, textTokens);
      const score      = Math.max(tagScore, textScore * 0.8);

      if (score > 0.1 || (el.tag && el.tag.includes(brokenTokens[0]))) {
        const info = el.tag ? knownTags.get(el.tag) : null;
        candidates.push({ el, score, info, tagScore, textScore });
      }
    }

    candidates.sort((a, b) => b.score - a.score);
    const best = candidates.slice(0, 5);

    const L = [];
    const line = s => L.push(s ?? "");

    line(`🔧 Healing: ${action} → ${brokenElement}`);
    line(`   Screen: ${getScreenName(deviceId)}   Device: ${deviceId}`);
    line();

    if (!best.length) {
      line("❌ No matching element found on screen.");
      line("   The element may be on a different screen — navigate the app first, then call forge_heal_step again.");
      return { content: [{ type: "text", text: L.join("\n") }] };
    }

    line("── Best matches ──────────────────────────────────────────────────");
    line();

    for (const c of best) {
      const { el, score, info } = c;
      line(`Score: ${(score * 100).toFixed(0)}%`);
      if (el.tag) {
        if (info) {
          line(`  ✅ REGISTERED → element: ${info.key}   (feature: ${info.feature})`);
        } else {
          // Auto-register it
          const reg = registerElement(el.tag);
          if (!reg.error) {
            line(`  ⚠️  NEW TAG → element: ${reg.elementKey}   (added to ${reg.feature}.yaml)`);
          } else {
            line(`  ⚠️  UNREGISTERED → locator: ${el.tag}`);
          }
        }
      } else if (el.text) {
        line(`  💬 TEXT → text: "${el.text}"`);
      }
      if (el.text && el.tag) line(`     visible text: "${el.text}"`);
      line(`     cls: ${el.cls}   bounds: ${el.bounds}`);
      line();
    }

    // ── Generate the fixed YAML step ──────────────────────────────────────
    const top = best[0];
    const topEl = top.el;
    const topInfo = top.info;

    line("── Fixed YAML step (replace the broken step with this) ───────────");
    line();

    let fixedYaml = `- action: ${action}\n`;
    if (topEl.tag) {
      if (topInfo) {
        fixedYaml += `  element: ${topInfo.key}`;
      } else {
        const reg = registerElement(topEl.tag);
        fixedYaml += reg.error
          ? `  locator: ${topEl.tag}`
          : `  element: ${reg.elementKey}`;
      }
    } else if (topEl.text) {
      fixedYaml += `  text: "${topEl.text}"`;
    }

    // Preserve other fields from original step yaml if provided
    if (stepYaml) {
      for (const raw of stepYaml.split("\n")) {
        const m = raw.match(/^\s+(value|timeout|text|variable|times|maxScrolls):\s*(.*)/);
        if (m && !(m[1] === "text" && topEl.text)) {
          fixedYaml += `\n  ${m[1]}: ${m[2]}`;
        }
      }
    }

    line(fixedYaml);
    line();
    line(`⚡ Confidence: ${(top.score * 100).toFixed(0)}%  (${top.tagScore > top.textScore ? "tag match" : "text match"})`);

    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── forge_read_test ───────────────────────────────────────────────────────────

server.tool(
  "forge_read_test",

  "Read the full YAML content of an existing Forge test file. " +
  "Always call this before editing a test — never reconstruct the steps from memory. " +
  "Pass filename only (e.g. shop_add_to_cart_toast.yaml) and optionally a subfolder.",

  {
    filename : z.string().describe("Test filename, e.g. shop_add_to_cart_toast.yaml"),
    subfolder: z.string().optional().describe("Subfolder, e.g. shop, login, home"),
  },

  ({ filename, subfolder }) => {
    const fname = filename.endsWith(".yaml") || filename.endsWith(".yml")
      ? filename : filename + ".yaml";

    // Try the given subfolder first, then search all subdirs
    const candidates = subfolder
      ? [join(TESTDATA_ROOT, subfolder, fname)]
      : [
          join(TESTDATA_ROOT, fname),
          ...["shop","login","home","profile","rewards","upi","billpay"].map(
            d => join(TESTDATA_ROOT, d, fname)
          ),
        ];

    for (const p of candidates) {
      if (existsSync(p)) {
        const content = readFileSync(p, "utf8");
        const rel = p.replace(FORGE_ROOT + "/", "");
        return { content: [{ type: "text", text: `📄 ${rel}\n\n${content}` }] };
      }
    }

    return { content: [{ type: "text", text: `❌ File not found: ${fname}` }] };
  }
);

// ── forge_save_test ───────────────────────────────────────────────────────────

const TESTDATA_ROOT = join(FORGE_ROOT, "src/test/java/com/popclub/androidTests");

server.tool(
  "forge_save_test",

  "Save a generated Forge YAML test to the correct location under " +
  "src/test/java/com/popclub/androidTests/<subfolder>/<filename>.yaml. " +
  "Always use this tool to save generated tests — never write the file directly. " +
  "If subfolder is omitted, the file goes in the root androidTests directory. " +
  "Returns the saved path so you can show it to the user.",

  {
    filename : z.string().describe("File name only, e.g. ts_add_to_cart.yaml"),
    content  : z.string().describe("Full YAML content of the test"),
    subfolder: z.string().optional().describe("Optional subfolder, e.g. shop, login, home, profile"),
  },

  ({ filename, content, subfolder }) => {
    // Ensure .yaml extension
    const fname = filename.endsWith(".yaml") || filename.endsWith(".yml")
      ? filename : filename + ".yaml";

    const dir     = subfolder
      ? join(TESTDATA_ROOT, subfolder)
      : TESTDATA_ROOT;
    const absPath = join(dir, fname);

    // Safety: must stay inside TESTDATA_ROOT
    if (!absPath.startsWith(TESTDATA_ROOT)) {
      return { content: [{ type: "text", text: "❌ Path traversal denied." }] };
    }

    try {
      mkdirSync(dir, { recursive: true });
      writeFileSync(absPath, content, "utf8");
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Save failed: ${e.message}` }] };
    }

    const rel = absPath.replace(FORGE_ROOT + "/", "");
    return {
      content: [{
        type: "text",
        text: [
          `✅ Saved: ${rel}`,
          ``,
          `To run:`,
          `   mvn test -DtestFile=${fname}`,
        ].join("\n"),
      }]
    };
  }
);

// ── forge_list_elements ───────────────────────────────────────────────────────

server.tool(
  "forge_list_elements",

  "List all registered elements from the elements/*.yaml files. " +
  "Optionally filter by file (e.g. shop.yaml) or search for a keyword in the key name. " +
  "Useful before writing a test — check what element keys are already available.",

  {
    file  : z.string().optional().describe("Filter by YAML file, e.g. shop.yaml"),
    search: z.string().optional().describe("Keyword to search in element key names"),
  },

  ({ file, search }) => {
    const L = [];
    const line = s => L.push(s ?? "");

    try {
      const files = readdirSync(ELEMENTS_DIR).filter(f => f.endsWith(".yaml") || f.endsWith(".yml")).sort();
      const targetFiles = file ? files.filter(f => f === file || f === file + ".yaml") : files;

      if (!targetFiles.length) {
        return { content: [{ type: "text", text: `❌ No element files found${file ? ` matching "${file}"` : ""}.` }] };
      }

      let total = 0;
      for (const fname of targetFiles) {
        let parsed;
        try { parsed = parseYaml(readFileSync(join(ELEMENTS_DIR, fname), "utf8")); } catch (_) { continue; }
        if (!parsed || typeof parsed !== "object") continue;

        const entries = Object.entries(parsed);
        const filtered = search
          ? entries.filter(([k]) => k.toLowerCase().includes(search.toLowerCase()))
          : entries;

        if (!filtered.length) continue;

        line(`\n📄 ${fname}  (${filtered.length} element${filtered.length !== 1 ? "s" : ""})`);
        line("─".repeat(50));

        for (const [key, def] of filtered) {
          if (!def) continue;
          const al = Array.isArray(def.android) ? def.android : [];
          const il = Array.isArray(def.ios)     ? def.ios     : [];
          const androidVal = al[0]?.value || "—";
          const iosVal     = il[0]?.value || "—";
          line(`  ${key}`);
          line(`    android: ${androidVal}`);
          if (il.length) line(`    ios:     ${iosVal}`);
          total++;
        }
      }

      if (!total) {
        return { content: [{ type: "text", text: `No elements found${search ? ` matching "${search}"` : ""}.` }] };
      }

      line(`\n${"─".repeat(50)}`);
      line(`Total: ${total} element${total !== 1 ? "s" : ""}${search ? ` matching "${search}"` : ""}`);
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Error reading elements: ${e.message}` }] };
    }

    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── forge_save_element ────────────────────────────────────────────────────────

server.tool(
  "forge_save_element",

  "Add or update a registered element in an elements/*.yaml file. " +
  "Keys are sorted alphabetically after saving. " +
  "Use this when you discover a new testTag on screen and want to register it " +
  "so tests can use  element: <key>  instead of  locator: <tag>.",

  {
    file         : z.string().describe("Target YAML file, e.g. shop.yaml"),
    key          : z.string().describe("Element key (snake_case), e.g. cart_checkout_button"),
    androidValue : z.string().describe("Android locator value (accessibilityId / testTag value)"),
    androidType  : z.string().optional().describe("Android locator type (default: accessibilityId)"),
    iosValue     : z.string().optional().describe("iOS locator value (same as android if omitted)"),
    iosType      : z.string().optional().describe("iOS locator type (default: accessibilityId)"),
  },

  ({ file, key, androidValue, androidType = "accessibilityId", iosValue, iosType = "accessibilityId" }) => {
    if (!/^[a-zA-Z0-9_]+$/.test(key)) {
      return { content: [{ type: "text", text: `❌ Key "${key}" must be alphanumeric/underscore only.` }] };
    }

    const filePath = join(ELEMENTS_DIR, file.endsWith(".yaml") ? file : file + ".yaml");
    if (!filePath.startsWith(ELEMENTS_DIR)) {
      return { content: [{ type: "text", text: "❌ Path traversal denied." }] };
    }
    if (!existsSync(filePath)) {
      return { content: [{ type: "text", text: `❌ File not found: ${basename(filePath)}` }] };
    }

    try {
      let parsed = {};
      try { parsed = parseYaml(readFileSync(filePath, "utf8")) || {}; } catch (_) {}

      const def = { android: [{ type: androidType, value: androidValue }] };
      if (iosValue) def.ios = [{ type: iosType, value: iosValue }];
      parsed[key] = def;

      // Sort keys alphabetically
      const sorted = Object.fromEntries(
        Object.entries(parsed).sort(([a], [b]) => a.localeCompare(b))
      );
      writeFileSync(filePath, stringifyYaml(sorted, { lineWidth: 0 }), "utf8");

      return { content: [{ type: "text", text: `✅ Saved element "${key}" to ${basename(filePath)}\n\nUse in tests:\n  element: ${key}` }] };
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Save failed: ${e.message}` }] };
    }
  }
);

// ── forge_delete_element ──────────────────────────────────────────────────────

server.tool(
  "forge_delete_element",

  "Remove a registered element key from an elements/*.yaml file. " +
  "Use with care — check forge_check_elements first to confirm the key is unused.",

  {
    file: z.string().describe("YAML file, e.g. shop.yaml"),
    key : z.string().describe("Element key to delete, e.g. old_cart_button"),
  },

  ({ file, key }) => {
    const filePath = join(ELEMENTS_DIR, file.endsWith(".yaml") ? file : file + ".yaml");
    if (!filePath.startsWith(ELEMENTS_DIR)) {
      return { content: [{ type: "text", text: "❌ Path traversal denied." }] };
    }
    if (!existsSync(filePath)) {
      return { content: [{ type: "text", text: `❌ File not found: ${basename(filePath)}` }] };
    }

    try {
      let parsed = parseYaml(readFileSync(filePath, "utf8")) || {};
      if (!(key in parsed)) {
        return { content: [{ type: "text", text: `⚠️  Key "${key}" not found in ${basename(filePath)}.` }] };
      }
      delete parsed[key];
      writeFileSync(filePath, stringifyYaml(parsed, { lineWidth: 0 }), "utf8");
      return { content: [{ type: "text", text: `✅ Deleted "${key}" from ${basename(filePath)}.` }] };
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Delete failed: ${e.message}` }] };
    }
  }
);

// ── forge_check_elements ──────────────────────────────────────────────────────

server.tool(
  "forge_check_elements",

  "Scan all Forge test YAML files and elements/*.yaml files to produce a health report: " +
  "(1) Missing — element keys used in tests but not defined in any elements file. " +
  "(2) Unused  — element keys defined in elements files but never referenced in any test. " +
  "Run this periodically to keep the element registry clean.",

  {},

  () => {
    // ── Collect all defined element keys ──────────────────────────────────
    const defined = new Map(); // key → filename
    try {
      for (const f of readdirSync(ELEMENTS_DIR).filter(f => f.endsWith(".yaml"))) {
        const parsed = parseYaml(readFileSync(join(ELEMENTS_DIR, f), "utf8")) || {};
        for (const key of Object.keys(parsed)) defined.set(key, f);
      }
    } catch (_) {}

    // ── Collect all element: usages in test YAMLs ─────────────────────────
    const usages = new Map(); // key → [file, ...]

    function walkTests(dir) {
      try {
        for (const entry of readdirSync(dir)) {
          const full = join(dir, entry);
          if (entry.endsWith(".yaml") || entry.endsWith(".yml")) {
            try {
              const content = readFileSync(full, "utf8");
              for (const m of content.matchAll(/^\s+element:\s+(\S+)/gm)) {
                const key = m[1].replace(/#.*/, "").trim();
                const rel = full.replace(TESTDATA_ROOT + "/", "");
                if (!usages.has(key)) usages.set(key, []);
                usages.get(key).push(rel);
              }
            } catch (_) {}
          } else if (!entry.includes(".")) {
            walkTests(full);
          }
        }
      } catch (_) {}
    }
    walkTests(TESTDATA_ROOT);

    // ── Compute missing + unused ──────────────────────────────────────────
    const missing = []; // used but not defined
    const unused  = []; // defined but never used

    for (const [key, files] of usages) {
      if (!defined.has(key)) missing.push({ key, usedIn: [...new Set(files)] });
    }
    for (const [key, file] of defined) {
      if (!usages.has(key)) unused.push({ key, file });
    }

    const L = [];
    const line = s => L.push(s ?? "");

    line(`📊 Element Health Report`);
    line(`   Defined: ${defined.size}   Used: ${usages.size}   Missing: ${missing.length}   Unused: ${unused.length}`);
    line();

    if (missing.length) {
      line(`❌ MISSING (${missing.length}) — used in tests but not in any elements file:`);
      line("─".repeat(60));
      for (const { key, usedIn } of missing.sort((a, b) => a.key.localeCompare(b.key))) {
        line(`  ${key}`);
        usedIn.forEach(f => line(`    ← ${f}`));
      }
      line();
    } else {
      line("✅ No missing elements.");
      line();
    }

    if (unused.length) {
      line(`⚠️  UNUSED (${unused.length}) — defined but never referenced in any test:`);
      line("─".repeat(60));
      for (const { key, file } of unused.sort((a, b) => a.key.localeCompare(b.key))) {
        line(`  ${key}  (${file})`);
      }
      line();
      line("Consider removing unused elements to keep the registry clean.");
    } else {
      line("✅ No unused elements.");
    }

    return { content: [{ type: "text", text: L.join("\n") }] };
  }
);

// ── forge_rename_test ─────────────────────────────────────────────────────────

server.tool(
  "forge_rename_test",

  "Rename a Forge YAML test file. Both source and destination are relative to " +
  "src/test/java/com/popclub/androidTests/. The .yaml extension is added automatically.",

  {
    from: z.string().describe("Current file path, e.g. shop/ts_add_to_cart.yaml"),
    to  : z.string().describe("New file path, e.g. shop/ts_add_to_cart_v2.yaml"),
  },

  ({ from, to }) => {
    const addExt = p => (p.endsWith(".yaml") || p.endsWith(".yml")) ? p : p + ".yaml";
    const fromPath = join(TESTDATA_ROOT, addExt(from));
    const toPath   = join(TESTDATA_ROOT, addExt(to));

    if (!fromPath.startsWith(TESTDATA_ROOT) || !toPath.startsWith(TESTDATA_ROOT)) {
      return { content: [{ type: "text", text: "❌ Path traversal denied." }] };
    }
    if (!existsSync(fromPath)) {
      return { content: [{ type: "text", text: `❌ Source not found: ${from}` }] };
    }
    if (existsSync(toPath)) {
      return { content: [{ type: "text", text: `❌ Destination already exists: ${to}` }] };
    }

    try {
      mkdirSync(dirname(toPath), { recursive: true });
      renameSync(fromPath, toPath);
      return { content: [{ type: "text", text: `✅ Renamed:\n  ${from} → ${to}` }] };
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Rename failed: ${e.message}` }] };
    }
  }
);

// ── forge_duplicate_test ──────────────────────────────────────────────────────

server.tool(
  "forge_duplicate_test",

  "Duplicate a Forge YAML test file. Useful as a starting point for a new variant. " +
  "Paths are relative to src/test/java/com/popclub/androidTests/.",

  {
    source: z.string().describe("Source file path, e.g. shop/ts_add_to_cart.yaml"),
    dest  : z.string().describe("Destination file path, e.g. shop/ts_add_to_cart_guest.yaml"),
  },

  ({ source, dest }) => {
    const addExt = p => (p.endsWith(".yaml") || p.endsWith(".yml")) ? p : p + ".yaml";
    const srcPath  = join(TESTDATA_ROOT, addExt(source));
    const destPath = join(TESTDATA_ROOT, addExt(dest));

    if (!srcPath.startsWith(TESTDATA_ROOT) || !destPath.startsWith(TESTDATA_ROOT)) {
      return { content: [{ type: "text", text: "❌ Path traversal denied." }] };
    }
    if (!existsSync(srcPath)) {
      return { content: [{ type: "text", text: `❌ Source not found: ${source}` }] };
    }
    if (existsSync(destPath)) {
      return { content: [{ type: "text", text: `❌ Destination already exists: ${dest}` }] };
    }

    try {
      mkdirSync(dirname(destPath), { recursive: true });
      copyFileSync(srcPath, destPath);
      const content = readFileSync(destPath, "utf8");
      return { content: [{ type: "text", text: `✅ Duplicated:\n  ${source} → ${dest}\n\nContent:\n${content}` }] };
    } catch (e) {
      return { content: [{ type: "text", text: `❌ Duplicate failed: ${e.message}` }] };
    }
  }
);

// ── Start ─────────────────────────────────────────────────────────────────────
const transport = new StdioServerTransport();
await server.connect(transport);
