# QA Tag Pipeline — End-to-End Guide

## What is this?

A pipeline that ensures every interactive element in the POP Android app
has a proper `qaTestTag` so Appium tests in Forge can locate them reliably.

---

## Repos involved

| Repo | Purpose |
|---|---|
| `popdroid` | Android source code — where tags are added |
| `Forge` | Test automation — where tags are used in YAML test steps |

---

## Two ways tags are detected

### 1. Static scan (source code)
Scans Kotlin source files for interactive Compose elements missing `.qaTestTag(...)`.
Does NOT need a device.

### 2. Live scan (real device / emulator)
Runs the actual app, dumps the screen, checks which tags are present and
whether they follow the naming convention.
Needs a connected device or emulator.

---

## Naming convention (must follow this)

| Element type | Format | Example |
|---|---|---|
| Single element | `const val SCREEN_ELEMENT_TYPE` | `const val LOGIN_CONTINUE_BUTTON` |
| List/repeated | `fun screenSectionItem(index: Int)` | `fun homeBannerItem(index: Int)` |
| Tag value | `snake_case` with screen prefix | `"login_continue_button"` |

**Rule:** tag must start with the screen name prefix.
`login_continue_button` ✅ — `continue_button` ❌ — `ContinueButton` ❌

---

## Files & scripts

### Popdroid repo

| File | What it does |
|---|---|
| `core/.../TestTags.kt` | Single source of truth — all `const val` and `fun` tag declarations |
| `scripts/qa-tag-detect.py` | Static scan — finds Compose elements missing `.qaTestTag()` |
| `scripts/qa-tag-forge-sync.py` | Runs post-merge via GitHub Actions — syncs tags to Forge element YAMLs |
| `scripts/pre-push` | Git hook — runs static scan before every push, blocks if tags missing |

### Forge repo (`android_automation` branch)

| File | What it does |
|---|---|
| `src/test/resources/elements/*.yaml` | Element locators used in YAML test steps |
| `src/main/java/.../TagFinder.java` | Interactive CLI — start Appium, scan screens live |
| `src/main/java/.../QaTagAnalyzer.java` | Core analysis — checks naming, finds missing, suggests names |
| `src/main/java/.../ScanTagsAction.java` | Mid-test scan — use `action: scanTags` in any test YAML |
| `run-tag-finder.sh` | Script to run TagFinder standalone |
| `reports/qa-app-scan/` | Output folder — per-screen YAML reports + summary |

---

## Step-by-step flows

---

### Flow 1 — Developer adds a new screen

**Where:** Popdroid repo

```
1. Write your Composable screen

2. Run static scan to find missing tags:
   python3 scripts/qa-tag-detect.py <path/to/YourScreen.kt>
   → writes build/qa-tag-report.md with suggested tag names

3. Fix missing tags (two ways):
   A. Manual: copy suggested names from report → add to TestTags.kt
              add .qaTestTag(TestTags.YOUR_TAG) to each element
   B. Auto:   run `claude /qa-tags` → Claude reads the report and applies fixes

4. Verify:
   python3 scripts/qa-tag-detect.py <path/to/YourScreen.kt>
   → should print "all interactive elements already tagged"

5. Push Popdroid PR → development
   (pre-push hook runs static scan automatically — blocks if tags still missing)

6. PR gets reviewed and merged → development

7. GitHub Actions triggers automatically (post-merge):
   - runs qa-tag-forge-sync.py
   - pushes new tag entries to the right elements/*.yaml in Forge
   - opens/updates a PR in Forge → android_automation

   Developer does NOT need to run qa-tag-forge-sync.py manually.
```

---

### Flow 2 — QA runs live app scan

**Where:** Forge repo, needs device/emulator connected

