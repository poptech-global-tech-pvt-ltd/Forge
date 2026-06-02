#!/usr/bin/env python3
"""qa-app-scan.py — Scan a running qaDebug app screen by screen via Appium.

Connects to a running Appium session (or starts one), then for every screen
the user navigates to:
  1. Dumps the full accessibility tree
  2. Finds every interactive element (clickable / focusable / widget class)
  3. Checks existing content-desc values against the naming convention
  4. Flags elements with no content-desc (missing tags)
  5. Suggests a TestTags constant name from visible text + element class + screen

Output:
  reports/qa-app-scan/
    <ScreenName>.yaml     — per-screen report: good / bad / missing
    summary.yaml          — rolled-up totals
    missing_from_app.txt  — same format as missing_test_tags.txt (from source scan)
                            but derived from the LIVE app

Usage:
    # Interactive mode — navigate the app manually, press Enter to scan each screen:
    python3 scripts/qa-app-scan.py

    # Scan just the current screen and exit:
    python3 scripts/qa-app-scan.py --once

    # Specify Appium URL and package:
    python3 scripts/qa-app-scan.py --appium http://localhost:4723 --package com.popclub.android.debug

Requirements:
    pip install Appium-Python-Client PyYAML
    Appium server running, qaDebug APK installed, device connected via ADB.
"""
import argparse
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

try:
    from appium import webdriver
    from appium.webdriver.common.appiumby import AppiumBy
    from selenium.common.exceptions import WebDriverException
except ImportError:
    print("ERROR: Appium-Python-Client not installed.")
    print("  pip install Appium-Python-Client")
    sys.exit(1)

# ── Config ────────────────────────────────────────────────────────────────────

FORGE_ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = FORGE_ROOT / "reports" / "qa-app-scan"

APP_PACKAGE = "com.popclub.android.debug"   # qaDebug package name
APPIUM_URL  = "http://localhost:4723"

# Android widget classes that are inherently interactive
INTERACTIVE_CLASSES = {
    "android.widget.Button",
    "android.widget.ImageButton",
    "android.widget.EditText",
    "android.widget.CheckBox",
    "android.widget.RadioButton",
    "android.widget.Switch",
    "android.widget.ToggleButton",
    "android.widget.Spinner",
    "android.widget.SeekBar",
}

# content-desc values that are expected to be generic (accessibility-only, not test tags)
ALLOWED_GENERIC = {
    "Close", "Back", "Navigate up", "More options",
    "Navigate up", "Open navigation drawer",
}

# Known good screen prefixes (from TestTags.kt conventions)
KNOWN_PREFIXES = [
    "login_", "otp_", "home_", "profile_", "send_money_", "enter_amount_",
    "tss_", "everything_upi_", "upi_", "bank_transfer_", "txn_",
    "payment_success_", "bill_", "recharge_", "cart_", "shop_", "product_",
    "order_", "rewards_", "cashback_", "pop_coins_", "credit_card_", "cc_",
    "rupay_", "toolbar_", "common_", "app_update_", "faq_", "help_support_",
    "address_", "wishlist_", "mandate_", "check_balance_", "request_money_",
]

# ── Naming helpers ─────────────────────────────────────────────────────────────

_SCREEN_SUFFIXES = ("Activity", "Fragment", "Screen", "Dialog", "BottomSheet", "Page")

def camel_to_snake(s: str) -> str:
    s1 = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", s)
    return re.sub(r"([a-z\d])([A-Z])", r"\1_\2", s1).lower()


def text_to_snake(text: str) -> str:
    """'Pay Now' → 'pay_now',  'Add Favourite!' → 'add_favourite'"""
    text = re.sub(r"[^a-zA-Z0-9\s]", "", text).strip()
    return re.sub(r"\s+", "_", text).lower()


def strip_package(activity: str) -> str:
    """'com.popclub.android.profile.ProfileFragment' → 'ProfileFragment'"""
    return activity.split(".")[-1]


def screen_prefix(screen_name: str) -> str:
    stem = screen_name
    for s in _SCREEN_SUFFIXES:
        if stem.endswith(s):
            stem = stem[: -len(s)]
            break
    return camel_to_snake(stem)


def classify_element(class_name: str) -> str:
    lc = class_name.lower()
    if "button" in lc or "imagebutton" in lc:
        return "button"
    if "edittext" in lc:
        return "input"
    if "checkbox" in lc:
        return "checkbox"
    if "radiobutton" in lc:
        return "radio"
    if "switch" in lc or "toggle" in lc:
        return "switch"
    return "row"


