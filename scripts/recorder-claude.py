#!/usr/bin/env python3
"""recorder-claude.py — Capture app screens → full QA tag inventory + Maestro assertions via Claude.

Two modes
─────────
  live   Capture ADB screenshots while you interact with the device.
         After each unique screen is captured you can:
           ENTER        → keep frame, no note
           s + ENTER    → skip this frame
           any text     → keep frame + attach as context note for Claude

  video  Extract key frames from a screen recording file (needs ffmpeg).

Usage
─────
  # Live capture — named report:
  python3 scripts/recorder-claude.py live \\
      --context "Shop checkout flow" --name shop_checkout

  # Specific device:
  python3 scripts/recorder-claude.py live \\
      --context "UPI send money" --name upi_flow --device 10BDCM0YJZ00043

  # From a screen recording:
  python3 scripts/recorder-claude.py video path/to/recording.mp4 \\
      --context "Checkout flow" --name checkout_v2

  # Non-interactive (no per-frame prompts):
  python3 scripts/recorder-claude.py live \\
      --context "Login flow" --name login --auto

Output  (all saved to reports/<name>/)
──────
  reports/<name>/<name>.md           ← full element inventory table + coverage gaps
  reports/<name>/<name>_asserts.yaml ← Maestro assertVisible flow for every element
  reports/<name>/<name>_missing.yaml ← TagPatcher-compatible missing tags list

Setup
─────
  pip install Pillow
  brew install ffmpeg   # only for video mode
"""

import argparse
import io
import os
import re
import signal
import subprocess
import sys
import tempfile
import threading
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

# ── Dependency check ──────────────────────────────────────────────────────────
try:
    from PIL import Image
except ImportError:
    print("❌  Pillow not installed. Run: pip install Pillow", file=sys.stderr)
    sys.exit(1)

# ── Paths ─────────────────────────────────────────────────────────────────────
FORGE_ROOT       = Path(__file__).resolve().parents[1]
DEFAULT_POPDROID = FORGE_ROOT.parent / "popdroid"
TEST_TAGS_PATH   = "core/src/main/java/com/popclub/core/TestTags.kt"
APP_PACKAGE      = "com.popclub.android"
APP_ACTIVITY     = "com.popclub.android.LauncherFresh"
APK_DIR          = FORGE_ROOT / "src" / "main" / "resources"

# ── Tuning ────────────────────────────────────────────────────────────────────
CHANGE_THRESHOLD = 10    # perceptual-hash hamming distance to detect screen change
FRAME_WIDTH      = 540   # resize before Claude (token efficiency)
MAX_FRAMES       = 0     # max unique screens per session (0 = unlimited)

# ── Image utils ───────────────────────────────────────────────────────────────

def avg_hash(img: Image.Image, size: int = 8) -> int:
    small  = img.convert("L").resize((size, size), Image.LANCZOS)
    pixels = list(small.tobytes())
    avg    = sum(pixels) / len(pixels)
    bits   = "".join("1" if p >= avg else "0" for p in pixels)
    return int(bits, 2)

def hamming(a: int, b: int) -> int:
    return bin(a ^ b).count("1")

def resize_for_api(img: Image.Image, width: int = FRAME_WIDTH) -> Image.Image:
    w, h = img.size
    return img if w <= width else img.resize((width, int(h * width / w)), Image.LANCZOS)

# ── TestTags parser ───────────────────────────────────────────────────────────

def parse_existing_tags(popdroid: Path) -> set[str]:
    path = popdroid / TEST_TAGS_PATH
    if not path.exists():
        print(f"  ⚠️  TestTags.kt not found at {path}")
        return set()
    text   = path.read_text(encoding="utf-8")
    consts = set(re.findall(r'const val\s+(\w+)\s*=\s*"([^"]+)"', text))
    # Return both the const name AND the string value so Claude can match either
    names  = {c[0] for c in consts}
    values = {c[1] for c in consts}
    fns    = set(re.findall(r"fun\s+(\w+)\s*\(index:", text))
    return names | values | fns

