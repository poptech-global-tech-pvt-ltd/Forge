# Tuition C2C Test Architecture

## Overview

The suite is a **hybrid test framework** — YAML-driven Android UI automation + Java REST API testing — running in a **single JVM** so state (JWT tokens, order numbers) flows across both layers without any external store.

```
┌─────────────────────────────────────────────────────┐
│                   Single JVM Process                │
│                                                     │
│  [YAML Layer]          [API Layer]                  │
│  tuition_create.yaml → TuitionPayerJourneyTest      │
│  (Android Forge)       TuitionOnboardingTest        │
│         │                      │                   │
│         └──── TestContext ─────┘                   │
│               (shared state)                        │
└─────────────────────────────────────────────────────┘
```

---

## Layer Breakdown

| Layer | Classes | Responsibility |
|---|---|---|
| **Test** | `TuitionPayerJourneyTest`, `TuitionOnboardingTest` | Assertions, step orchestration |
| **Service** | `CsvcService`, `OmsService`, `OnboardingService` | HTTP calls, auth headers |
| **DTO** | `PayeeBasicInfoRequest`, `CreateTuitionOrderRequest`, etc. | Request/response shape |
| **Data** | `TuitionTestData`, `tuition_test_data.json` | Test values |
| **Context** | `TestContext` | Cross-class state store |
| **Config** | `ConfigManager`, `sit.properties` | Environment config |
| **YAML** | `tuition_create.yaml` | Android login + token capture |

---

## E2E Flow

```
Android YAML login
        ↓  captureToken → TestContext
TuitionPayerJourneyTest
  step1  getPayeeList
  step2  getPayeeBasicInfo         → payeeId
  step3  verifyPayeeInfo
  step4  getPaymentSummary
  step5  generatePaymentIntent     → paymentIntentId, razorpay_order_id
  step6  reserveLimit              → limitReservationId
  step7  createTuitionOrder        → orderNumber
  step7b Razorpay payment API      ← direct Razorpay test-mode call
  step8  SSE stream                → LINK_CREATED → inviteCode → TestContext
  step9  consumeReservedLimit

TuitionOnboardingTest              ← reads inviteCode from TestContext
  step1  sendOtp(inviteCode)
  step2  verifyOtp                 → hashiraToken
  step3  getPayeeDetails
  step4  payeeConsent (accept)
  step5  pollOrderStatus → ACCEPTED
```

---

## Design Patterns

### 1. Service Object Pattern
Each backend microservice maps to one Java class (`CsvcService` → C2C-Svc, `OmsService` → OMS, `OnboardingService` → Onboarding). Tests never call Rest Assured directly — they call service methods.

**Why:** Separates HTTP mechanics from test logic. If an endpoint URL changes, fix it in one place.

```java
// Test doesn't know about HTTP — just calls the service
Response r = csvc.getPayeeBasicInfo(request);
```

**Why not inheritance (BaseService)?** C2C services all hit Kong but have different auth requirements. Standalone classes with their own `buildSpec()` are simpler than overriding auth in subclasses.

---

### 2. Builder Pattern (Lombok `@Builder`)
All request DTOs use Lombok's `@Builder`. Tests construct requests with only the fields they need.

**Why:** DTOs have 6–10 fields; `@AllArgsConstructor` forces every field in order — adding a field silently breaks all callers. Builder is self-documenting.

```java
// AllArgsConstructor — which value is which?
new GeneratePaymentIntentRequest("pid-123", 1236.56, 0.54, 1234.0, 3.00, "key");

// Builder — intent is clear
GeneratePaymentIntentRequest.builder()
    .payeeDisplayId("pid-123")
    .totalPayable(1236.56)
    .idempotencyKey(UUID.randomUUID().toString())
    .build()
```

**Why not ObjectMother?** ObjectMother works well when you have a canonical "valid" object and need small variations. Here each test needs different combinations of fields — Builder is more flexible.