def suggest_tag(screen_name: str, text: str, class_name: str, nth: int) -> str:
    """Generate a suggested TestTags declaration.

    Uses visible text first (most semantic), falls back to element class.
    nth > 0  →  indexed helper  fun screenTextItem(index)
    nth == 0 →  const val SCREEN_TEXT_TYPE = "screen_text_type"
    """
    prefix = screen_prefix(screen_name)
    element_type = classify_element(class_name)

    if text and len(text.strip()) > 0 and len(text.strip()) < 40:
        label = text_to_snake(text.strip())
        # Drop if it's a number or very short
        if len(label) < 2 or label.isdigit():
            label = element_type
    else:
        label = element_type

    base = f"{prefix}_{label}"
    # Deduplicate consecutive identical words
    parts = base.split("_")
    deduped = [parts[0]] if parts else []
    for p in parts[1:]:
        if p and p != deduped[-1]:
            deduped.append(p)
    base = "_".join(deduped)

    if nth > 0:
        func = re.sub(r"_([a-z])", lambda m: m.group(1).upper(), base)
        func = func[0].lower() + func[1:] + "Item"
        return f'fun {func}(index: Int) = "{base}_item_$index"'
    return f'const val {base.upper()} = "{base}"'


# ── Naming convention checker ─────────────────────────────────────────────────

def check_convention(tag: str) -> Optional[str]:
    """Returns None if tag is fine, or a string describing the problem."""
    if tag in ALLOWED_GENERIC:
        return None  # accessibility-only label, not a test tag

    # Must be snake_case
    if not re.match(r"^[a-z][a-z0-9_]+$", tag):
        if re.match(r"^[A-Z]", tag):
            return f"PascalCase or Title Case — should be snake_case with screen prefix"
        if " " in tag:
            return f"contains spaces — should be snake_case"
        return f"not snake_case — use lowercase_with_underscores"

    # Must have a known screen prefix
    if not any(tag.startswith(p) for p in KNOWN_PREFIXES):
        return f"no screen prefix — tag is too generic, prefix with screen name (e.g. home_{tag})"

    return None


# ── Element tree walker ───────────────────────────────────────────────────────

def get_all_elements(driver) -> list[dict]:
    """Return list of {tag, text, class, clickable, focusable, bounds} dicts."""
    results = []
    try:
        elements = driver.find_elements(AppiumBy.XPATH, "//*")
        for el in elements:
            try:
                tag         = el.get_attribute("content-desc") or ""
                text        = el.get_attribute("text") or ""
                class_name  = el.get_attribute("class") or ""
                clickable   = el.get_attribute("clickable") == "true"
                focusable   = el.get_attribute("focusable") == "true"
                resource_id = el.get_attribute("resource-id") or ""
                bounds      = el.get_attribute("bounds") or ""
                enabled     = el.get_attribute("enabled") == "true"

                results.append({
                    "tag":         tag.strip(),
                    "text":        text.strip(),
                    "class":       class_name,
                    "clickable":   clickable,
                    "focusable":   focusable,
                    "resource_id": resource_id,
                    "bounds":      bounds,
                    "enabled":     enabled,
                })
            except Exception:
                continue
    except Exception as e:
        print(f"  ⚠️  Error fetching elements: {e}")
    return results


def is_interactive(el: dict) -> bool:
    return (
        el["clickable"]
        or el["focusable"]
        or el["class"] in INTERACTIVE_CLASSES
    )


# ── Screen scanner ────────────────────────────────────────────────────────────

def scan_screen(driver, screen_name: str) -> dict:
    """Scan the current screen. Returns structured report dict."""
    print(f"\n  🔍 Scanning {screen_name}…")
    elements = get_all_elements(driver)

    good:    list[dict] = []   # tagged + naming convention OK
    bad:     list[dict] = []   # tagged + naming convention BROKEN
    missing: list[dict] = []   # interactive + NO tag

    # Count (text, class) pairs to detect indexed elements
    interactive_els = [e for e in elements if is_interactive(e) and e["enabled"]]
    type_counts: dict[tuple[str, str], int] = {}
    for el in interactive_els:
        key = (text_to_snake(el["text"]) if el["text"] else "", el["class"])
        type_counts[key] = type_counts.get(key, 0) + 1

    type_seen: dict[tuple[str, str], int] = {}
    for el in interactive_els:
        tag        = el["tag"]
        text       = el["text"]
        class_name = el["class"]
        key        = (text_to_snake(text) if text else "", class_name)
        nth        = type_seen.get(key, 0)
        type_seen[key] = nth + 1

        if tag:
            if tag in ALLOWED_GENERIC:
                continue  # skip accessibility-only labels
            problem = check_convention(tag)
            if problem:
                bad.append({
                    "tag":     tag,
                    "problem": problem,
                    "text":    text,
                    "class":   class_name,
                    "bounds":  el["bounds"],
                    "suggested": suggest_tag(screen_name, text, class_name,
                                             nth if type_counts[key] > 1 else 0),
                })
            else:
                good.append({
                    "tag":    tag,
                    "text":   text,
                    "class":  class_name,
                    "bounds": el["bounds"],
                })
        else:
            missing.append({
                "text":      text,
                "class":     class_name,
                "bounds":    el["bounds"],
                "suggested": suggest_tag(screen_name, text, class_name,
                                         nth if type_counts[key] > 1 else 0),
            })

    return {
        "screen":  screen_name,
        "scanned": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "counts":  {"good": len(good), "bad_naming": len(bad), "missing": len(missing)},
        "good":    good,
        "bad_naming": bad,
        "missing": missing,
    }


