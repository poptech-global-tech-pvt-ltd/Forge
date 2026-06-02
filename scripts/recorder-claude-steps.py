#!/usr/bin/env python3
"""recorder-claude-steps.py — Step-guided recording with manually written steps.

No TestSigma needed. You write the steps, the script walks you through them
on the device one by one, captures a screenshot after each, then sends
everything to Claude to produce the same output as recorder-claude-testsigma.py.

Two ways to provide steps
─────────────────────────
  1. Steps file (one step per line):
       python3.11 scripts/recorder-claude-steps.py \\
           --steps steps/shop_checkout.txt \\
           --name shop_checkout

  2. Type steps interactively at the start:
       python3.11 scripts/recorder-claude-steps.py \\
           --name shop_checkout

Steps file format (steps/shop_checkout.txt)
───────────────────────────────────────────
  Open the app and wait for the home screen
  Tap the Shop tab in the bottom navigation
  Scroll down to find a product and tap it
  Tap "Add to cart" on the product detail page
  Open the cart and tap "Pay"
  Complete payment with default method
  Verify the order success screen

Output  (all saved to reports/<name>/)
──────
  reports/<name>/<name>.md                ← element inventory + coverage gaps
  reports/<name>/<name>_asserts.yaml      ← Maestro assertVisible flow
  reports/<name>/ts_<name>.yaml           ← Forge-automatable YAML (Claude-generated)
  reports/<name>/<name>_missing.yaml      ← feed to /qa-tags
"""

import argparse
import io
import os
import re
import subprocess
import sys
import tempfile
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

# ── Dependency check ──────────────────────────────────────────────────────────
try:
    from PIL import Image
except ImportError:
    print("❌  Pillow not installed. Run: pip3 install Pillow", file=sys.stderr)
    sys.exit(1)

# ── Paths ─────────────────────────────────────────────────────────────────────
FORGE_ROOT       = Path(__file__).resolve().parents[1]
DEFAULT_POPDROID = FORGE_ROOT.parent / "popdroid"
TEST_TAGS_PATH   = "core/src/main/java/com/popclub/core/TestTags.kt"
APK_DIR          = FORGE_ROOT / "src" / "main" / "resources"

APP_PACKAGE  = "com.popclub.android"
APP_ACTIVITY = "com.popclub.android.LauncherFresh"
FRAME_WIDTH  = 540

# ─────────────────────────────────────────────────────────────────────────────
# Steps input
# ─────────────────────────────────────────────────────────────────────────────