**Gotcha:** `@Data` + `@Builder` adds setters, making DTOs mutable. For immutable DTOs use `@Value` + `@Builder`.

---

### 3. Singleton + Static Factory (`TuitionTestData.get()`)
Test data is loaded once from JSON, shared across all test instances.

**Why:** Avoids re-parsing JSON per test method. All tests use the same values — change test data in one place.

**Why acceptable here (not in unit tests):** Unit tests need isolated state per test. Integration tests share infrastructure (same DB, same backend) — shared read-only test data is fine.

**Risk:** If two suites run in the same JVM simultaneously, the singleton is shared. Fix: make it suite-scoped using TestNG `@BeforeSuite` instead of static initializer.

```java
private final TuitionTestData data = TuitionTestData.get(); // loaded once
```

---

### 4. Blackboard Pattern (TestContext)
`TestContext` is a static store that holds `userToken`, `userId`, and a `Map<String, String>` scalar store. Any test class can write or read from it.

**Why:** TestNG creates a new instance per test class — instance fields don't survive across classes. `TestContext` carries `invite_code` and `orderNumber` from `TuitionPayerJourneyTest.step7` into `TuitionOnboardingTest.setup()`.

**Why not method arguments?** TestNG test methods don't accept arbitrary arguments between classes — only `@DataProvider` feeds data, and that's per-method, not cross-class.

**Why not a database?** Adds infrastructure dependency. For a single JVM run, in-memory is faster, simpler, and has no cleanup cost.

```java
// Writer (payer test)
TestContext.setScalarData("c2c_invite_code", inviteCode);

// Reader (onboarding test)
inviteCode = TestContext.getScalarData("c2c_invite_code");
```

**Risk:** Static state is global mutable state. Must be cleared between suite runs or tests bleed into each other.

---

### 5. Chain of Responsibility (`dependsOnMethods`)
Happy path tests are chained — each step depends on the previous. If step 3 fails, steps 4–9 are skipped automatically.

**Why:** Matches the real business flow. Also avoids cascading false failures — if `getPayeeBasicInfo` fails, you don't want 8 more failures in the report.

**Trade-off:** Long chains make individual step reruns hard. If step5 is flaky, you must rerun steps 1–4 first. For focused regression, use `dependsOnGroups` or run with `-Dc2c.invite.code=...` to skip the payer flow entirely.

---

### 6. Strategy Pattern (Per-Service Auth)
Each service has its own auth strategy:
- `CsvcService` → `Authorization: Bearer <student_token>`
- `OmsService` → `Authorization: Bearer` + `X-RequestId: UUID`
- `OnboardingService` → public spec for OTP, then `Bearer <hashira_token>` after login

**Why:** If all services extended BaseService, auth logic would need overriding in each. Standalone `buildSpec()` per class is explicit and easier to debug.

---

### 7. Token Bridge Pattern (YAML → API)
The YAML `captureToken` action writes the app JWT into `TestContext.setUserToken()`. The API `@BeforeClass` reads it with `TestContext.getUserToken()`.

**Why:** The student token is only obtainable by logging into the real app — it's not a static credential. YAML acts as a setup fixture that provisions the token the API tests need.

```
tuition_create.yaml
  - action: captureToken   ← writes to TestContext

TuitionPayerJourneyTest
  @BeforeClass setup()
    String token = TestContext.getUserToken();  ← reads from TestContext
```

---

### 8. DTO Pattern with `@JsonIgnoreProperties`
Separate request and response DTOs, with `@JsonProperty` for snake_case → camelCase mapping.

**Why `@JsonIgnoreProperties(ignoreUnknown = true)` on responses:** Tests don't break when the API adds new fields — only fields the test cares about are mapped.

**Why not `Map<String, Object>` for responses?** Type safety — field name typos caught at compile time, not runtime. `r.jsonPath().getString("data.payee_id")` fails silently with null; a typed DTO fails loudly.