```
1. Connect device or start emulator
   adb devices   ← verify device is listed

2. Run TagFinder:
   cd Forge
   ./run-tag-finder.sh                    ← auto-detect device
   ./run-tag-finder.sh emulator-5554      ← specific device

3. What happens automatically:
   - Appium server starts
   - App launches fresh (force-stop → relaunch)
   - Prompts: "Screen name (or ENTER):"

4. Navigate on device to a screen, then in terminal:
   - Type screen name (e.g. "login") and press ENTER
     OR just press ENTER to auto-detect from activity name

5. TagFinder outputs:
   ✅ Good tags   — present + correctly named
   ⚠️  Bad naming  — present but naming convention violated
   ❌ Missing     — interactive element has no tag at all

6. Per-screen report written to:
   reports/qa-app-scan/login.yaml

7. Repeat for each screen. Type 'exit' when done.

8. Summary written to:
   reports/qa-app-scan/summary.yaml
   reports/qa-app-scan/missing_from_app.txt
```

---

### Flow 3 — Mid-test inline scan (during CI test run)

**Where:** Forge test YAML

Add `action: scanTags` anywhere in a test YAML to scan the current screen:

```yaml
steps:
  - action: launchApp

  - action: tap
    element: login_phone_input

  - action: scanTags          # ← scans login screen, prints report, writes YAML
    value: login              # optional screen name label

  - action: tap
    element: login_continue_button

  - action: scanTags          # ← scans OTP screen
    value: otp
```

Output goes to `reports/qa-app-scan/<screenName>.yaml` during the test run.

---

## How missing_from_app.txt reaches the Popdroid developer

This is the key cross-repo problem.
`missing_from_app.txt` is generated in Forge by TagFinder.
Popdroid developers work in a different repo and may not have Forge cloned.

### The solution — GitHub Issue in Popdroid repo

After TagFinder scan completes (when you type `exit`), run:

```bash
gh issue create \
  --repo poptech-global-tech-pvt-ltd/popdroid \
  --title "[QA Tags] Missing tags found in live scan — $(date +%Y-%m-%d)" \
  --body-file reports/qa-app-scan/missing_from_app.txt \
  --label "qa-tags"
```

This creates a GitHub Issue directly in the Popdroid repo with the full list
of missing and badly named tags. The Popdroid developer:

1. Gets notified on GitHub (email / GitHub notification)
2. Opens the issue — sees exactly which screen, which element, and what to name it
3. Fixes the tags in source code
4. Closes the issue when done

### What the issue looks like

```
Title: [QA Tags] Missing tags found in live scan — 2026-05-20

# Missing / Bad QA Tags — Live App Scan

## Screen: login
  ❌ MISSING
     class: android.widget.EditText
     suggested: const val LOGIN_INPUT = "login_input"

  ❌ MISSING
     class: android.widget.Button
     suggested: const val LOGIN_BUTTON = "login_button"

  ⚠️ BAD NAMING
     tag: "Phone"  problem: PascalCase — should be snake_case with screen prefix
     suggested: const val LOGIN_PHONE_INPUT = "login_phone_input"

## Screen: home
  ❌ MISSING
     class: android.widget.Button  text: "Send Money"
     suggested: const val HOME_SEND_MONEY_BUTTON = "home_send_money_button"
```

### Prerequisites

Install GitHub CLI (one time):
```bash
brew install gh
gh auth login   # authenticate with your GitHub account
```

### Why GitHub Issue and not Slack / email?

| Method | Problem |
|---|---|
| Slack | No tracking — gets lost in chat |
| Email | No tracking — gets buried |
| File share | Dev needs Forge repo cloned |
| GitHub Issue | Tracked, assigned, linked to repo, closes when fixed ✅ |

---

## Full pipeline diagram

```
Developer writes new Composable screen
              ↓
  qa-tag-detect.py (static scan)
              ↓
     Missing tags found?
        YES                NO
         ↓                  ↓
   claude /qa-tags     qa-tag-forge-sync.py
         ↓                  ↓
  Tags added to        Tags pushed to
  TestTags.kt +        Forge elements/*.yaml
  source files
         ↓
  qa-tag-forge-sync.py
         ↓
  Tags pushed to Forge elements/*.yaml
         ↓
  Push Popdroid PR → development
  (pre-push hook blocks if still missing)
         ↓
  PR merged → development
         ↓
  GitHub Actions: qa-tag-forge-sync.py runs automatically
         ↓
  Forge elements/*.yaml updated + Forge PR opened
         ↓
  QA runs TagFinder on device
         ↓
  Live scan: good / bad / missing
         ↓
  missing_from_app.txt generated
         ↓
  gh issue create → Popdroid repo
         ↓
  Dev notified on GitHub
         ↓
  Dev fixes tags → closes issue
         ↓
  Loop repeats until all tags present
```

