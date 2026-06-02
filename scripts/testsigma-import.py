#!/usr/bin/env python3
"""testsigma-import.py — Fetch test cases from a TestSigma test run → runnable Forge YAMLs via Claude.

Fetches all test cases from a TestSigma test run, converts them to runnable
Forge YAML test files using Claude, and produces the same 3-file output
format as recorder-claude.py.

Usage
─────
  # Import from a specific test run:
  python3.11 scripts/testsigma-import.py \\
      --run-id <test-run-uuid> --name shop_import

  # Dry-run: fetch & list cases, no Claude call:
  python3.11 scripts/testsigma-import.py \\
      --run-id <test-run-uuid> --name shop_import --dry-run

  # Convert one test case (by TestSigma human ID e.g. PO-1234):
  python3.11 scripts/testsigma-import.py \\
      --run-id <test-run-uuid> --name shop_import --case-id PO-1234

  # Limit batch size (default 10 cases per Claude call):
  python3.11 scripts/testsigma-import.py \\
      --run-id <test-run-uuid> --name shop_import --batch 5

  # Cap total cases processed (useful for testing):
  python3.11 scripts/testsigma-import.py \\
      --run-id <test-run-uuid> --name shop_import --max-cases 20

Output  (all saved to reports/<name>/)
──────
  reports/<name>/<name>.md                ← inventory table + coverage gaps
  reports/<name>/ts_<id>_<title>.yaml     ← one runnable Forge YAML per test case
  reports/<name>/<name>_missing.yaml      ← TagPatcher-compatible missing tags list

Token setup
───────────
  Add to src/test/resources/local.properties:
    TESTSIGMA_API_TOKEN=<token from TestSigmaClient.java>
"""

import argparse
import os
import re
import subprocess
import sys
import time
from datetime import datetime
from pathlib import Path
from typing import Optional

# ── Dependency check ──────────────────────────────────────────────────────────
try:
    import requests
except ImportError:
    print("❌  requests not installed. Run: pip3.11 install requests", file=sys.stderr)
    sys.exit(1)

try:
    import yaml
except ImportError:
    print("❌  pyyaml not installed. Run: pip3.11 install pyyaml", file=sys.stderr)
    sys.exit(1)

# ── Paths ─────────────────────────────────────────────────────────────────────
FORGE_ROOT       = Path(__file__).resolve().parents[1]
ELEMENTS_DIR     = FORGE_ROOT / "src" / "test" / "resources" / "elements"
LOCAL_PROPS_PATH = FORGE_ROOT / "src" / "test" / "resources" / "local.properties"
DEFAULT_POPDROID = FORGE_ROOT.parent / "popdroid"
TEST_TAGS_PATH   = "core/src/main/java/com/popclub/core/TestTags.kt"

# ── TestSigma config ──────────────────────────────────────────────────────────
TESTSIGMA_BASE_URL = "https://test-management.testsigma.com/api/v1"
DEFAULT_PROJECT_ID = "d8f4a221-bc6d-47d8-9448-0834f5d012ec"

# ── local.properties loader ───────────────────────────────────────────────────

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

# ── TestSigma API client ──────────────────────────────────────────────────────

class TestSigmaClient:
    def __init__(self, token: str):
        import urllib3
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.session = requests.Session()
        self.session.verify = False   # macOS Homebrew SSL bundle issue
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

    def list_runs(self, project_id: str = DEFAULT_PROJECT_ID, size: int = 20) -> list:
        """List recent test runs (for discovery)."""
        data = self.get(f"/projects/{project_id}/test_runs", params={"size": size})
        return (data.get("data") or {}).get("test_runs") or data.get("content") or []

    def get_run_test_cases(self, run_id: str,
                            project_id: str = DEFAULT_PROJECT_ID) -> list:
        """
        Fetch all test cases from a test run.
        Endpoint: GET /projects/{projectId}/test_runs/{runId}/test_cases
        Response:  data.test_run_cases[].test_case
        """
        cases = []
        cursor = None
        page   = 0

        total = None

        while True:
            page += 1
            params = {"size": 50}
            if cursor:
                params["next"] = cursor

            data = self.get(
                f"/projects/{project_id}/test_runs/{run_id}/test_cases",
                params=params,
            )

            run_cases = (data.get("data") or {}).get("test_run_cases") \
                        or data.get("content") \
                        or []

            # Unwrap test_case from each run_case entry
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

            # Stop when no more data, no next cursor, or we've hit the reported total
            if not run_cases or not next_cursor or (total and len(cases) >= total):
                break
            cursor = next_cursor
            time.sleep(0.2)

        return cases