---

## Auth Token Lifecycle

| Token | Source | Used By | Scope |
|---|---|---|---|
| `student access_token` | App login via YAML `captureToken` | CsvcService, OmsService | Payer journey |
| `tutor hashira_token` | `OnboardingService.verifyOtp()` response | OnboardingService (consent, payee details) | Tutor onboarding |

---

## SSE Stream Design

After `createTuitionOrder`, the Razorpay payment fires a webhook to the backend which emits a `LINK_CREATED` event on the SSE stream.

```java
// Stream MUST be opened before verifyPaymentIntent is called
CompletableFuture<OrderStreamResult> streamFuture =
    CompletableFuture.supplyAsync(() -> oms.waitForOrderLink(orderNumber, 60));

// This triggers the payment — backend fires SSE on webhook receipt
csvc.verifyPaymentIntent(paymentIntentId, request);

// Collect the event
OrderStreamResult result = streamFuture.get(65, TimeUnit.SECONDS);
```

**Why async:** `verifyPaymentIntent` triggers the event. If the stream is opened after the call returns, the event may already have fired and be missed.

---

## Interview Questions & Answers

---

### Design Patterns

**Q: Why Builder and not ObjectMother for test data?**

ObjectMother works when you have one canonical valid object with small variations (e.g., `aValidUser().withEmail("bad")`). Here, each test constructs requests with completely different field combinations — payer tests need fee breakdowns, error tests need minimal invalid data. Builder gives per-call control without a factory method per scenario.

**Q: `TuitionTestData` is a Singleton — what's the danger in test code?**

If the singleton holds mutable state, test A's changes leak into test B. Here it's read-only after construction, so it's safe. The real risk is parallel suites sharing the same JVM — both read the same JSON, which is fine for read-only data but would break if the singleton cached dynamic state like tokens.

**Q: Services are stateful (hold `accessToken`) — what problem does that cause?**

If you instantiate `CsvcService` once and reuse it across test classes, the token from class A is still set when class B runs. If class B needs a different token (e.g., a different user), it silently uses the wrong one. Fix: instantiate services in `@BeforeClass`, not as static fields.

**Q: Why not extend BaseService?**

`BaseService.assertStatus()` is static — any class can call it without extending. The only reason to extend BaseService is to inherit `buildSpec()`. But C2C services use Kong base URL + Bearer auth, while card services use a different base URL + X-Auth-Token. Sharing `buildSpec()` would mean overriding it in every subclass — net negative. Standalone classes are explicit.

---

### TestNG

**Q: Why `@BeforeClass` and not `@BeforeSuite` for token setup?**

`@BeforeSuite` runs once for the entire suite — the token would need to be set before any test class loads. If the YAML step hasn't run yet (different suite block), the token is null. `@BeforeClass` runs just before the test class executes, by which time the YAML step has already written the token to `TestContext`. It also means each class can assert the token exists independently.

**Q: If `@BeforeClass setup()` throws `AssertionError`, what happens?**

All `@Test` methods in that class are skipped (not failed). TestNG marks them as `SKIP` with the cause being the `@BeforeClass` failure. This is why asserting the token in `setup()` is important — without it, every test would fail with a `NullPointerException` instead of a clear skip message.

**Q: `parallel="methods"` — what breaks?**

`step2` depends on `step1` writing `payeeId`. If they run in parallel, `step2` runs before `payeeId` is set — `NullPointerException`. `dependsOnMethods` enforces sequential execution within a class even with `parallel="methods"`, but across classes the TestContext scalar data written by `TuitionPayerJourneyTest` may not be visible to `TuitionOnboardingTest` if they run concurrently.

**Q: `dependsOnMethods` vs `dependsOnGroups` — when to use which?**

`dependsOnMethods` is fine for a linear chain within one class. `dependsOnGroups` is better for cross-class dependencies — e.g., `TuitionOnboardingTest` depending on group `tuition-payer` instead of a specific method name. It's more resilient to method renames and expresses intent at the business level.