def read_steps_file(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    return [l.strip() for l in lines if l.strip() and not l.strip().startswith("#")]

def prompt_steps_interactively() -> list[str]:
    print("\n📝  Enter your test steps (one per line).")
    print("    Press ENTER on an empty line when done.\n")
    steps = []
    i = 1
    while True:
        try:
            line = input(f"  Step {i}: ").strip()
        except (EOFError, KeyboardInterrupt):
            break
        if not line:
            if steps:
                break
            continue
        steps.append(line)
        i += 1
    return steps

# ─────────────────────────────────────────────────────────────────────────────
# TestTags parser
# ─────────────────────────────────────────────────────────────────────────────

def parse_existing_tags(popdroid: Path) -> set[str]:
    path = popdroid / TEST_TAGS_PATH
    if not path.exists():
        print(f"  ⚠️  TestTags.kt not found at {path}")
        return set()
    text   = path.read_text(encoding="utf-8")
    consts = set(re.findall(r'const val\s+(\w+)\s*=\s*"([^"]+)"', text))
    names  = {c[0] for c in consts}
    values = {c[1] for c in consts}
    fns    = set(re.findall(r"fun\s+(\w+)\s*\(index:", text))
    return names | values | fns

def format_tags_for_prompt(tags: set[str], limit: int = 100) -> str:
    sample = sorted(tags)[:limit]
    suffix = f" … +{len(tags)-limit} more" if len(tags) > limit else ""
    return ", ".join(sample) + suffix

# ─────────────────────────────────────────────────────────────────────────────
# ADB helpers
# ─────────────────────────────────────────────────────────────────────────────

def adb(device: Optional[str], *args) -> subprocess.CompletedProcess:
    cmd = ["adb"] + (["-s", device] if device else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=15)

def adb_screencap(device: Optional[str], retries: int = 3) -> Optional[Image.Image]:
    cmd = ["adb"] + (["-s", device] if device else []) + ["exec-out", "screencap", "-p"]
    for attempt in range(1, retries + 1):
        try:
            r = subprocess.run(cmd, capture_output=True, timeout=15)
            if r.returncode == 0 and r.stdout:
                return Image.open(io.BytesIO(r.stdout))
        except Exception:
            pass
        if attempt < retries:
            print(f"  ⏱️  Screenshot attempt {attempt} failed — retrying…")
            time.sleep(1.5)
    return None

def resize_for_api(img: Image.Image, width: int = FRAME_WIDTH) -> Image.Image:
    w, h = img.size
    return img if w <= width else img.resize((width, int(h * width / w)), Image.LANCZOS)

def ensure_app_launched(device: Optional[str]) -> None:
    r = adb(device, "shell", "pm", "list", "packages", APP_PACKAGE)
    if APP_PACKAGE not in r.stdout:
        apks = sorted(APK_DIR.glob("*.apk"))
        if not apks:
            print(f"❌  App not installed and no APK in {APK_DIR}", file=sys.stderr)
            sys.exit(1)
        print(f"  Installing {apks[0].name}…")
        subprocess.run(["adb"] + (["-s", device] if device else []) +
                       ["install", "-r", str(apks[0])], text=True)
    adb(device, "shell", "svc", "power", "stayon", "true")
    adb(device, "shell", "input", "keyevent", "224")
    time.sleep(0.5)
    adb(device, "shell", "wm", "dismiss-keyguard")
    adb(device, "shell", "input", "keyevent", "82")
    time.sleep(0.5)
    adb(device, "shell", "am", "force-stop", APP_PACKAGE)
    time.sleep(0.5)
    adb(device, "shell", "am", "start", "-n", f"{APP_PACKAGE}/{APP_ACTIVITY}")
    print("  Waiting 4s for app to load…")
    time.sleep(4)

# ─────────────────────────────────────────────────────────────────────────────
# Claude prompt
# ─────────────────────────────────────────────────────────────────────────────

ELEMENT_FILES = [
    "common", "login", "home", "shop", "cart",
    "upi", "profile", "rewards", "billpay", "credit_card",
]

PROMPT_TEMPLATE = """\
You are a QA automation engineer for the POP Club Android app (Jetpack Compose).

You have two inputs:
  1. Manually written test steps (plain English, what the tester did)
  2. Device screenshots — one per step, showing the app state AFTER that step

Your job:
  A. Identify every interactive element visible
  B. Check which elements already exist in the Forge element repository
  C. Convert the raw steps into an automatable Forge YAML test file
  D. Output new element repository entries for any element NOT already in the repo

━━━ FLOW CONTEXT ━━━
{context}

━━━ TEST STEPS (manually written) ━━━
{raw_steps}

━━━ EXISTING TESTTAGS ({tag_count} constants — mark ✅, do NOT re-suggest) ━━━
{existing_str}

━━━ FORGE ELEMENT REPOSITORY ━━━
These files define the element locators the test runner uses.
Read each one to know which element keys already exist before suggesting new ones.
{element_repo_paths}
Element key format in these files:
  element_key_name:
    android:
      - type: accessibilityId
        value: <qaTestTag value>
Rule: use existing keys wherever possible. Only add new entries when the element
key is genuinely absent from ALL element files.

━━━ FORGE YAML FORMAT ━━━
```yaml
testName: <Descriptive name>
platform: android
noReset: false
tags:
  - smoke
retry: 1
steps:
  - action: launchApp
  - action: tap
    element: shop_tab
  - action: waitFor
    element: shop_search_bar
  - action: scroll
    direction: down
  - action: tap
    element: shop_product_item_0
  - action: verifyElement
    element: product_detail_add_to_cart_button
  - action: enterText
    element: search_input
    value: "yoga bar"
```
Available actions: launchApp, tapIfPresent, tap, enterText, waitFor,
verifyElement, scroll, swipe, back, assertText

━━━ NAMING RULES FOR NEW TAGS ━━━
• Fixed element  → SCREAMING_SNAKE_CASE  e.g. CART_PAY_BUTTON, LOGIN_CONTINUE_BUTTON
• Suffixes       → _BUTTON  _INPUT  _ROW  _TAB  _CHIP  _CHECKBOX  _ICON
• Repeated items → fun camelCase(index: Int)  e.g. fun shopProductItem(index: Int)
• Prefix         → screen/section: HOME_  CART_  SHOP_  PRODUCT_DETAIL_  LOGIN_
• Global chrome  → COMMON_  e.g. COMMON_BACK_BUTTON, COMMON_HOME_TAB
• In Forge YAML always use the snake_case VALUE, not the const name

━━━ OUTPUT — produce ALL FIVE sections ━━━

## 1. Interactive Element Tag Analysis — {context}

| # | Screen | Element Description | Element Key | qaTestTag Value | In Repo | In TestTags |
|---|--------|---------------------|-------------|-----------------|---------|-------------|
| 1 | Login | Mobile number input | `login_mobile_input` | `login_mobile_input` | ❌ | ❌ |
| 2 | Home | Shop bottom nav tab | `common_shop_tab` | `common_shop_tab` | ✅ | ✅ |
| 3 | Cart | Pay CTA button | `cart_pay_button` | `cart_pay_button` | ✅ | ✅ |
(one row per interactive element — be exhaustive)

## 2. Coverage Gaps

Group by screen. Note:
- Steps that could not be mapped to a visible element
- Edge states (loading, empty, error) not captured
- Elements ambiguous from screenshots alone

## 3. Forge Test YAML

```yaml
# FILE: ts_{name}.yaml
testName: {context}
platform: android
noReset: false
features:
  - login
  - home
  - shop
testCaseIds:
  - {name}
tags:
  - smoke
retry: 1
steps:
  - action: launchApp
  (map each step using the element KEY from Section 1, not the tag value)
```

## 4. Missing Tags YAML

```yaml
missing:
  # ── <screen name> ──
  - element:    <human description>
    class:      <android.widget.Button | android.widget.TextView | android.view.View>
    bounds:     (unknown)
    suggestion: const val SCREEN_ELEMENT_KIND = "screen_element_kind"

  # ── repeated item ──
  - element:    Product grid card
    class:      android.view.View
    bounds:     (unknown)
    suggestion: fun shopProductItem(index: Int) = "shop_product_item_$index"
```
Only ❌ TestTags elements from Section 1.

## 5. Element Repository Entries

For every element where "In Repo" = ❌ in Section 1, output YAML blocks grouped
by which file they belong in (common / login / home / shop / cart / upi / profile).
Decide the file based on which screen the element appears on.

```yaml
# FILE: common.yaml
common_shop_tab:
  android:
    - type: accessibilityId
      value: common_shop_tab

# FILE: shop.yaml
shop_search_button:
  android:
    - type: accessibilityId
      value: shop_search_button

product_list_item_0:
  android:
    - type: accessibilityId
      value: product_list_item_0
```

Only include elements that are genuinely absent from the element files you read.
Each block starts with a `# FILE: <name>.yaml` comment so the writer knows where to append.
"""

# ─────────────────────────────────────────────────────────────────────────────
# Claude call
# ─────────────────────────────────────────────────────────────────────────────

def analyse_frames(frames: list[Image.Image],
                   frame_notes: dict[int, str],
                   context: str,
                   existing_tags: set[str],
                   all_steps: list[str],
                   name: str = "",
                   out_dir: Path = None) -> tuple:
    import json

    # Save screenshots to the output dir (persistent, not a tempdir)
    shots_dir = out_dir / "screenshots"
    shots_dir.mkdir(parents=True, exist_ok=True)
    for i, img in enumerate(frames):
        img.save(shots_dir / f"step_{i+1:02d}.png", format="PNG", optimize=True)

    raw_steps_text = "\n".join(f"  {i+1}. {s}" for i, s in enumerate(all_steps))

    mapping = {
        i: frame_notes.get(i, f"Step {i+1}") for i in range(len(frames))
    }

    # Build element repo paths block
    elements_dir = FORGE_ROOT / "src" / "test" / "resources" / "elements"
    repo_paths_lines = []
    for fname in ELEMENT_FILES:
        p = elements_dir / f"{fname}.yaml"
        if p.exists():
            repo_paths_lines.append(f"  {p}  (feature: {fname})")
    element_repo_paths = "\n".join(repo_paths_lines) if repo_paths_lines else "  (none found)"

    # Build prompt and save it so Claude Code can read it
    mapping_lines = ["Screenshot → Step mapping (taken AFTER step completed):"]
    for i, note in mapping.items():
        mapping_lines.append(f"  step_{i+1:02d}.png  →  Step {i+1}: {note}")

    prompt = PROMPT_TEMPLATE.format(
        context           = context,
        raw_steps         = raw_steps_text,
        tag_count         = len(existing_tags),
        existing_str      = format_tags_for_prompt(existing_tags),
        name              = name or "test",
        element_repo_paths= element_repo_paths,
    )
    prompt += "\n" + "\n".join(mapping_lines)
    prompt += "\n\nUse the Read tool to view these screenshots:\n"
    for i in range(len(frames)):
        prompt += f"  step_{i+1:02d}.png: {shots_dir / f'step_{i+1:02d}.png'}\n"
    prompt += (
        "\nAfter viewing all screenshots, produce all 4 sections as instructed.\n"
        "In Section 3 map each plain-English step to the correct Forge action + element tag.\n"
        "Where a tag is missing (❌) use the suggested snake_case value — "
        "it will be created by /qa-tags after this session."
    )

    prompt_path = out_dir / "prompt.txt"
    prompt_path.write_text(prompt, encoding="utf-8")

    # Save session metadata
    session = {
        "context": context,
        "name": name,
        "steps": all_steps,
        "frame_notes": {str(k): v for k, v in mapping.items()},
        "screenshots": [str(shots_dir / f"step_{i+1:02d}.png") for i in range(len(frames))],
        "existing_tag_count": len(existing_tags),
        "captured_at": datetime.now().isoformat(),
    }
    (out_dir / "session.json").write_text(
        json.dumps(session, indent=2), encoding="utf-8"
    )

    return prompt_path, shots_dir

# ─────────────────────────────────────────────────────────────────────────────
# Output writers
# ─────────────────────────────────────────────────────────────────────────────

def write_element_repo_entries(analysis: str) -> dict[str, int]:
    """
    Parse Section 5 from Claude's output and append new entries to the
    correct element YAML files in src/test/resources/elements/.
    Returns {filename: count_added}.
    """
    import re as _re

    idx = analysis.find("## 5. Element Repository Entries")
    if idx == -1:
        return {}

    section = analysis[idx:]
    start   = section.find("```yaml")
    end     = section.find("```", start + 7) if start != -1 else -1
    if start == -1 or end == -1:
        return {}

    block   = section[start + 7 : end].strip()
    elements_dir = FORGE_ROOT / "src" / "test" / "resources" / "elements"
    results: dict[str, int] = {}

    current_file: Optional[str] = None
    current_lines: list[str]    = []

    def _flush(fname: str, lines: list[str]) -> None:
        if not fname or not lines:
            return
        # Strip trailing blank lines
        content = "\n".join(lines).rstrip()
        if not content:
            return
        path = elements_dir / fname
        if not path.exists():
            print(f"  ⚠️  Element file not found: {fname} — skipping")
            return
        existing = path.read_text(encoding="utf-8")
        # Count how many top-level keys are genuinely new
        new_keys = []
        for line in lines:
            m = _re.match(r'^([a-z0-9_]+):$', line.strip())
            if m:
                key = m.group(1)
                if f"\n{key}:" not in existing and not existing.startswith(f"{key}:"):
                    new_keys.append(key)
        if not new_keys:
            return
        with path.open("a", encoding="utf-8") as f:
            f.write(f"\n# ─── Added by recorder-claude-steps ───\n")
            f.write(content + "\n")
        results[fname] = results.get(fname, 0) + len(new_keys)
        print(f"  📝  {fname}: +{len(new_keys)} element(s): {', '.join(new_keys)}")

    for line in block.splitlines():
        file_match = _re.match(r"#\s*FILE:\s*(\S+\.yaml)", line.strip())
        if file_match:
            _flush(current_file, current_lines)
            current_file  = file_match.group(1)
            current_lines = []
        else:
            current_lines.append(line)

    _flush(current_file, current_lines)
    return results


def extract_yaml_block(text: str, marker: str) -> Optional[str]:
    idx = text.find(marker)
    if idx == -1:
        return None
    sub   = text[idx:]
    start = sub.find("```yaml")
    if start == -1:
        return None
    end = sub.find("```", start + 7)
    if end == -1:
        return None
    return sub[start + 7 : end].strip()

def save_outputs(analysis: str, context: str, frame_count: int,
                 tag_count: int, out_dir: Path, name: str) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)

    # ── Element repository (write first so Forge YAML keys exist) ─────────────
    print(f"\n  🗃️   Writing new elements to repository…")
    added = write_element_repo_entries(analysis)
    if added:
        total = sum(added.values())
        print(f"      Added {total} element(s) across {len(added)} file(s)")
    else:
        print(f"      No new elements needed — all already in repository")

    # Markdown report
    md_path = out_dir / f"{name}.md"
    md_path.write_text(
        f"# Screen Inventory — {context}\n\n"
        f"_Generated by `scripts/recorder-claude-steps.py` · "
        f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n"
        f"**Frames analysed:** {frame_count} &nbsp;|&nbsp; "
        f"**Existing TestTags:** {tag_count}\n\n"
        "---\n\n"
        + analysis +
        "\n\n---\n\n"
        "_To inject missing tags: cd into popdroid and run `claude /qa-tags`._\n",
        encoding="utf-8",
    )
    print(f"  📋  Report       → {md_path.relative_to(FORGE_ROOT)}")

    # Forge YAML (Claude-converted from manual steps)
    forge_yaml = extract_yaml_block(analysis, "## 3. Forge Test YAML")
    if forge_yaml:
        first_line = forge_yaml.splitlines()[0].strip()
        m = re.match(r"#\s*FILE:\s*(.+)", first_line)
        forge_filename = m.group(1).strip() if m else f"ts_{name}.yaml"
        forge_content  = "\n".join(forge_yaml.splitlines()[1:]).strip() if m else forge_yaml
        forge_path     = out_dir / forge_filename
        forge_path.write_text(
            f"# Generated by scripts/recorder-claude-steps.py\n"
            f"# Context: {context}\n"
            f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n\n"
            + forge_content + "\n",
            encoding="utf-8",
        )
        print(f"  ✅  Forge YAML   → {forge_path.relative_to(FORGE_ROOT)}")
    else:
        print("  ⚠️  No Forge YAML block found in Claude response")

    # Missing tags YAML
    missing_yaml = extract_yaml_block(analysis, "## 4. Missing Tags YAML")
    if missing_yaml:
        missing_path = out_dir / f"{name}_missing.yaml"
        missing_path.write_text(
            f"# Missing test tags — {context}\n"
            f"# Report: {name}\n\n"
            + missing_yaml + "\n",
            encoding="utf-8",
        )
        print(f"  🏷️   Missing tags → {missing_path.relative_to(FORGE_ROOT)}")
        print(f"      Patch with:  cd ../popdroid && claude /qa-tags "
              f"../Forge/reports/{name}/{name}_missing.yaml")
    else:
        print("  ⚠️  No missing tags YAML block found in Claude response")
    print()

