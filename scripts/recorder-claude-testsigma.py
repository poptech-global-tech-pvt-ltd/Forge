#!/usr/bin/env python3
"""recorder-claude-testsigma.py — Step-guided recording from a TestSigma test case.

Fetches test steps from a TestSigma test run, shows each step to the tester
one-by-one, captures a screenshot after each step, then sends everything to
Claude to produce the same 3-file output as recorder-claude.py.

Usage
─────
  # Record a specific test case from a run:
  python3 scripts/recorder-claude-testsigma.py \\
      --run-id <test-run-uuid> --case-id PO-1234 --name shop_add_to_cart

  # Record all cases from a run (one recording session per case):
  python3 scripts/recorder-claude-testsigma.py \\
      --run-id <test-run-uuid> --name shop_smoke

  # Specific ADB device:
  python3 scripts/recorder-claude-testsigma.py \\
      --run-id <uuid> --case-id PO-1234 --name shop_cart --device 10BDCM0YJZ00043

  # Non-interactive — auto-capture after each step (no per-step prompt):
  python3 scripts/recorder-claude-testsigma.py \\
      --run-id <uuid> --case-id PO-1234 --name shop_cart --auto

Output  (all saved to reports/<name>/)
──────
  reports/<name>/<name>.md                ← element inventory + coverage gaps
  reports/<name>/<name>_asserts.yaml      ← Maestro assertVisible flow
  reports/<name>/<name>_missing.yaml      ← TagPatcher-compatible missing tags

Token setup
───────────
  Add to src/test/resources/local.properties:
    TESTSIGMA_API_TOKEN=<your token>
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
    import requests
except ImportError:
    print("❌  requests not installed. Run: pip3 install requests", file=sys.stderr)
    sys.exit(1)

try:
    from PIL import Image
except ImportError:
    print("❌  Pillow not installed. Run: pip3 install Pillow", file=sys.stderr)
    sys.exit(1)

# ── Paths ─────────────────────────────────────────────────────────────────────
FORGE_ROOT       = Path(__file__).resolve().parents[1]
LOCAL_PROPS_PATH = FORGE_ROOT / "src" / "test" / "resources" / "local.properties"
DEFAULT_POPDROID = FORGE_ROOT.parent / "popdroid"
TEST_TAGS_PATH   = "core/src/main/java/com/popclub/core/TestTags.kt"
APK_DIR          = FORGE_ROOT / "src" / "main" / "resources"

# ── App constants ─────────────────────────────────────────────────────────────
APP_PACKAGE  = "com.popclub.android"
APP_ACTIVITY = "com.popclub.android.LauncherFresh"

# ── TestSigma config ──────────────────────────────────────────────────────────
TESTSIGMA_BASE_URL = "https://test-management.testsigma.com/api/v1"
DEFAULT_PROJECT_ID = "d8f4a221-bc6d-47d8-9448-0834f5d012ec"

# ── Image tuning ──────────────────────────────────────────────────────────────
FRAME_WIDTH = 540   # resize before Claude (token efficiency)

# ─────────────────────────────────────────────────────────────────────────────
# local.properties
# ─────────────────────────────────────────────────────────────────────────────

def load_local_props() -> dict:
    props = {}
    if LOCAL_PROPS_PATH.exists():
        for line in LOCAL_PROPS_PATH.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, _, v = line.partition("=")
                props[k.strip()] = v.strip()
    return props

def get_testsigma_token() -> str:
    token = os.environ.get("TESTSIGMA_API_TOKEN", "")
    if not token:
        token = load_local_props().get("TESTSIGMA_API_TOKEN", "")
    if not token:
        print("❌  TESTSIGMA_API_TOKEN not set.", file=sys.stderr)
        print(f"    Add to: {LOCAL_PROPS_PATH}", file=sys.stderr)
        sys.exit(1)
    return token

# ─────────────────────────────────────────────────────────────────────────────
# TestSigma API client
# ─────────────────────────────────────────────────────────────────────────────

class TestSigmaClient:
    def __init__(self, token: str):
        import urllib3
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update({
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
            "Content-Type": "application/json",
        })

    def get(self, path: str, params: dict = None, retries: int = 3) -> dict:
        url = f"{TESTSIGMA_BASE_URL}/{path.lstrip('/')}"
        for attempt in range(1, retries + 1):
            try:
                r = self.session.get(url, params=params, timeout=60)
                if not r.ok:
                    print(f"  ❌  HTTP {r.status_code} — {url}", file=sys.stderr)
                    print(f"      Response: {r.text[:400]}", file=sys.stderr)
                    r.raise_for_status()
                return r.json()
            except requests.exceptions.Timeout:
                if attempt < retries:
                    print(f"  ⏱️  Timeout on attempt {attempt}/{retries} — retrying…")
                    time.sleep(2 * attempt)
                else:
                    raise

    def get_run_test_cases(self, run_id: str,
                           project_id: str = DEFAULT_PROJECT_ID) -> list:
        """Fetch all test cases from a test run (paginated)."""
        cases, cursor, page, total = [], None, 0, None
        while True:
            page += 1
            params = {"size": 50}
            if cursor:
                params["next"] = cursor
            data = self.get(
                f"/projects/{project_id}/test_runs/{run_id}/test_cases",
                params=params,
            )
            run_cases = (
                (data.get("data") or {}).get("test_run_cases")
                or data.get("content") or []
            )
            for rc in run_cases:
                tc = rc.get("test_case") or rc
                tc["_run_status"] = rc.get("status", "")
                cases.append(tc)

            page_info   = data.get("page_info") or {}
            next_cursor = page_info.get("next", "")
            if total is None:
                total = page_info.get("total_count", 0)

            print(f"    Page {page}: +{len(run_cases)} cases  "
                  f"(total so far: {len(cases)}/{total})")

            if not run_cases or not next_cursor or (total and len(cases) >= total):
                break
            cursor = next_cursor
            time.sleep(0.2)
        return cases

    def get_test_case_steps(self, project_id: str, case_id_uuid: str) -> list:
        """Fetch detailed test steps for a single test case UUID."""
        data = self.get(f"/projects/{project_id}/test_cases/{case_id_uuid}")
        steps = (data.get("data") or {}).get("test_steps") or []
        return steps

    def find_case_by_human_id(self, project_id: str, human_id: str) -> Optional[dict]:
        """
        Look up a test case directly by human ID (e.g. PO-1234) without a run.
        Returns the test case dict or None if not found.
        """
        data = self.get(
            f"/projects/{project_id}/test_cases",
            params={"search": human_id, "size": 20},
        )
        cases = (data.get("data") or {}).get("test_cases") or data.get("content") or []
        for tc in cases:
            if (tc.get("human_id") or "").upper() == human_id.upper():
                return tc
        return None

    def list_recent_runs(self, project_id: str = DEFAULT_PROJECT_ID,
                         size: int = 10) -> list:
        """List recent test runs for discovery."""
        data = self.get(f"/projects/{project_id}/test_runs", params={"size": size})
        return (
            (data.get("data") or {}).get("test_runs")
            or data.get("content") or []
        )

# ─────────────────────────────────────────────────────────────────────────────
# HTML → plain text
# ─────────────────────────────────────────────────────────────────────────────

def strip_html(html: str) -> str:
    if not html:
        return ""
    text = re.sub(r"<br\s*/?>", "\n", html, flags=re.IGNORECASE)
    text = re.sub(r"<p[^>]*>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</p>", "", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    text = (text.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", '"').replace("&#39;", "'"))
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    return "\n".join(lines)

def extract_steps(tc: dict, client: TestSigmaClient, project_id: str) -> list[str]:
    """
    Extract ordered step texts from a test case.
    Tries: data.test_steps[].description → data.test_case.steps (HTML) → empty.
    Falls back to fetching detailed steps if needed.
    """
    # Try inline steps list first (already fetched in run_cases)
    raw_steps = tc.get("test_steps") or []
    if raw_steps:
        return [strip_html(s.get("description", "")) for s in raw_steps if s.get("description")]

    # Try HTML steps field
    html_steps = strip_html(tc.get("steps") or "")
    if html_steps:
        return [l for l in html_steps.splitlines() if l.strip()]

    # Fetch details using case UUID
    case_uuid = tc.get("id")
    if case_uuid:
        detailed = client.get_test_case_steps(project_id, case_uuid)
        if detailed:
            return [strip_html(s.get("description", "")) for s in detailed if s.get("description")]

    return []

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
# Claude prompt + analysis  (same format as recorder-claude.py)
# ─────────────────────────────────────────────────────────────────────────────

PROMPT_TEMPLATE = """\
You are a QA automation engineer for the POP Club Android app (Jetpack Compose).