# ── Report writers ─────────────────────────────────────────────────────────────

def write_screen_yaml(report: dict) -> Path:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = REPORT_DIR / f"{report['screen']}.yaml"

    lines = [
        f"# Screen: {report['screen']}",
        f"# Scanned: {report['scanned']}",
        f"# Good: {report['counts']['good']}  |  Bad naming: {report['counts']['bad_naming']}  |  Missing: {report['counts']['missing']}",
        "",
        f"screen: {report['screen']}",
        f"scanned: \"{report['scanned']}\"",
        "",
        "good_tags:",
    ]
    for e in report["good"]:
        lines.append(f"  - tag: {e['tag']}")
        if e["text"]:
            lines.append(f"    text: \"{e['text']}\"")
        lines.append(f"    class: {e['class']}")

    lines += ["", "bad_naming:"]
    for e in report["bad_naming"]:
        lines.append(f"  - tag: \"{e['tag']}\"")
        lines.append(f"    problem: \"{e['problem']}\"")
        if e["text"]:
            lines.append(f"    text: \"{e['text']}\"")
        lines.append(f"    suggested: \"{e['suggested']}\"")

    lines += ["", "missing_tags:"]
    for e in report["missing"]:
        lines.append(f"  - class: {e['class']}")
        if e["text"]:
            lines.append(f"    text: \"{e['text']}\"")
        lines.append(f"    bounds: \"{e['bounds']}\"")
        lines.append(f"    suggested: \"{e['suggested']}\"")

    out_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return out_path


def write_summary(all_reports: list[dict]) -> None:
    total_good    = sum(r["counts"]["good"] for r in all_reports)
    total_bad     = sum(r["counts"]["bad_naming"] for r in all_reports)
    total_missing = sum(r["counts"]["missing"] for r in all_reports)

    lines = [
        "# qa-app-scan summary",
        f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "",
        f"total_screens_scanned: {len(all_reports)}",
        f"total_good_tags: {total_good}",
        f"total_bad_naming: {total_bad}",
        f"total_missing_tags: {total_missing}",
        "",
        "screens:",
    ]
    for r in all_reports:
        lines.append(f"  {r['screen']}:")
        lines.append(f"    good: {r['counts']['good']}")
        lines.append(f"    bad_naming: {r['counts']['bad_naming']}")
        lines.append(f"    missing: {r['counts']['missing']}")

    (REPORT_DIR / "summary.yaml").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # Also write a missing_from_app.txt in Forge root (same format as missing_test_tags.txt)
    txt_lines = [
        "# Missing Tags — detected from LIVE APP (qa-app-scan.py)",
        f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        "#",
        "# Source: runtime accessibility tree (what Appium actually sees)",
        "# These elements are INTERACTIVE but have no content-desc on device.",
        "# Suggestions are based on visible text — more accurate than source-only scanning.",
        "",
    ]
    for r in all_reports:
        if not r["missing"] and not r["bad_naming"]:
            continue
        txt_lines.append("─" * 80)
        txt_lines.append(f"Screen : {r['screen']}")
        txt_lines.append("")
        if r["bad_naming"]:
            txt_lines.append("  ⚠️  BAD NAMING (fix these — Appium may pick wrong element):")
            for e in r["bad_naming"]:
                txt_lines.append(f"    tag   : \"{e['tag']}\"")
                txt_lines.append(f"    issue : {e['problem']}")
                txt_lines.append(f"    text  : \"{e['text']}\"")
                txt_lines.append(f"    💡      {e['suggested']}")
                txt_lines.append("")
        if r["missing"]:
            txt_lines.append("  ❌  MISSING TAGS (Appium cannot find these):")
            for e in r["missing"]:
                label = f"\"{e['text']}\"" if e["text"] else e["class"]
                txt_lines.append(f"    element : {label}  [{e['class']}]  {e['bounds']}")
                txt_lines.append(f"    💡        {e['suggested']}")
                txt_lines.append("")

    (FORGE_ROOT / "missing_from_app.txt").write_text("\n".join(txt_lines), encoding="utf-8")
    print(f"\n  📄 missing_from_app.txt written to Forge root")