# ── HTML → plain text ─────────────────────────────────────────────────────────

def strip_html(html: str) -> str:
    """Strip HTML tags and decode basic entities."""
    if not html:
        return ""
    text = re.sub(r"<br\s*/?>", "\n", html, flags=re.IGNORECASE)
    text = re.sub(r"<p[^>]*>", "\n", text, flags=re.IGNORECASE)
    text = re.sub(r"</p>", "", text, flags=re.IGNORECASE)
    text = re.sub(r"<[^>]+>", "", text)
    text = text.replace("&amp;", "&").replace("&lt;", "<").replace(
           "&gt;", ">").replace("&quot;", '"').replace("&#39;", "'")
    # Collapse blank lines
    lines = [l.strip() for l in text.splitlines() if l.strip()]
    return "\n".join(lines)

# ── Element repo loader ───────────────────────────────────────────────────────

def load_element_repo() -> dict:
    repo = {}
    if not ELEMENTS_DIR.exists():
        return repo
    for f in sorted(ELEMENTS_DIR.glob("*.yaml")):
        try:
            data = yaml.safe_load(f.read_text(encoding="utf-8")) or {}
            repo[f.stem] = list(data.keys())
        except Exception as e:
            print(f"  ⚠️  Could not parse {f.name}: {e}")
    return repo

def format_element_repo(repo: dict) -> str:
    lines = []
    for module, names in repo.items():
        lines.append(f"\n### {module}.yaml")
        for n in names:
            lines.append(f"  - {n}")
    return "\n".join(lines)

# ── TestTags parser ───────────────────────────────────────────────────────────

def parse_existing_tags(popdroid: Path) -> set:
    path = popdroid / TEST_TAGS_PATH
    if not path.exists():
        return set()
    text   = path.read_text(encoding="utf-8")
    consts = set(re.findall(r'const val\s+(\w+)\s*=\s*"([^"]+)"', text))
    names  = {c[0] for c in consts}
    values = {c[1] for c in consts}
    fns    = set(re.findall(r"fun\s+(\w+)\s*\(index:", text))
    return names | values | fns

# ── Filtering helpers ─────────────────────────────────────────────────────────

def is_automatable(tc: dict) -> bool:
    """Skip deleted/obsolete cases."""
    if tc.get("deleted"):
        return False
    status = (tc.get("status_id") or "").lower()
    return True   # status_id is a UUID — keep all unless deleted

def slugify(text: str, max_len: int = 40) -> str:
    s = re.sub(r"[^\w\s-]", "", text.lower())
    s = re.sub(r"[\s_-]+", "_", s).strip("_")
    return s[:max_len]

# ── Claude prompt ─────────────────────────────────────────────────────────────

CLAUDE_PROMPT_TEMPLATE = """\
You are a QA automation engineer for the POP Club Android app.
Convert the TestSigma test cases below into runnable Forge test YAML files.

━━━ CONTEXT ━━━
• Framework: Forge (Java/TestNG + Appium) with YAML-driven test steps
• Platform: Android
• Phone for login: 1234561122  |  OTP: 560102  (test accounts, fixed OTP)
• Every test that needs auth must include the captureToken step (CI-safe JWT)
• Run a single test:  mvn test -DtestFile=<filename>.yaml

━━━ AVAILABLE ELEMENT TAGS ━━━
Only use elements from this list. Flag anything missing in Section 4.

{element_repo}

━━━ EXISTING TESTTAGS ({tag_count} constants — do NOT re-suggest these) ━━━
{existing_tags}

━━━ FORGE YAML FORMAT ━━━
```yaml
testName: <Descriptive name>
platform: android
noReset: false

testCaseIds:
  - PO-1234

features:
  - home
  - shop

tags:
  - smoke
  - shop

retry: 1

steps:
  - action: launchApp

  - action: tapIfPresent
    element: skip_tour

  # include login steps when test needs auth:
  - action: enterText
    element: login_input_phone
    value: 1234561122

  - action: tap
    element: login_button

  - action: enterText
    element: otp_input
    value: 560102

  - action: captureToken
    value: "1234561122"
    text: "560102"

  - action: waitFor
    element: home_tab

  # test-specific steps:
  - action: tap
    element: shop_tab

  - action: waitFor
    element: shop_search_icon

  - action: verifyElement
    element: search_input
```

Available actions: launchApp, tapIfPresent, tap, enterText, waitFor,
verifyElement, scroll, swipe, captureToken, verifyCLP, assertText, back

━━━ TEST CASES TO CONVERT ━━━
{test_cases_block}

━━━ OUTPUT — produce ALL FOUR sections ━━━

## 1. Test Case Inventory — {batch_label}

| # | TestSigma ID | Title | Automated? | Notes |
|---|--------------|-------|------------|-------|
(one row per test case — Automated = Yes / Partial / No + reason)

## 2. Coverage Gaps
List cases that could NOT be fully automated and why
(missing elements, payment flows, biometrics, complex gestures, etc.)

## 3. Forge Test YAMLs
For each automatable test case output a YAML block starting with a FILE comment.
Format EXACTLY — no extra text between blocks:

```yaml
# FILE: ts_{id}_{slug}.yaml
testName: <name>
...full yaml...
```

## 4. Missing Tags YAML
List every element used in the YAMLs that is NOT in the element repo.
Format EXACTLY:

```yaml
missing:
  - element:    Sort button on product list
    class:      android.widget.Button
    screen:     Product List
    suggestion: const val PRODUCT_LIST_SORT_BUTTON = "product_list_sort_button"
```
"""

