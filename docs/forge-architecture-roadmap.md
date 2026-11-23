# Forge Architecture Roadmap

**Status:** Draft for review
**Date:** 2026-11-17
**Goal:** Make Forge a platform-agnostic, fast, low-flake mobile automation framework.

---

## 1. Where we are today

| Asset | State |
|---|---|
| YAML tests | 106 files across 13 feature folders (`RCBP`, `Shop_*`, `card`, `cardOnboarding`, `home`, `login`, `profile`, `shop`) |
| Actions | 46 implementations in `src/main/java/com/popclub/android/actions/` |
| Self-healing | `com.popclub.heal.SelfHealingEngine` (Android), `com.popclub.web.heal.WebSelfHealingEngine` (web) — no shared abstraction |
| gRPC driver | 13 unary RPCs, on-device companion APK, port 7790 — **unwired from test execution** |
| iOS | `com.popclub.driver.ios` (Appium-based) |
| AI actions | `AssertWithAI`, `AssertNoDefectsWithAI`, `ExtractTextWithAI`, `EvalScript` — recently added, ported from Maestro's command set |
| Device farm | **STF integrated and working** — `STFClient`, `CloudConfig`, `cloud.properties`, Appium servers pre-started on the STF host |
| Parallel execution | **Already works** — `parallel="tests" thread-count="2"` in `testng-multi-device.xml`, `ThreadLocal` drivers |

### The three structural problems

**1. Actions are nailed to one platform and one transport.**

Every action resolves its driver from a static singleton returning a concrete type:

```java
// TapAction.java:22, WaitForAction.java:14, and ~44 more
AndroidDriver driver = DriverManager.getDriver();
WaitUtil.pollUntilVisible(driver, step.locators, timeout);
```

The `Action` contract has no context parameter at all:

```java
public interface Action { void perform(Step step); }
```

41 action call sites go through `DriverManager.getDriver()`, which returns a concrete `AppiumDriver`.

Consequences: the gRPC driver cannot be used by any action, and iOS cannot share action code.

**Not a consequence — correcting an earlier draft:** parallelism is *not* blocked. `DriverManager.java:11` is `private static ThreadLocal<AppiumDriver> driver`, `AppiumDriverManager.java:19-20` is likewise `ThreadLocal`, and `testng-multi-device.xml` already runs `parallel="tests" thread-count="2"` across two devices. Parallel execution works today.

**2. Three retry layers stack multiplicatively, and nothing waits for the app to settle.**

| Layer | Location | Behaviour |
|---|---|---|
| Step retry | `TestExecutor.java:359-419` | `maxRetry = retry > 0 ? retry - 1 : 2` → 3 attempts default |
| Per-action wait | each action calls `WaitUtil.pollUntilVisible` | 30s default (`TestContext.java:47`), 100ms poll (`WaitUtil.java:36`) |
| Healing | `TestExecutor.java:403` | Only after the retry budget is exhausted |

Worst case for a genuinely missing element: **3 × 30s = 90 seconds before healing even starts.**

Separately — and more importantly — **no action waits for the app to settle after acting.** Every action fires and immediately proceeds, racing animations and in-flight loads. Retry tuning cannot fix this.

**3. Healing is slow, narrow, and rewrites source files unreviewed.**

- First move is `driver.getPageSource()` (`SelfHealingEngine.java:192`) — the slowest call in the Appium protocol, 1–5s on a dense screen.
- Only extracts `content-desc` values matching `^[a-z][a-z0-9]*(_[a-z0-9]+)+$` (`:200`). Cannot heal `resource-id`, XPath, or text locators.
- Triggers on `exception.getMessage()` string-matching (`:261`), not exception type — a message change silently disables healing.
- `MIN_SCORE` is 0.60 (`:32`) while the class doc says 0.40.
- **`patchTestStep()` (`:130-185`) regex-rewrites test YAML files in place during a run.** A 0.60-confidence token-overlap match is written into the source of truth with no human review.

---

## 2. What we learned from Maestro