def format_tags_for_prompt(tags: set[str], limit: int = 100) -> str:
    sample = sorted(tags)[:limit]
    suffix = f" … +{len(tags)-limit} more" if len(tags) > limit else ""
    return ", ".join(sample) + suffix

# ── ADB helpers ───────────────────────────────────────────────────────────────

def adb(device: Optional[str], *args) -> subprocess.CompletedProcess:
    cmd = ["adb"] + (["-s", device] if device else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=15)

def adb_screencap(device: Optional[str]) -> Optional[Image.Image]:
    cmd = ["adb"] + (["-s", device] if device else []) + ["exec-out", "screencap", "-p"]
    try:
        r = subprocess.run(cmd, capture_output=True, timeout=10)
        return Image.open(io.BytesIO(r.stdout)) if r.returncode == 0 and r.stdout else None
    except Exception:
        return None

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

# ── Video frame extraction ────────────────────────────────────────────────────

def extract_video_frames(video_path: Path) -> list[Image.Image]:
    tmp = FORGE_ROOT / "reports" / "_frames_tmp"
    tmp.mkdir(parents=True, exist_ok=True)
    for f in tmp.glob("frame_*.png"): f.unlink()
    subprocess.run(["ffmpeg", "-i", str(video_path),
                    "-vf", f"select=gt(scene\\,0.3),scale={FRAME_WIDTH}:-1",
                    "-vsync", "vfr", str(tmp / "frame_%03d.png"),
                    "-y", "-loglevel", "error"], capture_output=True)
    found = sorted(tmp.glob("frame_*.png"))
    if not found:
        subprocess.run(["ffmpeg", "-i", str(video_path),
                        "-vf", f"fps=1,scale={FRAME_WIDTH}:-1",
                        "-frames:v", str(MAX_FRAMES), str(tmp / "frame_%03d.png"),
                        "-y", "-loglevel", "error"], capture_output=True)
        found = sorted(tmp.glob("frame_*.png"))
    imgs = [Image.open(f).copy() for f in found[:MAX_FRAMES]]
    for f in found: f.unlink()
    try: tmp.rmdir()
    except: pass
    return imgs

# ── Claude prompt ─────────────────────────────────────────────────────────────

PROMPT_TEMPLATE = """\
You are a QA automation engineer analysing Android app screenshots for POP Club app.

Flow context: {context}

Existing TestTags constants ({tag_count} total — do NOT re-suggest these):
{existing_str}

Per-frame context notes from the tester:
{frame_notes}

Your job: identify every *interactive* UI element visible across all screenshots
and suggest stable qaTestTag names.

━━━ NAMING RULES ━━━
• Fixed element  → SCREAMING_SNAKE_CASE  e.g. CART_PAY_BUTTON, LOGIN_CONTINUE_BUTTON
• Suffixes: _BUTTON  _INPUT  _ROW  _TAB  _CHIP  _CHECKBOX  _TOGGLE  _LABEL  _ICON
• Repeated items → camelCase fun: fun cartItemRow(index: Int)
• Prefix = screen/section name  e.g. HOME_  CART_  CHECKOUT_  PRODUCT_DETAIL_
• Skip purely decorative elements (non-tappable dividers, static illustrations)
• Skip elements whose tag already exists in TestTags (mark ✅ but don't re-add)

━━━ OUTPUT — produce ALL THREE sections ━━━

## 1. Interactive Element Tag Analysis — {context}

| # | Screen | Element Description | Suggested Tag | Already in TestTags |
|---|--------|---------------------|---------------|---------------------|
(one row per interactive element)

## 2. Coverage Gaps
List any screens where coverage is incomplete, elements that might need tags
but were unclear, and any edge states (empty, error, loading) that need coverage.

## 3. Maestro Assertions YAML
Produce a Maestro flow file that asserts every element is visible.
Use `id:` for elements that have (or will have) a qaTestTag,
use `text:` for elements without a tag (these are ❌ rows).
Format EXACTLY like this — no extra text, start with the yaml block:

```yaml
appId: com.popclub.android
---
# ── Screen: Home ──
- assertVisible:
    id: "home_search_bar"
- assertVisible:
    text: "Everything UPI"   # TODO: add HOME_EVERYTHING_UPI_ROW tag
# ── Screen: Cart ──
- assertVisible:
    id: "cart_pay_button"
```

## 4. Missing Tags YAML
Produce a _missing.yaml compatible with TagPatcher for every ❌ element.
Format EXACTLY like this:

```yaml
missing:
  # ── home ──
  - element:    Everything UPI row
    class:      android.view.View
    bounds:     (unknown)
    suggestion: const val HOME_EVERYTHING_UPI_ROW = "home_everything_upi_row"

  # ── cart ──
  - element:    Pay button
    class:      android.widget.Button
    bounds:     (unknown)
    suggestion: const val CART_PAY_BUTTON = "cart_pay_button"
```
"""