---

## How tags flow from Popdroid → Forge

```
Popdroid source code
  └── TestTags.kt          (const val LOGIN_CONTINUE_BUTTON = "login_continue_button")
  └── LoginScreen.kt       (.qaTestTag(TestTags.LOGIN_CONTINUE_BUTTON))
          ↓
  qa-tag-forge-sync.py     (reads TestTags.kt, maps prefix → YAML file)
          ↓
Forge elements/login.yaml
  login_continue_button:
    android:
      - type: accessibilityId
        value: login_continue_button
          ↓
Forge test YAML
  - action: tap
    element: login_continue_button
```

---

## How Appium finds the tag at runtime

```
qaTestTag("login_continue_button")
    ↓
Modifier.testTag("login_continue_button")       ← Compose test framework
    +
semantics { contentDescription = "login_continue_button" }  ← accessibility tree
    ↓
Appium getPageSource() → content-desc="login_continue_button"
    ↓
driver.findElement(AppiumBy.accessibilityId("login_continue_button"))
```

**This only works in debug builds.** `qaTestTag` is a no-op in release builds.

---

## Prefix → YAML file mapping

| Tag prefix | Forge YAML file |
|---|---|
| `login_`, `otp_` | `elements/login.yaml` |
| `home_` | `elements/home.yaml` |
| `upi_`, `send_money_`, `bank_transfer_`, `tss_`, `everything_upi_` | `elements/upi.yaml` |
| `shop_`, `product_`, `cart_`, `order_`, `search_` | `elements/shop.yaml` |
| `bill_`, `recharge_` | `elements/billpay.yaml` |
| `rewards_`, `cashback_`, `pop_coins_` | `elements/rewards.yaml` |
| `credit_card_`, `cc_`, `rupay_` | `elements/credit_card.yaml` |
| `profile_`, `help_support_`, `faq_`, `address_`, `refer_` | `elements/profile.yaml` |
| `toolbar_`, `common_`, `app_update_` | `elements/common.yaml` |

---

## Quick reference — commands

```bash
# Static scan (Popdroid)
python3 scripts/qa-tag-detect.py path/to/YourScreen.kt

# Auto-fix missing tags (Popdroid)
claude /qa-tags

# Sync source tags → Forge YAMLs (runs automatically via GitHub Actions post-merge)
# Run manually only for local testing:
python3 scripts/qa-tag-forge-sync.py --forge /Users/deepa/repos/Forge

# Live app scan (Forge)
cd /Users/deepa/repos/Forge
./run-tag-finder.sh                    # auto-detect device
./run-tag-finder.sh emulator-5554      # specific device

# After scan — create GitHub Issue in Popdroid (Forge)
gh issue create \
  --repo poptech-global-tech-pvt-ltd/popdroid \
  --title "[QA Tags] Missing tags — $(date +%Y-%m-%d)" \
  --body-file reports/qa-app-scan/missing_from_app.txt \
  --label "qa-tags"
```

---

## Who does what

| Role | Responsibility |
|---|---|
| **Android dev** | Add `qaTestTag` to new screens, keep TestTags.kt updated, fix GitHub Issues labelled `qa-tags` |
| **QA engineer** | Run TagFinder after each build, create GitHub Issue with missing tags |
| **QA engineer** | Write Forge test YAMLs using element names from `elements/*.yaml` |
| **CI pipeline** | Run static scan on every PR — block merge if tags missing |

---

## Setup checklist (one time per developer)

```
Android dev:
  [ ] cp scripts/pre-push .git/hooks/pre-push   (in Popdroid)
  [ ] chmod +x .git/hooks/pre-push
  [ ] brew install gh && gh auth login

QA engineer:
  [ ] Clone Forge repo
  [ ] git checkout android_automation
  [ ] brew install gh && gh auth login
  [ ] adb devices → confirm device appears
  [ ] Place debug APK at Forge/src/main/resources/pop-debug.apk
```