**Q: What is `IRetryAnalyzer` and when should you NOT use it here?**

`IRetryAnalyzer` reruns a failed test automatically. You should NOT use it in a payment flow because the steps are not idempotent — retrying `createTuitionOrder` may create a duplicate order, retrying `reserveLimit` may double-reserve. Only use retry on truly stateless, read-only tests like `getPayeeList`.

**Q: `@DataProvider` vs Singleton for test data — what does `@DataProvider` give you?**

`@DataProvider` feeds multiple rows of data into a single test method — the test runs once per row. That's useful for parameterized negative cases (test 5 different invalid VPAs). Singleton gives you one set of values shared across all methods — useful when all methods use the same tutor's data. You'd combine both: Singleton for shared config, `@DataProvider` for parameterized error cases.

---

### Playwright

**Q: Playwright vs Selenium — three biggest architectural differences?**

1. **Protocol:** Selenium uses WebDriver (HTTP-based, one command per round trip). Playwright uses CDP/WebSocket — persistent connection, lower latency, bidirectional.
2. **Auto-wait:** Playwright waits for elements to be actionable before interacting. Selenium requires explicit `WebDriverWait`. Playwright's auto-wait eliminates most flaky `NoSuchElementException` errors.
3. **Browser contexts:** Playwright has `BrowserContext` — an isolated session (cookies, storage, auth) within one browser process. You can run multiple independent sessions in parallel without launching multiple browsers.

**Q: How does Playwright auto-wait work and when does it fail?**

Before every action (click, fill, etc.) Playwright checks: is the element attached to DOM? Visible? Not disabled? Not obscured? Stable (not animating)? If all pass, it acts. It fails when an element passes all checks but the app hasn't finished processing a previous action — e.g., clicking Submit while the network request is still in flight. Fix: `page.waitForResponse()` or `page.waitForLoadState('networkidle')`.

**Q: How would you capture the `hashira_token` from the verify-OTP response in Playwright?**

```javascript
const [response] = await Promise.all([
  page.waitForResponse(r =>
    r.url().includes('/verify-otp') && r.request().method() === 'POST'
  ),
  page.click('#verify-otp-button')
]);
const body = await response.json();
const hashiraToken = body.data.token;
```

`Promise.all` is critical — you set up the listener before clicking so you don't miss the response if it fires before `waitForResponse` is called.

**Q: How would you skip OTP login on every Playwright test using `storageState`?**

Run login once, save session:
```javascript
// global-setup.ts
const context = await browser.newContext();
const page = await context.newPage();
// ... perform OTP login ...
await context.storageState({ path: 'tutor-auth.json' });
```
Reuse in tests:
```javascript
// playwright.config.ts
use: { storageState: 'tutor-auth.json' }
```
Playwright restores cookies and localStorage — the app sees the tutor as already logged in.

**Q: How do you pass `invite_code` from the Java suite to a Playwright test?**

Three options, in order of preference:
1. **Environment variable:** Java writes `invite_code` to a temp file or env var; Playwright reads `process.env.INVITE_CODE`.
2. **Shared JSON file:** Java writes `{ "inviteCode": "xxx" }` to a known path; Playwright reads it in a fixture.
3. **Test API call:** Playwright makes an API call to get a fresh invite code directly, bypassing the Java suite entirely for standalone runs.

**Q: What is `test.describe.serial()` in Playwright?**

By default Playwright runs tests in parallel. `test.describe.serial()` forces tests within a block to run sequentially and in order — like TestNG's `dependsOnMethods`. Use it for flows where step N produces state step N+1 needs. Without it, parallel execution would break the tutor onboarding flow the same way it breaks the TestNG chain.

**Q: How would you listen to an SSE stream in Playwright?**