# ── Appium connection ─────────────────────────────────────────────────────────

def connect(appium_url: str, package: str) -> webdriver.Remote:
    """Attach to an already-running Appium session or start a new one."""
    from appium.options import AppiumOptions
    options = AppiumOptions()
    options.platform_name = "Android"
    options.automation_name = "UiAutomator2"
    options.app_package = package
    options.set_capability("noReset", True)
    options.set_capability("autoLaunch", False)   # attach to running app

    print(f"  Connecting to Appium at {appium_url}…")
    driver = webdriver.Remote(appium_url, options=options)
    driver.implicitly_wait(5)
    return driver


def current_screen_name(driver, package: str) -> str:
    """Derive a screen name from the current activity."""
    try:
        activity = driver.current_activity or ""
        name = strip_package(activity)
        if name and name not in ("", package):
            return name
    except Exception:
        pass
    return "UnknownScreen"


# ── Entry point ────────────────────────────────────────────────────────────────

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--appium",  default=APPIUM_URL,  help="Appium server URL")
    parser.add_argument("--package", default=APP_PACKAGE, help="App package name")
    parser.add_argument("--once",    action="store_true",  help="Scan current screen once and exit")
    parser.add_argument("--name",    help="Override screen name (default: auto-detect from activity)")
    args = parser.parse_args()

    try:
        driver = connect(args.appium, args.package)
    except Exception as e:
        print(f"\n❌ Could not connect to Appium: {e}")
        print("   Make sure:")
        print("   1. Appium server is running:  appium")
        print("   2. qaDebug APK is installed on the device")
        print("   3. Device is connected:  adb devices")
        return 1

    all_reports: list[dict] = []

    try:
        if args.once:
            screen_name = args.name or current_screen_name(driver, args.package)
            report = scan_screen(driver, screen_name)
            path = write_screen_yaml(report)
            all_reports.append(report)
            print(f"\n  ✅ {screen_name}: {report['counts']['good']} good  "
                  f"| {report['counts']['bad_naming']} bad naming  "
                  f"| {report['counts']['missing']} missing")
            print(f"  📄 {path.relative_to(FORGE_ROOT)}")
        else:
            print("\n━━━ QA App Scanner — Interactive Mode ━━━")
            print("Navigate to a screen in the app, then press Enter to scan it.")
            print("Type a screen name to override auto-detection, or just Enter to use the detected name.")
            print("Type 'done' to finish and write the summary.\n")

            while True:
                raw = input("► Press Enter to scan (or 'done' to finish): ").strip()
                if raw.lower() in ("done", "exit", "q"):
                    break

                screen_name = raw if raw else (args.name or current_screen_name(driver, args.package))
                report = scan_screen(driver, screen_name)
                path = write_screen_yaml(report)
                all_reports.append(report)

                g = report["counts"]["good"]
                b = report["counts"]["bad_naming"]
                m = report["counts"]["missing"]
                print(f"\n  ✅ {screen_name}:  {g} good  |  {b} bad naming  |  {m} missing")

                if b:
                    print(f"  ⚠️  Bad naming examples:")
                    for e in report["bad_naming"][:3]:
                        print(f"     \"{e['tag']}\" → {e['problem']}")
                        print(f"     💡 {e['suggested']}")
                if m:
                    print(f"  ❌  Missing tag examples:")
                    for e in report["missing"][:3]:
                        label = f'"{e["text"]}"' if e["text"] else e["class"]
                        print(f"     {label} [{e['class']}]")
                        print(f"     💡 {e['suggested']}")
                print(f"  📄 {path.relative_to(FORGE_ROOT)}\n")

    finally:
        driver.quit()

    if all_reports:
        write_summary(all_reports)
        total_bad     = sum(r["counts"]["bad_naming"] for r in all_reports)
        total_missing = sum(r["counts"]["missing"] for r in all_reports)
        print(f"\n━━━ Summary ━━━")
        print(f"  Screens scanned : {len(all_reports)}")
        print(f"  Bad naming      : {total_bad}  ← fix in Popdroid TestTags + screen files")
        print(f"  Missing tags    : {total_missing}  ← add .qaTestTag() in Popdroid")
        print(f"  Reports         : {REPORT_DIR.relative_to(FORGE_ROOT)}/")
        print(f"  Full gap list   : missing_from_app.txt\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