# ── Claude call ───────────────────────────────────────────────────────────────

def analyse_frames(frames: list[Image.Image],
                   frame_notes: dict[int, str],
                   context: str,
                   existing_tags: set[str]) -> str:
    tmp_dir = Path(tempfile.mkdtemp(prefix="claude_frames_"))
    frame_files: list[Path] = []
    for i, img in enumerate(frames):
        fp = tmp_dir / f"screen_{i+1:02d}.png"
        img.save(fp, format="PNG", optimize=True)
        frame_files.append(fp)

    # Build frame notes block
    notes_lines = []
    for i in range(len(frames)):
        note = frame_notes.get(i, "")
        if note:
            notes_lines.append(f"  Screenshot {i+1}: {note}")
    notes_block = "\n".join(notes_lines) if notes_lines else "  (none provided)"

    prompt = PROMPT_TEMPLATE.format(
        context     = context,
        tag_count   = len(existing_tags),
        existing_str= format_tags_for_prompt(existing_tags),
        frame_notes = notes_block,
    )
    prompt += "\n\nUse the Read tool to view these screenshots:\n"
    for i, fp in enumerate(frame_files):
        prompt += f"  Screenshot {i+1}: {fp}\n"
    prompt += "\nAfter viewing all screenshots, produce all 4 sections as instructed."

    cmd = [
        "claude", "--print",
        "--allowedTools", "Read",
        "--add-dir", str(tmp_dir),
    ]

    print(f"\n🤖  Sending {len(frames)} screenshot(s) to Claude…\n")
    result = subprocess.run(cmd, input=prompt, capture_output=True, text=True,
                            timeout=300)

    for f in frame_files:
        try: f.unlink()
        except: pass
    try: tmp_dir.rmdir()
    except: pass

    if result.returncode != 0:
        err = (result.stderr or result.stdout or "unknown").strip().splitlines()[0]
        print(f"❌  Claude error: {err}", file=sys.stderr)
        sys.exit(1)

    return result.stdout.strip()

# ── Output writers ────────────────────────────────────────────────────────────