```javascript
const eventData = [];
await page.route('**/orders/*/stream', async route => {
  // Can't easily intercept SSE via route — use evaluate instead
});

// Better: use page.evaluate to attach an EventSource
const linkCreated = await page.evaluate(() => {
  return new Promise((resolve) => {
    const es = new EventSource('/c2c/oms/v1/orders/ORD-123/stream');
    es.onmessage = (e) => {
      const data = JSON.parse(e.data);
      if (data.status === 'LINK_CREATED') {
        es.close();
        resolve(data.invite_code);
      }
    };
  });
});
```

---

### Senior / Cross-cutting

**Q: API test vs UI test for the same flow — when do you choose which?**

API tests are faster, more stable, and test the contract directly. UI tests validate the full user experience including rendering, navigation, and JavaScript behaviour. For the tutor onboarding: test the happy path via API (fast regression), test the UI once for the OTP entry screen and consent page (catches CSS bugs, button states). Rule of thumb: if the bug would only appear in a browser, use UI. If the bug is in the backend, use API.

**Q: Suite takes 8 minutes — how to cut to 3 without removing coverage?**

1. **Parallelize independent classes** — `TuitionPayerJourneyTest` and a second user's payer journey can run simultaneously.
2. **Mock Razorpay** — the Razorpay payment + webhook round trip is the slowest step. In SIT, use Razorpay test mode with a pre-configured webhook rather than waiting for real webhook delivery.
3. **Skip YAML login if token cached** — if `TestContext` already has a valid token from a previous run (stored in a file), skip the Android login step.
4. **Reduce SSE timeout** — 60s is conservative. In SIT, webhook arrives in <5s. Set timeout to 15s.

**Q: How do you handle test data cleanup between runs?**

1. **Idempotent test data:** Use a fixed tutor mobile number that maps to the same payee — multiple runs reuse the same payee record.
2. **Order cleanup:** Add a `@AfterSuite` that calls an internal admin API to cancel/delete orders created by the test run.
3. **Invite code uniqueness:** Each `createTuitionOrder` generates a new `invite_code` — no cleanup needed since codes are single-use.
4. **DB seed scripts:** For full reset, maintain a SQL seed that restores the SIT DB to a known state before a full suite run.

---

## Staff Test Engineer Interview Questions & Answers

---

### Strategy & Vision

**Q: How do you decide what NOT to automate?**

Automate when the test is stable, repeatable, and run frequently. Don't automate:
- One-time exploratory tests — cost exceeds value
- Tests that require human judgment (visual design, UX feel)
- Flaky flows where the app isn't ready (automating instability just creates noise)
- Tests that change every sprint — maintenance cost exceeds savings

For the tuition flow: Razorpay's 3DS OTP screen is not automated — it's a third-party UI that changes without notice. API-level verification covers the same risk cheaper.

---

**Q: How do you build a test strategy for a greenfield payment product from scratch?**

1. **Risk map first** — identify what failure costs most (payment double-charge, wrong amount, failed payout). Those get the deepest coverage.
2. **Layer the pyramid** — unit tests own business logic (fee calculation, rounding), integration tests own the API contracts, E2E owns the critical user journeys only.
3. **Contract testing early** — before services are built, agree on request/response shapes via Pact or OpenAPI. Prevents integration surprises.
4. **Shift left on auth** — token flows, expiry, invalid auth should be tested at the service level, not just E2E.
5. **Observability as a test signal** — wire test runs to production-equivalent logs/metrics so failures surface in dashboards, not just CI.

---

**Q: Your E2E suite takes 45 minutes in CI. Engineering wants to merge quickly. What do you do?**

Don't just make it faster — make it *smarter*:
- **Tiered pipelines:** PR gate runs smoke (5 min), full E2E runs post-merge on main.
- **Impact analysis:** Only run tests for services touched by the PR. A change to `CsvcService` doesn't need to run `OnboardingService` tests.
- **Parallelize:** Identify independent test classes and run them concurrently. Payment journey and order management can run in parallel with independent test data.
- **Quarantine flaky tests:** Remove them from the gate immediately, fix in a separate track. One flaky test that reruns 3× costs more than the test is worth.
- **API over UI:** Replace UI steps with API calls wherever the test isn't validating UI. Login via API token instead of YAML automation cuts 2–3 minutes per suite.