You have two inputs:
  1. Raw test steps from TestSigma (plain English, what the tester did)
  2. Device screenshots — one per step, showing the app state AFTER that step

Your job:
  A. Convert the raw steps into an automatable Forge YAML test file
  B. Identify every interactive element visible and suggest qaTestTag names
  C. List what is missing

━━━ FLOW CONTEXT ━━━
{context}

━━━ RAW TEST STEPS (from TestSigma — plain English) ━━━
{raw_steps}

━━━ EXISTING TESTTAGS ({tag_count} constants — mark ✅, do NOT re-suggest) ━━━
{existing_str}

━━━ FORGE YAML FORMAT ━━━
```yaml
testName: <Descriptive name>
platform: android
noReset: false
testCaseIds:
  - <human_id>
features:
  - <feature>
tags:
  - smoke
retry: 1
steps:
  - action: launchApp
  - action: tapIfPresent
    element: skip_tour
  - action: tap
    element: shop_tab              # ← use snake_case tag VALUE (not const name)
  - action: waitFor
    element: shop_search_bar
  - action: scroll
    direction: down
  - action: tap
    element: shop_product_item_0   # ← indexed helper: shopProductItem(0)
  - action: verifyElement
    element: product_detail_add_to_cart_button
```
Available actions: launchApp, tapIfPresent, tap, enterText, waitFor,
verifyElement, scroll, swipe, captureToken, assertText, back