We evaluated replacing Forge with [Maestro](https://github.com/mobile-dev-inc/maestro). **Conclusion: do not replace.** But its source answers three design questions directly.

### 2.1 Element finding belongs above the driver, not inside it

`maestro-client/src/main/java/maestro/Driver.kt` exposes only raw observation:

```kotlin
fun contentDescriptor(excludeKeyboardElements: Boolean = false): TreeNode
fun queryOnDeviceElements(query: OnDeviceElementQuery): List<TreeNode> = listOf()
fun capabilities(): List<Capability>
```

Matching happens in `Maestro.kt` (`findElementWithTimeout`, `allElementsMatching`) over composable predicates in `Filters.kt`: `textMatches`, `idMatches`, `containsChild`, `index`, and relational ones — `below`, `above`, `leftOf`, `rightOf`.

Those relational filters are the same mechanism a neighbour-anchor healing strategy needs. Putting lookup above the driver means healing strategies compose with normal lookup instead of being a separate engine.

### 2.2 Settling after action is the real anti-flake mechanism

```kotlin
fun waitForAppToSettle(initialHierarchy: ViewHierarchy?, appId: String?, timeoutMs: Int? = null): ViewHierarchy?
fun waitUntilScreenIsStatic(timeoutMs: Long): Boolean
```

Algorithm:
1. Screenshot-diff until the screen stops animating (`SCREEN_SETTLE_TIMEOUT_MS = 3000`).
2. Fall back to view-hierarchy diffing: fetch repeatedly, require the hierarchy be **identical twice in a row** and the root's `is-loading` attribute false, polling every 200ms.

Applied to **mutating** commands (`tap`, `swipe`, `inputText`, `openLink`, `pressKey`) after they act. Reads and assertions use lookup poll loops instead.

Reference constants (verify against the version targeted):

| Constant | Value | File |
|---|---|---|
| `lookupTimeoutMs` | 17000 | `Orchestra.kt` |
| `optionalLookupTimeoutMs` | 7000 | `Orchestra.kt` |
| `ANIMATION_TIMEOUT_MS` | 15000 | `Maestro.kt` |
| `ELEMENT_STABILITY_TIMEOUT_MS` | 3000 (poll 100ms) | `Maestro.kt` |
| `SCREEN_SETTLE_TIMEOUT_MS` | 3000 | `IOSDriver.kt` |

Note the split is **required vs optional**, not positive vs negative assertion.

### 2.3 Building our own iOS driver is a dead end

Maestro's own history is the experiment we were considering:

- Started on Facebook's **idb** (gRPC). It used private Apple APIs, returned incomplete hierarchies (missing `UITabBar` elements), threw frequent gRPC errors, and **broke on iOS 16**.
- Replaced it in v1.18 with a bespoke **Swift XCUITest runner** (`maestro-ios-xctest-runner`): ~67 Swift files, an Xcode project, ~20 HTTP route handlers, plus accessibility internals (`AXClientSwizzler.swift`, `PointerEventPath.swift`).
- A funded company with a dedicated mobile team built that, and the README still states: **"Physical iOS devices are not yet supported."**
- Physical-device support exists only in a third-party fork (`devicelab-dev/maestro-runner`) — which gets there using **WebDriverAgent**.

Also notable: Maestro chose **HTTP, not gRPC**, for its iOS runner.

**Therefore: drop "gRPC on both platforms" as a goal.** The correct goal is *one `UiDriver` interface, transport chosen per platform on the merits.* Tests care about the interface; the wire protocol is an implementation detail.

---

## 3. What the STF device farm changes

We run our own STF farm, already integrated. This resolves the iOS question and surfaces one blocker.

### 3.1 The gRPC driver cannot reach an STF device at all

`ForgeDriverManager.adb()` (`ForgeDriverManager.java:98-111`) builds `adb -s <serial> ...` with **no `-H` / `-P` host flags**, so it always talks to the *local* ADB server. STF devices live on the farm host. The gRPC companion APK is launched and port-forwarded against the wrong ADB daemon — so `adb forward tcp:7790` silently targets nothing.

`ForgeDriverManager` has no awareness of `DeviceManager`, `CloudConfig`, or `adbHost`. **The gRPC path works only against a locally-plugged device.** On our actual infrastructure it does not work today.

The fix is cheap and the pattern already exists: `AppiumDriverManager.adbBase()` (`AppiumDriverManager.java:301-313`) already does `-H <adbHost> -P <adbServerPort>`. `ForgeDriverManager` needs to use it. Caveats to design around:
- STF remote-debug ports can change on provider restart and may even be reassigned to a different device — a forwarded tunnel can silently break or cross-wire.
- `adb reverse` only works from STF 3.9.0 / adbkit 4 onward; `adb forward` is not explicitly documented as supported over `remoteConnect`, though it should work as a normal ADB transport. **Verify empirically before building on it.**
- Unknown whether STF's `minicap`/`minitouch` agents conflict with an instrumentation-based server on the same device. Also needs an empirical check.

### 3.2 iOS: physical devices mean WebDriverAgent, confirmed

- `DeviceFarmer/stf_ios_support` uses **WebDriverAgent** and requires a macOS provider host.
- STF 3.9.0 added *experimental* built-in iPhone support on Linux providers (`doc/ios-provider.md`), explicitly labelled experimental.
- Known issues in the iOS path: plain-HTTP STF servers unsupported, video streams getting stuck, and no simultaneous multi-device video capture per provider.

Combined with Maestro's own outcome (§2.3), **physical iOS automation terminates at WebDriverAgent on every available path.** Phase 4's "do not build" is now settled on our own infrastructure, not just in theory.

### 3.3 Parallel is already solved; dynamic allocation is not

`testng-multi-device.xml` hardcodes two serials (`:14`, `:25`). The STF REST API supports pooled allocation (`GET /api/v1/devices`, `POST /api/v1/user/devices`), and `STFClient` already wraps reserve/release. The remaining work is replacing hardcoded serials with pool allocation — not building parallelism.

### 3.4 STF integration defects found

| Issue | Location | Impact |
|---|---|---|
| No `finally` / shutdown hook around device release | `STFClient.java:257-277`, `TestRunnerTest.java:159-163` | `kill -9` / CI timeout leaks the reservation. Mitigated only by the unconditional pre-reserve release at `DeviceManager.java:105`, which races under parallel load |
| Cloud `systemPort = 18200 + random(0,1000)` | `AppiumServerManager.java:132` | No registry, collision-prone. Local mode correctly uses OS-assigned `ServerSocket(0)` (`AppiumDriverManager.java:182-183`) |
| 401 clears token cache but still throws | `STFClient.java:316-324` | Caller must retry; no automatic retry |
| Flat 30s HTTP timeout, not configurable | `STFClient.java` throughout | — |
| `getRemoteConnectUrl()` never called | `STFClient.java:232-252` | Dead code |
| `adbConnect`/`adbReconnect`/`adbDisconnect` never called | `DeviceManager.java:212,218` | Dead — cloud path relies on Appium being co-located on the STF host (`AppiumDriverManager.java:99-100`) |
| TestSigma integration commented out | `TestRunnerTest.java:25-29,165-169` | "Re-enable once token is refreshed" |

---

## 4. Target architecture

```
          YAML test (unchanged)
                  │
            TestExecutor            ← flow-level retry only
                  │
          ActionFactory.get()       ← single decoration point
                  │
          ResilientAction           ← wait policy + settle + heal hook
                  │
             Action.perform(step, ctx)
                  │
        ┌─────────┴─────────┐
   ElementLookup         UiDriver
   (Filters)         ┌──────┼──────────────┐
                     │      │              │
         GrpcAndroidUiDriver│    AppiumUiDriver(IOSDriver)
                   AppiumUiDriver(AndroidDriver)
```

### 4.1 `UiDriver` — gestures and raw observation only

```java
public interface UiDriver {
    // observation
    TreeNode contentDescriptor();
    List<TreeNode> query(NodeQuery query);      // → SearchNodes on the gRPC path
    byte[] screenshot();
    DeviceInfo deviceInfo();

    // interaction
    void tap(Point p);
    void longPress(Point p);
    void inputText(String text, boolean clearFirst);
    void eraseText(int charactersToErase);
    void swipe(Point start, Point end, long durationMs);
    void pressKey(Key key);

    // settling  ← the anti-flake primitive
    boolean waitUntilScreenIsStatic(long timeoutMs);
    ViewHierarchy waitForAppToSettle(ViewHierarchy initial, long timeoutMs);

    // lifecycle & introspection
    void launchApp(String appId);
    void stopApp(String appId);
    Platform platform();
    List<Capability> capabilities();
    boolean isShutdown();
}
```

`capabilities()` keeps the abstraction honest: the gRPC driver has fast `SearchNodes`; the Appium driver does not. Actions query capability rather than sniffing types.

Note: `waitForAppToSettle` returning `null` means the driver *could not confirm* settling — not that it settled.

### 4.2 `ElementLookup` + `Filters` — matching above the driver

```java
public interface ElementFilter extends Predicate<TreeNode> { }

public final class Filters {
    public static ElementFilter accessibilityIdMatches(String pattern);
    public static ElementFilter resourceIdMatches(String pattern);
    public static ElementFilter textMatches(String pattern);
    public static ElementFilter below(ElementFilter anchor);
    public static ElementFilter above(ElementFilter anchor);
    public static ElementFilter leftOf(ElementFilter anchor);
    public static ElementFilter rightOf(ElementFilter anchor);
    public static ElementFilter containsChild(ElementFilter child);
    public static ElementFilter index(int i);
}

public final class ElementLookup {
    Optional<UiElement> findWithTimeout(List<Locator> locators, Duration timeout);
    List<UiElement> allMatching(ElementFilter filter);
}
```

### 4.3 `ActionContext` — kills the static singleton

```java
public interface Action { Result perform(Step step, ActionContext ctx); }

public interface ActionContext {
    UiDriver driver();
    ElementLookup lookup();
    ElementRepository elements();
    TimeoutPolicy timeouts();
    HealHook heal();
}
```

This is also what unblocks parallel multi-device execution.

---

## 5. Phased plan

### Phase 0 — Correctness fixes (0.5 day, no risk)

| Issue | Location | Fix |
|---|---|---|
| `MIN_SCORE` doc 0.40 vs constant 0.60 | `SelfHealingEngine.java:32` | Reconcile; 0.60 is likely too strict |
| Healing gates on message string-match | `SelfHealingEngine.java:261` | Match on exception type |
| Port doc says 6790, constant is 7790 | `ForgeDriverManager.java:10` | Fix comment |
| 11 of 13 RPC wrappers lack try/catch | `ForgeDriverClient.java` | Uniform error envelope |
| Dead `cmd` array | `ForgeDriverManager.java:99` | Delete |
| Dead `Base64` import | `ForgeDriverClient.java:8` | Delete |
| Host/port hardcoded in two files | both | Extract `ForgeDriverConfig` |
| **gRPC `adb` ignores STF host** | `ForgeDriverManager.java:98-111` | **Use the `-H`/`-P` pattern from `AppiumDriverManager.adbBase():301-313`. Blocks all gRPC work on the farm — see §3.1** |
| No shutdown hook for device release | `STFClient.java:257-277` | Add `Runtime.addShutdownHook` + `finally`; `kill -9` currently leaks reservations |
| Cloud `systemPort` randomised, no registry | `AppiumServerManager.java:132` | Track per-slot, or use the OS-assigned pattern from `AppiumDriverManager.java:182-183` |
| Hardcoded device serials | `testng-multi-device.xml:14,25` | Replace with STF pool allocation (§3.3) |
| Dead: `getRemoteConnectUrl`, `adbConnect`/`adbReconnect`/`adbDisconnect` | `STFClient.java:232-252`, `DeviceManager.java:212,218` | Delete or wire up |

### Phase 1 — Platform abstraction (1–2 weeks) — **keystone**

1. Define `UiDriver`, `ElementLookup`, `Filters`, `ActionContext`.
2. Implement `AppiumUiDriver` wrapping today's `AndroidDriver`/`IOSDriver`. **Pure refactor, zero behaviour change** — the 106 tests are the regression suite.
3. Migrate actions off `DriverManager.getDriver()` onto `ctx.driver()`, behind a flag, action by action, `mvn test` green at every step.
4. Implement `GrpcAndroidUiDriver` over the existing `ForgeDriverClient` and wire it into `ActionFactory` as a selectable transport.

Most of the 46 actions are mechanical. The exceptions needing real attention: `TapAction` (special-cases `point`/`bounds` locator types before reaching `WaitUtil`, `TapAction.java:30-49`), and the inline-lambda cases in `ActionFactory` (`repeat` `:28`, `assertVisible`/`assertNotVisible` `:32-33`, no-op sentinels `:73-84`).

### Phase 2 — Settle-by-default and retry collapse (1 week) — **highest value**

1. Implement `waitForAppToSettle` on both drivers (screenshot diff → hierarchy diff, per §2.2). The gRPC path already has the primitives: `Screenshot` and `SearchNodes`/`GetSource`.
2. Add `ResilientAction`, applied at the one chokepoint every action passes through — `ActionFactory.get(String)` (`ActionFactory.java:9`):

```java
public static Action get(String action) {
    Action base = switch (action) { /* unchanged */ };
    return ResilientAction.wrap(base, WaitPolicy.forAction(action));
}
```

3. Settle after mutating actions; use lookup poll loops for reads/asserts.
4. Adopt the required/optional timeout split (~17s / ~7s) in place of the flat 30s.
5. **Delete the step-level retry in `TestExecutor` and the per-action timeout boilerplate.** Do not stack a third layer — `ResilientAction` replaces them. `TestExecutor` retry becomes flow-level only.

Expected effect: median step unchanged; worst-case failing step drops from ~90s to ~10s; animation-race flake largely eliminated.

### Phase 3 — Healing rework (1 week)

1. **Stop auto-rewriting test YAML.** Replace `patchTestStep()` with a patch *proposal* written to `reports/` (diff or PR) for human review. Heal in memory, continue the run. **Highest-severity item in this document** — ship it independently of everything else if needed.
2. Swap `getPageSource()` for `SearchNodes` on the gRPC path (~100x faster); keep `getPageSource()` as the Appium fallback via `capabilities()`.
3. Move the heal hook from post-retry-exhaustion into `ResilientAction`, attempted after the **first** discovery miss. With `SearchNodes` this costs ~20ms, so early attempts are nearly free.
4. Make scoring strategy-based, reusing `Filters`:

```java
interface HealStrategy { Optional<Candidate> propose(Step step, List<TreeNode> tree); }
// AccessibilityIdSimilarity  — today's Dice scorer
// ResourceIdSimilarity
// TextSimilarity
// NeighbourAnchor            — via Filters.below/above/leftOf/rightOf
// BoundsProximity            — last-known-good coords, low confidence
// AiVisualGrounding          — reuses AssertWithAI plumbing, last resort
```

5. Unify `SelfHealingEngine` and `WebSelfHealingEngine` behind one interface.

### Phase 4 — iOS — **do not build**

Use `AppiumUiDriver(IOSDriver)` behind the `UiDriver` interface. YAML, actions, and healing become platform-agnostic; the transport differs underneath and test authors never see it. ~90% of the benefit for ~5% of the cost.

**Settled, not merely recommended.** Every path to physical iOS automation ends at WebDriverAgent: Maestro's own rebuild got to simulator-only after ~67 Swift files (§2.3), and STF's iOS provider is WDA-based with a macOS host requirement (§3.2). Do not evaluate `facebook/idb` either — Maestro already ran that experiment and abandoned it over private-API breakage.

### Phase 5 — Super-tool capabilities (after Phase 1)

- **Dynamic STF pool allocation** — parallel execution already works (§3.3); the gap is replacing hardcoded serials with `GET /api/v1/devices` + `POST /api/v1/user/devices` pool reservation, so thread count scales with farm capacity instead of being pinned to two known handsets. Biggest wall-clock win on a 106-test suite.
- **Record-and-replay** — `SearchNodes` + `Screenshot` per step produces a trace; feeds debugging and test generation.
- **Trace-based failure reports** — node tree + screenshot + proposed heal at the failure point, instead of a stack trace.
- **Element-repo drift detection** — walk every key in `testdata/elements/` against a live build, report drift before tests run.

---

## 6. Sequencing

```
Phase 0  (0.5d)  ── fixes, no risk
Phase 1  (1-2w)  ── UiDriver + Filters + ActionContext   ← everything depends on this
   ├─ Phase 2  (1w)      settle-by-default, retry collapse   ← highest value
   ├─ Phase 3  (1w)      healing: proposals, SearchNodes, heal-first, strategies
   └─ Phase 5  (ongoing) parallel, tracing
Phase 4          ── do not build
```

Phase 1's `Filters` layer is what makes Phase 3's healing strategies cheap, so the refactor pays for itself twice.

**If only two things get done:** Phase 3 item 1 (stop rewriting test YAML — risk) and Phase 2 (settle + retry collapse — speed and flake). Both are days, not weeks, and neither requires waiting on the full refactor.

---

## 7. Open questions

**Needs an empirical check before Phase 1 step 4 (gRPC on the farm):**
- Does `adb forward` actually work over an STF `remoteConnect` transport? It is not documented as supported, only inferred from it being a normal ADB transport. **Test this on one device before building on it.**
- Do STF's `minicap`/`minitouch` agents conflict with an instrumentation-based gRPC server on the same device? No documentation either way.
- STF remote-debug ports can change on provider restart and may be reassigned to another device. What is our reconnection/validation strategy for a long-lived tunnel?
- Is the gRPC companion APK maintained and installable on farm devices in CI (`./forge-driver/install.sh`)?

**Needs a decision:**
- What is the current measured flake rate per feature folder? Phase 2's impact should be measured against a baseline we do not yet have.
- Should the 4 flows in `maestro/` be kept as an ongoing benchmark, or retired once Phase 2 lands?
- Is the gRPC transport worth pursuing at all given §3.1, or should Phase 1 ship with `AppiumUiDriver` only and treat gRPC as optional? The healing speedup (§5 Phase 3 item 2) is its main justification — if `SearchNodes` cannot be reached over STF, that justification weakens considerably.