---

**Q: How do you measure test suite health over time?**

| Metric | What it tells you |
|---|---|
| Flakiness rate per test | Which tests need quarantine |
| Mean time to detection (MTTD) | How fast tests catch a regression |
| Test coverage by risk tier | Are critical paths covered? |
| False positive rate | Tests failing for infra reasons, not bugs |
| Suite duration trend | Is the suite growing uncontrolled? |
| Defect escape rate | Bugs reaching production that tests missed |

Flakiness rate is the most actionable — a test that fails 20% of the time for non-product reasons is worse than no test (it trains engineers to ignore failures).

---

### Architecture & Design

**Q: How would you design a test framework for a microservices architecture where 8 services interact?**

Key principles:
1. **Service-level contract tests** (Pact/OpenAPI) — each service owns its contract independently. Consumer-driven contracts prevent breaking changes silently.
2. **Shared test infrastructure library** — auth helpers, response assertions, test data builders published as an internal Maven/npm package. Teams don't duplicate boilerplate.
3. **Environment isolation** — each service has a sandbox that can run in isolation (with mocks for dependencies) AND in integration mode (real dependencies).
4. **Centralized test data management** — a test data service or seed scripts that provision known state before tests run, instead of tests creating their own data.
5. **Distributed tracing in tests** — every test call carries a `X-Test-Run-Id` header. When something fails, you trace the entire request chain across all 8 services instantly.

---

**Q: What is contract testing and when does it beat E2E testing?**

Contract testing verifies the API contract between a consumer and provider independently — without running both together. Pact is the standard tool.

```
Consumer (CsvcService) publishes:
  "I send POST /c2c/payee/basic-info with { payment_option, vpa }
   and expect { data: { payee_id, name } }"

Provider (C2C-Svc backend) verifies:
  "Does my actual implementation satisfy this contract?"
```

Beats E2E when:
- Services are developed by different teams on different schedules
- E2E environment is unstable or expensive to maintain
- You want to catch breaking API changes before deployment, not after integration

Doesn't replace E2E — contracts verify shape, not business logic or real data flows.

---

**Q: How do you handle test data in a shared SIT environment where 5 teams are running tests simultaneously?**

1. **Namespaced test data** — each team's test users have a prefix (`forge_tuition_`, `forge_card_`). Queries are scoped to that namespace.
2. **Data leasing** — a test data service issues a "lease" on a test account for the duration of a run. No two runs use the same account simultaneously.
3. **Idempotent setup** — tests check if preconditions are already met before creating data. Don't create a tutor profile if one already exists for that mobile.
4. **Cleanup contracts** — every test that creates data registers a cleanup hook. Cleanup runs whether the test passes or fails.
5. **Read-only shared fixtures** — reference data (consent types, categories) is shared and never modified by tests.

---

**Q: How do you design for testability at the architecture review stage?**

Push these in design reviews:
- **Correlation IDs on every request** — makes log tracing from test failures trivial
- **Test mode flags on third-party integrations** — Razorpay test keys, SMS OTP bypass for SIT
- **Admin/internal APIs for state inspection** — a `GET /internal/orders/{id}/full-state` endpoint that tests can use without parsing 5 different APIs
- **Deterministic IDs in test mode** — `idempotency_key` seeded from test run ID so reruns don't create duplicate records
- **Event emission** — if the backend emits events (Kafka, SSE, webhooks), tests can subscribe and assert on events instead of polling

---

### People & Process

**Q: A senior developer says "we don't need automated tests, we move fast." How do you respond?**