def extract_yaml_block(text: str, marker: str) -> Optional[str]:
    """Extract a ```yaml ... ``` block that appears after `marker`."""
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

    # ── 1. Markdown report ────────────────────────────────────────────────────
    md_path = out_dir / f"{name}.md"
    report = (
        f"# Screen Inventory — {context}\n\n"
        f"_Generated by `scripts/recorder-claude.py` · "
        f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n"
        f"**Frames analysed:** {frame_count} &nbsp;|&nbsp; "
        f"**Existing TestTags:** {tag_count}\n\n"
        "---\n\n"
        + analysis
        + "\n\n---\n\n"
        "_To inject missing tags: cd into popdroid and run `claude /qa-tags`._\n"
    )
    md_path.write_text(report, encoding="utf-8")
    print(f"  📋  Report       → {md_path.relative_to(FORGE_ROOT)}")

    # ── 2. Maestro assertions YAML ────────────────────────────────────────────
    asserts_yaml = extract_yaml_block(analysis, "## 3. Maestro Assertions YAML")
    if asserts_yaml:
        yaml_path = out_dir / f"{name}_asserts.yaml"
        yaml_path.write_text(
            f"# Maestro assertions — {context}\n"
            f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n"
            f"# Run with: maestro test {yaml_path.name}\n\n"
            + asserts_yaml + "\n",
            encoding="utf-8"
        )
        print(f"  ✅  Assertions   → {yaml_path.relative_to(FORGE_ROOT)}")
        print(f"      Run with:    maestro test {yaml_path}")
    else:
        print("  ⚠️  No Maestro YAML block found in Claude response")

    # ── 3. Missing tags YAML (TagPatcher-compatible) ──────────────────────────
    missing_yaml = extract_yaml_block(analysis, "## 4. Missing Tags YAML")
    if missing_yaml:
        missing_path = out_dir / f"{name}_missing.yaml"
        missing_path.write_text(
            f"# Missing test tags — add to Popdroid: TestTags.kt\n"
            f"# Report: {name}\n\n"
            + missing_yaml + "\n",
            encoding="utf-8"
        )
        print(f"  🏷️   Missing tags → {missing_path.relative_to(FORGE_ROOT)}")
        print(f"      Patch with:  java TagPatcher {missing_path}")
    else:
        print("  ⚠️  No missing tags YAML block found in Claude response")

    print()

# ── Live capture mode ─────────────────────────────────────────────────────────

def run_live(context: str, name: str, device: Optional[str],
             interval: float, popdroid: Path,
             auto: bool, max_frames: int) -> None:

    r = adb(device, "get-state")
    if r.returncode != 0 or "device" not in r.stdout:
        print("❌  No ADB device connected.", file=sys.stderr); sys.exit(1)

    print(f"\n📱  Setting up device…")
    ensure_app_launched(device)

    frames:      list[Image.Image] = []
    frame_notes: dict[int, str]    = {}
    hashes:      list[int]         = []
    interrupted = False

    def _stop(sig, _frame):
        nonlocal interrupted
        interrupted = True

    signal.signal(signal.SIGINT, _stop)

    print(f"\n📱  Live capture started  |  interval: {interval}s  |  auto: {auto}")
    if not auto:
        print("    After each new screen:")
        print("      ENTER        → keep, no note")
        print("      s + ENTER    → skip this frame")
        print("      type text    → keep + add context note")
    print("    Press Ctrl+C when done.\n")

    while not interrupted:
        img = adb_screencap(device)
        if img is None:
            time.sleep(2); continue

        h = avg_hash(img)
        if hashes and hamming(h, hashes[-1]) <= CHANGE_THRESHOLD:
            time.sleep(interval)
            continue

        # New unique screen detected
        idx = len(frames)
        print(f"  📸  Frame {idx+1:>2} captured")

        if not auto:
            try:
                user_input = input(
                    f"      [ENTER=keep | s=skip | type note]: "
                ).strip()
            except EOFError:
                user_input = ""

            if user_input.lower() == "s":
                print(f"      ↩  Skipped")
                hashes.append(h)
                time.sleep(interval)
                continue

            if user_input:
                frame_notes[idx] = user_input
                print(f"      📝  Note: {user_input}")

        frames.append(resize_for_api(img))
        hashes.append(h)

        if max_frames > 0 and len(frames) >= max_frames:
            print(f"\n⚠️  Reached {max_frames}-frame limit — stopping.")
            break

        time.sleep(interval)

    # Turn off stay-awake
    adb(device, "shell", "svc", "power", "stayon", "false")

    if not frames:
        print("No frames captured. Exiting.")
        return

    _run_analysis(frames, frame_notes, context, name, popdroid)

# ── Video mode ────────────────────────────────────────────────────────────────