def format_test_cases_block(cases: list) -> str:
    lines = []
    for i, tc in enumerate(cases, 1):
        human_id = tc.get("human_id", f"TC-{i}")
        title    = tc.get("title", "Untitled")
        steps    = strip_html(tc.get("steps") or "")
        expected = strip_html(tc.get("expected_results") or "")
        labels   = [l.get("name") for l in (tc.get("labels") or []) if l.get("name")]
        status   = tc.get("_run_status", "")

        lines.append(f"\n{'─' * 60}")
        lines.append(f"TEST CASE {i}: {human_id} — {title}")
        if status:
            lines.append(f"Last run status: {status}")
        if labels:
            lines.append(f"Labels: {', '.join(labels)}")
        lines.append("Steps:")
        if steps:
            for j, line in enumerate(steps.splitlines(), 1):
                lines.append(f"  {j}. {line}")
        else:
            lines.append("  (no steps recorded)")
        if expected:
            lines.append("Expected results:")
            for line in expected.splitlines():
                lines.append(f"  → {line}")

    return "\n".join(lines)

# ── Claude call ───────────────────────────────────────────────────────────────

def call_claude(prompt: str) -> str:
    cmd = [
        "claude", "--print",
        "--allowedTools", "Read",
        "--permission-mode", "bypassPermissions",
        "--no-session-persistence",
        "-p", prompt,
    ]
    env = os.environ.copy()
    env.pop("ANTHROPIC_API_KEY", None)

    result = subprocess.run(cmd, capture_output=True, text=True, env=env,
                            stdin=subprocess.DEVNULL, timeout=300)
    if result.returncode != 0:
        err = (result.stderr or result.stdout or "unknown").strip().splitlines()[0]
        print(f"❌  Claude error: {err}", file=sys.stderr)
        sys.exit(1)

    return result.stdout.strip()

# ── Output helpers ────────────────────────────────────────────────────────────

def extract_named_yaml_blocks(text: str, marker: str) -> list:
    """Return [(filename, content)] for every # FILE: ... yaml block after marker."""
    idx = text.find(marker)
    if idx == -1:
        return []
    sub     = text[idx:]
    results = []
    pos     = 0
    while True:
        start = sub.find("```yaml", pos)
        if start == -1:
            break
        end = sub.find("```", start + 7)
        if end == -1:
            break
        block = sub[start + 7 : end].strip()
        pos   = end + 3
        first = block.split("\n")[0].strip()
        m = re.match(r"#\s*FILE:\s*(.+)", first)
        if m:
            results.append((m.group(1).strip(), "\n".join(block.split("\n")[1:]).strip()))
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
    return sub[start + 7 : end].strip() if end != -1 else None