Don't argue — show data. Ask:
- How long does manual regression take before each release?
- How many production incidents in the last quarter were regressions?
- What's the cost of a payment bug in production vs catching it in CI?

Then frame automation as a speed enabler, not a quality gate. "With automated contract tests on the payment flow, you can merge payment changes without a 2-hour manual regression cycle." Make the value tangible and tied to their pain.

---

**Q: How do you grow junior test engineers on your team?**

1. **Start with code review** — pair on reviewing test PRs before writing. Reading good and bad tests builds taste.
2. **Own a service** — assign each junior a single service to own end-to-end: write tests, maintain them, monitor flakiness. Ownership builds judgment.
3. **Teach the why** — explain why `dependsOnMethods` instead of `Thread.sleep()`, why Builder instead of constructors. Juniors who understand principles make good decisions in new situations.
4. **Rotate into dev sprints** — junior QEs embedded in feature teams see requirements before code is written. They start thinking about testability upfront.
5. **Flaky test ownership** — assigning a junior to investigate a flaky test is the best learning exercise. They touch CI, logs, timing, async — the whole stack.

---

**Q: How do you push back on unrealistic test coverage demands from leadership?**

Translate coverage into risk:
- "100% line coverage on the payment service sounds good. But 40% of those lines are error handling paths that require mocking infrastructure failures — that's 3 weeks of work for scenarios that have never occurred in production."
- Propose **risk-based coverage**: critical paths (happy path, payment failure, invalid auth) at 100%, edge cases at 70%, internal admin APIs at 30%.
- Show the diminishing returns curve — the last 20% of coverage often costs as much as the first 80% and catches proportionally fewer bugs.

---

### Technical Depth

**Q: What is mutation testing and when would you use it on this suite?**

Mutation testing automatically introduces small bugs (mutations) into production code — changing `==` to `!=`, removing a null check — and checks whether your tests catch them. A test suite that doesn't catch a mutation is an incomplete test.

Use it on:
- Fee calculation logic (rounding, GST) — mutations in arithmetic are exactly what automated tests should catch
- Auth validation — ensure tests actually fail when a token is missing, not just when the request is wrong

Don't use it on:
- Integration tests that hit real APIs — mutations in HTTP client code produce network errors, not meaningful signal
- YAML-driven tests — mutation testing is for unit/integration level

Tools: PIT (Java), Stryker (JS/TS).

---

**Q: How do you test idempotency of the payment API?**

Send the same request twice with the same `idempotency_key` and assert:
1. Both calls return 200
2. Only one order is created (check via `listOrders`)
3. The second response body is identical to the first

```java
@Test
public void paymentIntent_isIdempotent() {
    String key = UUID.randomUUID().toString();

    Response r1 = csvc.generatePaymentIntent(
        GeneratePaymentIntentRequest.builder()
            .idempotencyKey(key).build());
    Response r2 = csvc.generatePaymentIntent(
        GeneratePaymentIntentRequest.builder()
            .idempotencyKey(key).build());

    assertEquals(r1.jsonPath().getString("data.payment_intent_id"),
                 r2.jsonPath().getString("data.payment_intent_id"),
                 "Idempotent calls must return the same intent ID");
}
```

---

**Q: How do you test the SSE stream for error conditions?**

| Scenario | How to test |
|---|---|
| Payment failed | Mock Razorpay webhook to send failure — assert stream emits `PAYMENT_FAILED` |
| Stream timeout | Don't call `verifyPaymentIntent` — assert `waitForOrderLink` returns `TIMEOUT` after N seconds |
| Connection drop | Close the stream mid-read — assert the client reconnects or fails gracefully |
| Out-of-order events | Send `LINK_CREATED` before `PAYMENT_SUCCESS` — assert the test handles both statuses |
| Auth expired mid-stream | Expire the token while stream is open — assert 401 is handled, not swallowed |

---

**Q: What's your approach to testing race conditions in a distributed system?**