━━━ NAMING RULES FOR NEW TAGS ━━━
• Fixed element  → SCREAMING_SNAKE_CASE const  e.g. CART_PAY_BUTTON
• Suffixes       → _BUTTON  _INPUT  _ROW  _TAB  _CHIP  _CHECKBOX  _ICON
• Repeated items → fun camelCase(index: Int)  e.g. fun shopProductItem(index: Int)
• Prefix         → screen/section: HOME_  CART_  SHOP_  PRODUCT_DETAIL_  LOGIN_
• Global chrome  → COMMON_  e.g. COMMON_BACK_BUTTON, COMMON_HOME_TAB
• In the Forge YAML always use the snake_case VALUE, not the const name

━━━ OUTPUT — produce ALL FOUR sections ━━━

## 1. Interactive Element Tag Analysis — {context}

| # | Screen | Element Description | Suggested Tag | Already in TestTags |
|---|--------|---------------------|---------------|---------------------|
| 1 | Login | Mobile number text input | `LOGIN_MOBILE_INPUT` | ❌ |
| 2 | Home | Profile avatar button (top-left) | `COMMON_PROFILE_BUTTON` | ❌ |
| 3 | Cart | "Pay ₹X" primary checkout button | `CART_PAY_BUTTON` | ❌ |
| 4 | Product Detail | "Enter Pincode" text input | `ADDRESS_SELECTION_PINCODE_INPUT` | ✅ |
(one row per interactive element across all screenshots — be exhaustive)

## 2. Coverage Gaps

Group by screen. Note:
- Steps that could not be mapped to a visible element (untaggable or ambiguous)
- Edge states (loading, empty, error) not captured
- Elements that need tags but were unclear from screenshots