def save_batch_outputs(analysis: str, batch_label: str, out_dir: Path,
                       all_md_sections: list, all_missing: list) -> list:
    saved = []

    # Per-case YAMLs
    for filename, content in extract_named_yaml_blocks(analysis, "## 3. Forge Test YAMLs"):
        p = out_dir / filename
        p.write_text(
            f"# Generated by scripts/testsigma-import.py\n"
            f"# Source: TestSigma run — {batch_label}\n"
            f"# Generated: {datetime.now().strftime('%Y-%m-%d %H:%M')}\n"
            f"# Run with: mvn test -DtestFile={filename}\n\n"
            + content + "\n",
            encoding="utf-8",
        )
        print(f"  ✅  {filename}")
        saved.append(filename)

    if not saved:
        print(f"  ⚠️  No Forge YAML blocks in Claude output for: {batch_label}")

    # Accumulate inventory + gaps
    for h2 in ["## 1. Test Case Inventory", "## 2. Coverage Gaps"]:
        idx = analysis.find(h2)
        if idx == -1:
            continue
        nxt = analysis.find("## 3.", idx)
        all_md_sections.append(analysis[idx : nxt if nxt != -1 else len(analysis)].strip())

    # Accumulate missing tags
    missing_yaml = extract_yaml_block(analysis, "## 4. Missing Tags YAML")
    if missing_yaml:
        try:
            data = yaml.safe_load(missing_yaml)
            if data and "missing" in data:
                all_missing.extend(data["missing"])
        except Exception:
            pass

    return saved

def save_final_report(name: str, all_md_sections: list, all_missing: list,
                      all_yamls: list, total_cases: int, out_dir: Path) -> None:
    # Markdown
    md_path = out_dir / f"{name}.md"
    header  = (
        f"# TestSigma Import — {name}\n\n"
        f"_Generated by `scripts/testsigma-import.py` · "
        f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}_\n\n"
        f"**Cases fetched:** {total_cases} &nbsp;|&nbsp; "
        f"**YAMLs generated:** {len(all_yamls)}\n\n---\n\n"
    )
    body   = "\n\n---\n\n".join(all_md_sections)
    footer = (
        "\n\n---\n\n## Generated Test Files\n\n"
        "| File | Run command |\n|------|-------------|\n"
        + "".join(f"| `{f}` | `mvn test -DtestFile={f}` |\n" for f in all_yamls)
        + "\n\n---\n\n_Patch missing tags: cd ../popdroid && claude /qa-tags_\n"
    )
    md_path.write_text(header + body + footer, encoding="utf-8")
    print(f"\n  📋  Report       → {md_path.relative_to(FORGE_ROOT)}")

    # Missing tags
    if all_missing:
        seen, deduped = set(), []
        for entry in all_missing:
            if isinstance(entry, dict):
                key = entry.get("suggestion", str(entry))
                if key not in seen:
                    seen.add(key)
                    deduped.append(entry)

        missing_path = out_dir / f"{name}_missing.yaml"
        lines = ["missing:"]
        for e in deduped:
            lines += [
                f"  - element:    {e.get('element', '')}",
                f"    class:      {e.get('class', 'android.view.View')}",
                f"    screen:     {e.get('screen', '')}",
                f"    suggestion: {e.get('suggestion', '')}",
                "",
            ]
        missing_path.write_text(
            f"# Missing test tags — add to Popdroid: TestTags.kt\n"
            f"# Import: {name}\n\n" + "\n".join(lines) + "\n",
            encoding="utf-8",
        )
        print(f"  🏷️   Missing tags → {missing_path.relative_to(FORGE_ROOT)}")

# ── Main ──────────────────────────────────────────────────────────────────────