Race conditions in tests require concurrency:

```java
@Test
public void reserveLimit_concurrent_onlyOneSucceeds() throws Exception {
    int threads = 5;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    List<Future<Integer>> futures = new ArrayList<>();

    for (int i = 0; i < threads; i++) {
        futures.add(executor.submit(() ->
            csvc.reserveLimit(ReserveLimitRequest.builder()
                .paymentIntentId(paymentIntentId)
                .category("TUITION")
                .build()).statusCode()
        ));
    }

    long successCount = futures.stream()
        .map(f -> { try { return f.get(); } catch (Exception e) { return 500; }})
        .filter(code -> code == 200)
        .count();

    assertEquals(successCount, 1, "Only one concurrent reserve should succeed");
}
```

Also use tools like Gatling for sustained concurrency and observing how the system degrades.

---

**Q: How do you enforce test quality standards across multiple teams?**

1. **Shared linting rules** — Checkstyle/PMD rules that flag `Thread.sleep`, hardcoded credentials, missing assertions, empty catch blocks in tests.
2. **Test review checklist** in PR template — does the test assert the right thing? Does it clean up after itself? Does it have a meaningful failure message?
3. **Flakiness SLA** — any test with >5% flakiness rate in 30 days gets quarantined automatically via CI config. Team is notified and owns the fix.
4. **Coverage gates by risk tier** — CI fails if critical path coverage drops below threshold. Measured by which endpoints are exercised, not just line coverage.
5. **Inner-source the framework** — teams contribute to the shared test library via PRs. Staff engineer reviews and merges. This builds consistency and spread of knowledge.

---

**Q: How do you handle secrets (Razorpay key secret, test mobile numbers) in CI?**

1. **Never in code or properties files** — `razorpay.key.secret` goes in a secrets manager (AWS Secrets Manager, Vault, GitHub Actions secrets).
2. **Injected at runtime** — CI pipeline fetches the secret and sets it as an environment variable. `ConfigManager` reads from env var first, then falls back to properties file.
3. **Rotation** — test credentials should be rotatable without code changes. If the key is in `sit.properties` in git, rotation requires a commit.
4. **Least privilege** — Razorpay test key only works in test mode, scoped to the SIT account. If it leaks, it can't charge real money.
5. **Audit** — log which test runs used which credentials (by key prefix, not value) so you can trace if a leaked key was used maliciously.

---

**Q: What's the difference between a test pyramid and a test trophy?**

**Test Pyramid (Mike Cohn):**
```
        /E2E\          ← few, slow, expensive
       /-----\
      /  Intg  \       ← medium
     /----------\
    /    Unit    \     ← many, fast, cheap
```

**Test Trophy (Kent C. Dodds):**
```
        /E2E\
       /-----\
      / Integ  \       ← most tests here
     /----------\
    /   Unit     \     ← fewer than pyramid
   /  (Static)   \     ← type checking, linting
```

Trophy shifts emphasis to integration tests because unit tests with heavy mocking give false confidence — they test the mock, not the real behaviour. For a payment flow hitting 3 microservices, integration tests at the API level (what this suite does) give more signal than 100 unit tests with mocked HTTP clients.

---

**Q: How would you introduce test automation to a team that has none?**

1. **Don't start with E2E** — it's the hardest to maintain and the slowest to show value. Start with API contract tests on the most critical endpoint (payment creation).
2. **Pick one failing regression** — find a bug that escaped to production, write a test that catches it. Ship that test. Now the team has seen automation prevent a real bug.
3. **Make the CI green and keep it green** — a red CI that nobody fixes is worse than no CI. Start small, stay green.
4. **Make it easy to contribute** — good test utilities, clear examples, documented patterns. If writing a new test takes 30 minutes of boilerplate, nobody will write tests.
5. **Celebrate catches** — when automation catches a bug before production, make it visible. "This test saved us from a payment regression" changes the culture faster than any mandate.