def run_video(video_path: Path, context: str, name: str, popdroid: Path) -> None:
    if not video_path.exists():
        print(f"❌  File not found: {video_path}", file=sys.stderr); sys.exit(1)
    if subprocess.run(["ffmpeg", "-version"], capture_output=True).returncode != 0:
        print("❌  ffmpeg not found. Install: brew install ffmpeg", file=sys.stderr); sys.exit(1)
    print(f"\n🎬  Extracting frames from {video_path.name}…")
    raw    = extract_video_frames(video_path)
    frames = [resize_for_api(f) for f in raw]
    print(f"    Extracted {len(frames)} unique frames.")
    _run_analysis(frames, {}, context, name, popdroid)

# ── Shared analysis runner ────────────────────────────────────────────────────

def _run_analysis(frames: list[Image.Image], frame_notes: dict[int, str],
                  context: str, name: str, popdroid: Path) -> None:
    existing_tags = parse_existing_tags(popdroid)
    print(f"\n📋  Loaded {len(existing_tags)} existing TestTags constants.")

    analysis = analyse_frames(frames, frame_notes, context, existing_tags)

    print(analysis[:3000])  # preview first 3000 chars in terminal
    if len(analysis) > 3000:
        print(f"\n  … (truncated — see full report in file)")

    out_dir = FORGE_ROOT / "reports" / name
    print(f"\n💾  Saving outputs to reports/{name}/")
    save_outputs(analysis, context, len(frames), len(existing_tags), out_dir, name)

    print("═" * 54)
    print("  Next steps:")
    print(f"  1. Review   reports/{name}/{name}.md")
    print(f"  2. Patch    cd ../popdroid && claude /qa-tags")
    print(f"  3. Assert   maestro test reports/{name}/{name}_asserts.yaml")
    print("═" * 54)

# ── CLI ───────────────────────────────────────────────────────────────────────

def main() -> None:
    if subprocess.run(["which", "claude"], capture_output=True).returncode != 0:
        print("❌  `claude` CLI not found.", file=sys.stderr); sys.exit(1)

    parser = argparse.ArgumentParser(
        prog="recorder-claude",
        description="Capture app screens → QA tag inventory + Maestro assertions via Claude.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--popdroid", type=Path, default=DEFAULT_POPDROID,
                        help=f"Path to popdroid repo (default: {DEFAULT_POPDROID})")

    sub = parser.add_subparsers(dest="mode", required=True)

    # live
    live_p = sub.add_parser("live", help="Capture via ADB while you interact")
    live_p.add_argument("--context", required=True,
                        help='Flow description e.g. "Shop checkout flow"')
    live_p.add_argument("--name", required=True,
                        help='Report name e.g. "shop_checkout" → reports/shop_checkout/')
    live_p.add_argument("--device",
                        help="ADB device serial (default: first connected)")
    live_p.add_argument("--interval", type=float, default=0.8,
                        help="Capture interval seconds (default: 0.8)")
    live_p.add_argument("--auto", action="store_true",
                        help="Non-interactive — no per-frame prompts")
    live_p.add_argument("--max-frames", type=int, default=MAX_FRAMES,
                        help=f"Max frames before auto-stop (default: unlimited, set >0 to cap)")

    # video
    video_p = sub.add_parser("video", help="Extract frames from a screen recording")
    video_p.add_argument("file", type=Path, help="Screen recording (.mp4, .mov…)")
    video_p.add_argument("--context", required=True,
                         help='Flow description e.g. "Checkout flow"')
    video_p.add_argument("--name", required=True,
                         help='Report name e.g. "checkout_v2" → reports/checkout_v2/')

    args = parser.parse_args()

    if args.mode == "live":
        run_live(args.context, args.name, args.device,
                 args.interval, args.popdroid, args.auto, args.max_frames)
    else:
        run_video(args.file, args.context, args.name, args.popdroid)


if __name__ == "__main__":
    main()