def main() -> None:
    if subprocess.run(["which", "claude"], capture_output=True).returncode != 0:
        print("❌  `claude` CLI not found.", file=sys.stderr); sys.exit(1)

    parser = argparse.ArgumentParser(
        prog="testsigma-import",
        description="Fetch TestSigma test run → runnable Forge YAMLs via Claude.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--run-id", required=True,
                        help="TestSigma test run UUID")
    parser.add_argument("--name", required=True,
                        help='Report name e.g. "shop_import" → reports/shop_import/')
    parser.add_argument("--case-id",
                        help="Convert only one case by human ID e.g. PO-1234")
    parser.add_argument("--batch", type=int, default=10,
                        help="Cases per Claude call (default: 10)")
    parser.add_argument("--dry-run", action="store_true",
                        help="Fetch & list cases without calling Claude")
    parser.add_argument("--max-cases", type=int, default=0,
                        help="Cap total cases (0 = all)")
    parser.add_argument("--project-id", default=DEFAULT_PROJECT_ID,
                        help=f"TestSigma project UUID (default: {DEFAULT_PROJECT_ID})")
    parser.add_argument("--popdroid", type=Path, default=DEFAULT_POPDROID,
                        help=f"Path to popdroid repo (default: {DEFAULT_POPDROID})")

    args = parser.parse_args()

    token   = get_testsigma_token()
    client  = TestSigmaClient(token)
    out_dir = FORGE_ROOT / "reports" / args.name
    out_dir.mkdir(parents=True, exist_ok=True)

    # Supporting data
    element_repo  = load_element_repo()
    existing_tags = parse_existing_tags(args.popdroid)
    element_str   = format_element_repo(element_repo)
    print(f"\n📦  {sum(len(v) for v in element_repo.values())} elements "
          f"from {len(element_repo)} YAML files")
    print(f"🏷️   {len(existing_tags)} existing TestTags constants")

    # Fetch
    print(f"\n🔍  Fetching test cases from run: {args.run_id}")
    all_cases = client.get_run_test_cases(args.run_id, args.project_id)
    print(f"\n✅  Fetched: {len(all_cases)} cases")

    # Filter
    all_cases = [c for c in all_cases if is_automatable(c)]

    if args.case_id:
        all_cases = [c for c in all_cases
                     if c.get("human_id") == args.case_id]
        if not all_cases:
            print(f"❌  Case not found: {args.case_id}", file=sys.stderr); sys.exit(1)

    if args.max_cases > 0:
        all_cases = all_cases[:args.max_cases]
        print(f"    Capped to {args.max_cases} (--max-cases)")

    if not all_cases:
        print("⚠️  No cases to process."); sys.exit(0)

    # Dry run
    if args.dry_run:
        print(f"\n══ DRY RUN — {len(all_cases)} cases ══")
        for i, tc in enumerate(all_cases, 1):
            steps_raw = strip_html(tc.get("steps") or "")
            n_steps   = len([l for l in steps_raw.splitlines() if l.strip()])
            print(f"  {i:>3}. {tc.get('human_id','?'):<12} "
                  f"{tc.get('title','Untitled')[:55]}  ({n_steps} steps)")
        print(f"\nRun without --dry-run to convert via Claude.")
        return

    # Claude batches
    batches = [all_cases[i:i + args.batch]
               for i in range(0, len(all_cases), args.batch)]
    print(f"\n🤖  {len(batches)} batch(es) → Claude  ({args.batch} cases/batch)\n")

    all_md_sections, all_missing, all_yamls = [], [], []

    for b_idx, batch in enumerate(batches, 1):
        ids         = [c.get("human_id", f"TC-{b_idx}") for c in batch]
        batch_label = f"Batch {b_idx}/{len(batches)}: {', '.join(str(i) for i in ids[:5])}"
        if len(ids) > 5:
            batch_label += f" +{len(ids)-5} more"
        print(f"  🔄  {batch_label}")

        first = batch[0]
        prompt = CLAUDE_PROMPT_TEMPLATE.format(
            element_repo    = element_str,
            tag_count       = len(existing_tags),
            existing_tags   = ", ".join(sorted(existing_tags)[:80])
                              + (f" … +{len(existing_tags)-80} more"
                                 if len(existing_tags) > 80 else ""),
            test_cases_block = format_test_cases_block(batch),
            batch_label     = batch_label,
            id              = slugify(str(first.get("human_id", "tc")), 10),
            slug            = slugify(first.get("title", "test")),
        )

        analysis = call_claude(prompt)
        print(analysis[:800])
        if len(analysis) > 800:
            print("  … (truncated)")

        saved = save_batch_outputs(analysis, batch_label, out_dir,
                                   all_md_sections, all_missing)
        all_yamls.extend(saved)
        print()

    # Final report
    print(f"\n💾  Saving summary → reports/{args.name}/")
    save_final_report(args.name, all_md_sections, all_missing, all_yamls,
                      len(all_cases), out_dir)

    print()
    print("═" * 60)
    print(f"  Cases processed : {len(all_cases)}")
    print(f"  YAMLs generated : {len(all_yamls)}")
    if all_yamls:
        print(f"  Run one        : mvn test -DtestFile={all_yamls[0]}")
    print(f"  Patch tags     : cd ../popdroid && claude /qa-tags")
    print("═" * 60)


if __name__ == "__main__":
    main()