## 3. Forge Test YAML

Convert the raw TestSigma steps into a runnable Forge YAML.
Use element tag VALUES you identified in Section 1.
Where a tag is missing (❌), still use the suggested snake_case value — it will
exist after `/qa-tags` runs.

```yaml
# FILE: ts_{name}.yaml
testName: {context}
platform: android
...
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

Only ❌ elements from Section 1. One entry per missing tag.
"""

def format_raw_steps(steps: list[str]) -> str:
    """Format raw TestSigma steps as a numbered plain-text list for the prompt."""
    return "\n".join(f"  {i}. {s}" for i, s in enumerate(steps, 1))


def analyse_frames(frames: list[Image.Image],
                   frame_notes: dict[int, str],
                   context: str,
                   existing_tags: set[str],
                   all_steps: list[str] | None = None,
                   human_id: str = "",
                   name: str = "") -> str:
    tmp_dir = Path(tempfile.mkdtemp(prefix="claude_ts_frames_"))
    frame_files: list[Path] = []
    for i, img in enumerate(frames):
        fp = tmp_dir / f"step_{i+1:02d}.png"
        img.save(fp, format="PNG", optimize=True)
        frame_files.append(fp)

    # Raw steps as plain English numbered list — Claude converts to YAML
    raw_steps_text = format_raw_steps(
        all_steps if all_steps
        else [frame_notes.get(i, f"Step {i+1}") for i in range(len(frames))]
    )

    # Screenshot → step mapping so Claude knows which screen matches which step
    mapping_lines = ["\nScreenshot → Step mapping (screenshot taken AFTER step completed):"]
    for i in range(len(frames)):
        note = frame_notes.get(i, f"Step {i+1}")
        mapping_lines.append(f"  step_{i+1:02d}.png  →  Step {i+1}: {note}")

    prompt = PROMPT_TEMPLATE.format(
        context     = context,
        raw_steps   = raw_steps_text,
        tag_count   = len(existing_tags),
        existing_str= format_tags_for_prompt(existing_tags),
        name        = name or "test",
    )
    prompt += "\n".join(mapping_lines)
    prompt += "\n\nUse the Read tool to view these screenshots:\n"
    for i, fp in enumerate(frame_files):
        prompt += f"  step_{i+1:02d}.png: {fp}\n"
    prompt += (
        "\nAfter viewing all screenshots, produce all 4 sections as instructed.\n"
        "In Section 3 (Forge YAML), map each plain-English step to the correct "
        "Forge action + element tag you identified from the screenshot.\n"
        "Where an element has no tag yet (❌), use the suggested snake_case value — "
        "it will be created by /qa-tags after this session."
    )

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

# ─────────────────────────────────────────────────────────────────────────────
# Output writers  (same format as recorder-claude.py)
# ─────────────────────────────────────────────────────────────────────────────

ELEMENT_FILES = [
    "common", "login", "home", "shop", "cart",
    "upi", "profile", "rewards", "billpay", "credit_card",
]


def write_element_repo_entries(analysis: str) -> dict:
    """Parse Section 5 and append new entries to the element YAML files."""
    import re as _re

    idx = analysis.find("## 5. Element Repository Entries")
    if idx == -1:
        return {}

    section = analysis[idx:]
    start   = section.find("```yaml")
    end     = section.find("```", start + 7) if start != -1 else -1
    if start == -1 or end == -1:
        return {}

    block        = section[start + 7 : end].strip()
    elements_dir = FORGE_ROOT / "src" / "test" / "resources" / "elements"
    results: dict = {}

    current_file:  Optional[str]  = None
    current_lines: list[str]      = []

    def _flush(fname, lines):
        if not fname or not lines:
            return
        content = "\n".join(lines).rstrip()
        if not content:
            return
        path = elements_dir / fname
        if not path.exists():
            print(f"  ⚠️  Element file not found: {fname} — skipping")
            return
        existing  = path.read_text(encoding="utf-8")
        new_keys  = []
        for line in lines:
            m = _re.match(r'^([a-z0-9_]+):$', line.strip())
            if m:
                key = m.group(1)
                if f"\n{key}:" not in existing and not existing.startswith(f"{key}:"):
                    new_keys.append(key)
        if not new_keys:
            return
        with path.open("a", encoding="utf-8") as f:
            f.write(f"\n# ─── Added by recorder-claude-testsigma ───\n")
            f.write(content + "\n")
        results[fname] = results.get(fname, 0) + len(new_keys)
        print(f"  📝  {fname}: +{len(new_keys)} element(s): {', '.join(new_keys)}")

    for line in block.splitlines():
        m = _re.match(r"#\s*FILE:\s*(\S+\.yaml)", line.strip())
        if m:
            _flush(current_file, current_lines)
            current_file  = m.group(1)
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

    # Markdown report  (matches recorder-claude.py header format exactly)
    md_path = out_dir / f"{name}.md"
    report = (
        f"# Screen Inventory — {context}\n\n"
        f"_Generated by `scripts/recorder-claude-testsigma.py` · "
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

    # Forge test YAML (Claude-converted from raw steps)
    forge_yaml = extract_yaml_block(analysis, "## 3. Forge Test YAML")
    if forge_yaml:
        # Strip the leading # FILE: ... comment line if present to get clean filename
        first_line = forge_yaml.splitlines()[0].strip()
        m = re.match(r"#\s*FILE:\s*(.+)", first_line)
        forge_filename = m.group(1).strip() if m else f"ts_{name}.yaml"
        forge_content  = "\n".join(forge_yaml.splitlines()[1:]).strip() if m else forge_yaml

        forge_path = out_dir / forge_filename
        forge_path.write_text(
            f"# Generated by scripts/recorder-claude-testsigma.py\n"
            f"# Source: TestSigma — {context}\n"
            f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n"
            f"# Run with: mvn test -DtestFile={forge_filename}\n\n"
            + forge_content + "\n",
            encoding="utf-8",
        )
        print(f"  ✅  Forge YAML   → {forge_path.relative_to(FORGE_ROOT)}")
        print(f"      Run with:    mvn test -DtestFile={forge_filename}")
    else:
        print("  ⚠️  No Forge YAML block found in Claude response")

    # Missing tags YAML (TagPatcher + /qa-tags compatible)
    missing_yaml = extract_yaml_block(analysis, "## 4. Missing Tags YAML")
    if missing_yaml:
        missing_path = out_dir / f"{name}_missing.yaml"
        missing_path.write_text(
            f"# Missing test tags — add to Popdroid: TestTags.kt\n"
            f"# Report: {name}\n\n"
            + missing_yaml + "\n",
            encoding="utf-8",
        )
        print(f"  🏷️   Missing tags → {missing_path.relative_to(FORGE_ROOT)}")
        print(f"      Patch with:  cd ../popdroid && claude /qa-tags "
              f"reports/{name}/{name}_missing.yaml")
    else:
        print("  ⚠️  No missing tags YAML block found in Claude response")
    print()

# ─────────────────────────────────────────────────────────────────────────────
# Step-guided recording for one test case
# ─────────────────────────────────────────────────────────────────────────────

def record_test_case(tc: dict, steps: list[str], device: Optional[str],
                     auto: bool, name: str, popdroid: Path) -> None:
    """Show each step, wait for user to perform it, capture screenshot."""

    title     = tc.get("title", "Untitled")
    human_id  = tc.get("human_id", "")
    context   = f"{human_id} — {title}" if human_id else title
    total     = len(steps)

    print(f"\n{'═' * 60}")
    print(f"  📋  {context}")
    print(f"  Steps: {total}")
    print(f"{'═' * 60}")

    if not steps:
        print("  ⚠️  No steps found for this test case. Cannot record.")
        return

    print("\n  Controls:")
    print("    ENTER        → done, capture screenshot")
    print("    s + ENTER    → skip this step (no screenshot)")
    print("    any text     → done + add extra note to context")
    print("    q + ENTER    → quit recording early\n")

    frames:      list[Image.Image] = []
    frame_notes: dict[int, str]    = {}

    for i, step_text in enumerate(steps):
        step_num = i + 1
        print(f"\n  ┌─ Step {step_num}/{total} {'─' * 40}")
        print(f"  │  {step_text}")
        print(f"  └{'─' * 47}")

        if auto:
            # Non-interactive: wait 2s for user to perform, then capture
            time.sleep(2)
        else:
            try:
                user_input = input("  ▶  [ENTER=done | s=skip | q=quit | note]: ").strip()
            except (EOFError, KeyboardInterrupt):
                print("\n  Interrupted.")
                break

            if user_input.lower() == "q":
                print("  ⏹  Recording stopped early.")
                break

            if user_input.lower() == "s":
                print("  ↩  Step skipped.")
                continue

            extra_note = user_input  # user typed a note

        # Capture screenshot
        img = adb_screencap(device)
        if img is None:
            print("  ⚠️  Screenshot failed — skipping frame.")
            continue

        frame_idx              = len(frames)
        note                   = step_text
        if not auto and extra_note:
            note = f"{step_text} | {extra_note}"
        frame_notes[frame_idx] = note
        frames.append(resize_for_api(img))
        print(f"  📸  Screenshot {frame_idx + 1} captured.")

    if not frames:
        print("\n  No screenshots captured. Nothing to analyse.")
        return

    # Load existing tags + run Claude
    existing_tags = parse_existing_tags(popdroid)
    print(f"\n  📋  Loaded {len(existing_tags)} existing TestTags constants.")

    analysis = analyse_frames(frames, frame_notes, context, existing_tags,
                              all_steps=steps,
                              human_id=tc.get("human_id", ""),
                              name=name)

    print(analysis[:3000])
    if len(analysis) > 3000:
        print("\n  … (truncated — see full report in file)")

    out_dir = FORGE_ROOT / "reports" / name
    print(f"\n  💾  Saving outputs to reports/{name}/")
    save_outputs(analysis, context, len(frames), len(existing_tags), out_dir, name)

    print("═" * 60)
    print("  Next steps:")
    print(f"  1. Review   reports/{name}/{name}.md")
    print(f"  2. Patch    cd ../popdroid && claude /qa-tags "
          f"../Forge/reports/{name}/{name}_missing.yaml")
    print(f"  3. Assert   maestro test reports/{name}/{name}_asserts.yaml")
    print("═" * 60)

# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    if subprocess.run(["which", "claude"], capture_output=True).returncode != 0:
        print("❌  `claude` CLI not found.", file=sys.stderr); sys.exit(1)

    parser = argparse.ArgumentParser(
        prog="recorder-claude-testsigma",
        description="Step-guided ADB recording from a TestSigma test case → QA tag inventory.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--run-id",
                        help="TestSigma test run UUID (optional if --case-id is given)")
    parser.add_argument("--name", required=True,
                        help='Report name e.g. "shop_cart" → reports/shop_cart/')
    parser.add_argument("--case-id",
                        help="Record only one case by human ID e.g. PO-1234 "
                             "(default: record all cases in the run, one by one)")
    parser.add_argument("--device",
                        help="ADB device serial (default: first connected)")
    parser.add_argument("--project-id", default=DEFAULT_PROJECT_ID,
                        help=f"TestSigma project UUID (default: {DEFAULT_PROJECT_ID})")
    parser.add_argument("--popdroid", type=Path, default=DEFAULT_POPDROID,
                        help=f"Path to popdroid repo (default: {DEFAULT_POPDROID})")
    parser.add_argument("--auto", action="store_true",
                        help="Non-interactive — auto-capture 2s after showing each step")
    parser.add_argument("--dry-run", action="store_true",
                        help="Fetch & list steps without recording")

    args = parser.parse_args()

    # Validate: need at least one of --run-id or --case-id
    if not args.run_id and not args.case_id:
        parser.error("Provide at least one of --run-id or --case-id.\n"
                     "  --case-id PO-1234              → record a single case directly\n"
                     "  --run-id <uuid>                → record all cases in a run\n"
                     "  --run-id <uuid> --case-id PO-1234 → one case from a run")

    # Check ADB
    # ADB check only needed for actual recording (not dry-run)
    if not args.dry_run:
        # Use `adb devices` to detect connected devices (works with multiple)
        r = subprocess.run(["adb", "devices"], capture_output=True, text=True)
        connected = [l.split()[0] for l in r.stdout.splitlines()
                     if l.strip() and "\tdevice" in l]
        if not connected:
            print("❌  No ADB device connected.", file=sys.stderr); sys.exit(1)
        # Auto-pick device if not specified
        if not args.device:
            args.device = connected[0]
            if len(connected) > 1:
                print(f"  ℹ️  Multiple devices found — using {args.device}")
                print(f"     Others: {', '.join(connected[1:])}")
                print(f"     Use --device <serial> to pick a different one.")

    token  = get_testsigma_token()
    client = TestSigmaClient(token)

    # ── Resolve cases ─────────────────────────────────────────────────────────
    if args.run_id:
        # Fetch from run, optionally filter to one case
        print(f"\n🔍  Fetching test cases from run: {args.run_id}")
        all_cases = client.get_run_test_cases(args.run_id, args.project_id)
        print(f"✅  Fetched {len(all_cases)} cases")

        if args.case_id:
            all_cases = [c for c in all_cases if c.get("human_id") == args.case_id]
            if not all_cases:
                print(f"❌  Case {args.case_id} not found in run {args.run_id}",
                      file=sys.stderr); sys.exit(1)
    else:
        # No run-id — look up the case directly by human ID
        print(f"\n🔍  Looking up {args.case_id} directly…")
        tc = client.find_case_by_human_id(args.project_id, args.case_id)
        if not tc:
            print(f"❌  Case not found: {args.case_id}", file=sys.stderr); sys.exit(1)
        print(f"✅  Found: {tc.get('title','Untitled')}")
        all_cases = [tc]

    if not all_cases:
        print("⚠️  No cases to process."); sys.exit(0)

    # Fetch steps for each case
    cases_with_steps = []
    for tc in all_cases:
        steps = extract_steps(tc, client, args.project_id)
        cases_with_steps.append((tc, steps))

    # Dry run: just list steps
    if args.dry_run:
        print(f"\n══ DRY RUN — {len(cases_with_steps)} case(s) ══\n")
        for tc, steps in cases_with_steps:
            print(f"  {tc.get('human_id','?')} — {tc.get('title','Untitled')}")
            if steps:
                for j, s in enumerate(steps, 1):
                    print(f"    {j}. {s}")
            else:
                print("    (no steps found)")
            print()
        print("Run without --dry-run to start recording.")
        return

    # Launch app once
    print(f"\n📱  Setting up device…")
    ensure_app_launched(args.device)

    # Record each case
    for idx, (tc, steps) in enumerate(cases_with_steps, 1):
        if len(cases_with_steps) > 1:
            name = f"{args.name}_{tc.get('human_id', str(idx)).lower().replace('-', '_')}"
            print(f"\n\n[{idx}/{len(cases_with_steps)}] Recording: {tc.get('human_id')} → {name}")
        else:
            name = args.name

        record_test_case(tc, steps, args.device, args.auto, name, args.popdroid)

        if idx < len(cases_with_steps):
            try:
                cont = input("\n  Continue to next case? [ENTER=yes | q=quit]: ").strip()
                if cont.lower() == "q":
                    break
            except (EOFError, KeyboardInterrupt):
                break

    # Turn off stay-awake
    subprocess.run(["adb"] + (["-s", args.device] if args.device else []) +
                   ["shell", "svc", "power", "stayon", "false"],
                   capture_output=True)


if __name__ == "__main__":
    main()