# ─────────────────────────────────────────────────────────────────────────────
# Recording loop
# ─────────────────────────────────────────────────────────────────────────────

def record(context: str, name: str,
           device: Optional[str], popdroid: Path) -> None:
    """
    Interleaved step entry + recording:
      1. Type the step description
      2. Perform it on the device
      3. Press ENTER to capture screenshot
      4. Repeat — blank step to finish
    """

    frames:      list[Image.Image] = []
    frame_notes: dict[int, str]    = {}
    all_steps:   list[str]         = []

    print(f"\n{'═' * 60}")
    print(f"  📋  {context}")
    print(f"{'═' * 60}")
    print("\n  For each step:")
    print("    1. Type the step description → ENTER")
    print("    2. Perform it on the device")
    print("    3. Press ENTER again to capture screenshot")
    print("\n    Leave step description blank → finish & send to Claude\n")

    step_num = 0
    while True:
        step_num += 1

        # ── 1. Get step description ───────────────────────────────────────
        try:
            step_text = input(f"  Step {step_num} description "
                              f"(blank = done): ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n  Stopped.")
            break

        if not step_text:
            print("  ✅  Steps complete.")
            break

        all_steps.append(step_text)
        print(f"  │  {step_text}")

        # ── 2. Perform on device, then ENTER ─────────────────────────────
        try:
            input(f"  ▶  Perform this step on the device, then press ENTER "
                  f"to capture… ")
        except (EOFError, KeyboardInterrupt):
            print("\n  Stopped.")
            break

        # ── 3. Capture screenshot ─────────────────────────────────────────
        img = adb_screencap(device)
        if img is None:
            print("  ⚠️  Screenshot failed — step recorded without screenshot.")
            # Still keep the step so Claude has context
        else:
            idx              = len(frames)
            frame_notes[idx] = step_text
            frames.append(resize_for_api(img))
            print(f"  📸  Screenshot {idx + 1} captured.\n")

    if not all_steps:
        print("\n  No steps recorded. Exiting.")
        return

    if not frames:
        print("\n  No screenshots captured. Nothing to analyse.")
        return

    print(f"\n  📝  {len(all_steps)} steps, {len(frames)} screenshots captured.")

    out_dir = FORGE_ROOT / "reports" / name
    out_dir.mkdir(parents=True, exist_ok=True)

    existing_tags = parse_existing_tags(popdroid)
    print(f"  📋  Loaded {len(existing_tags)} existing TestTags constants.")

    prompt_path, shots_dir = analyse_frames(
        frames, frame_notes, context, existing_tags,
        all_steps=all_steps, name=name, out_dir=out_dir,
    )

    print(f"\n  💾  Screenshots → {shots_dir.relative_to(FORGE_ROOT)}")
    print(f"  📝  Prompt     → {prompt_path.relative_to(FORGE_ROOT)}")

    print("\n" + "═" * 60)
    print("  ✅  Recording complete!")
    print()
    print("  Back in Claude Code, say:")
    print(f'  "analyze reports/{name}"')
    print()
    print("  Claude Code will read the screenshots and produce:")
    print(f"    reports/{name}/{name}.md")
    print(f"    reports/{name}/{name}_missing.yaml")
    print(f"    reports/{name}/ts_{name}.yaml")
    print()
    print("  Then run:")
    print(f"    cd ../popdroid && claude /qa-tags "
          f"../Forge/reports/{name}/{name}_missing.yaml")
    print("═" * 60)

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    if subprocess.run(["which", "claude"], capture_output=True).returncode != 0:
        print("❌  `claude` CLI not found.", file=sys.stderr); sys.exit(1)

    parser = argparse.ArgumentParser(
        prog="recorder-claude-steps",
        description="Step-guided recording with manually written steps → QA tag inventory.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--name", required=True,
                        help='Report name e.g. "shop_checkout" → reports/shop_checkout/')
    parser.add_argument("--context",
                        help='Flow description e.g. "Shop checkout flow" '
                             '(default: derived from --name)')
    parser.add_argument("--device",
                        help="ADB device serial (default: first connected)")
    parser.add_argument("--popdroid", type=Path, default=DEFAULT_POPDROID,
                        help=f"Path to popdroid repo (default: {DEFAULT_POPDROID})")

    args = parser.parse_args()
    context = args.context or args.name.replace("_", " ").title()

    # ADB check
    r = subprocess.run(["adb", "devices"], capture_output=True, text=True)
    connected = [l.split()[0] for l in r.stdout.splitlines()
                 if l.strip() and "\tdevice" in l]
    if not connected:
        print("❌  No ADB device connected.", file=sys.stderr); sys.exit(1)
    if not args.device:
        args.device = connected[0]
        if len(connected) > 1:
            print(f"  ℹ️  Multiple devices — using {args.device}")
            print(f"     Use --device <serial> to pick a different one.")

    print(f"\n📱  Setting up device…")
    ensure_app_launched(args.device)

    record(context, args.name, args.device, args.popdroid)

    subprocess.run(["adb"] + (["-s", args.device] if args.device else []) +
                   ["shell", "svc", "power", "stayon", "false"], capture_output=True)


if __name__ == "__main__":
    main()
