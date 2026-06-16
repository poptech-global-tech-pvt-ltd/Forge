# Forge — Claude Code Instructions

## What is Forge

Forge is a YAML-driven mobile test automation framework for the POP Android app.
Tests are written as YAML step files and run via Appium + TestNG.

## Run a test
```bash
mvn test                                                        # all tests
mvn test -DtestFile=ts_shop_checkout.yaml                       # single test
mvn test -DtestFile=shop_cart_remove.yaml,ts_shop_checkout.yaml # multiple tests
```

## Build
```bash
mvn compile       # compile only
mvn test-compile  # compile including test sources
```

## Key directories
| Path | Purpose |
|---|---|
| `src/test/resources/testdata/` | YAML test files |
| `src/test/resources/elements/` | Element key → accessibilityId mappings |
| `src/main/java/com/popclub/mobile/actions/` | Action implementations |
| `src/main/java/com/popclub/heal/` | Self-healing engine |
| `src/main/java/com/popclub/ai/` | Tag analysis, test generation helpers |
| `reports/` | Screenshots, videos, qa-tag-report |

## Skills

| Skill | What it does |
|---|---|
| `/generate-test "description"` | Generate a Forge YAML test from plain English |

## YAML test format — quick reference
```yaml
testName: My Test
platform: android
noReset: true
loginRequired: true
features:
  - common
  - shop
tags:
  - smoke
retry: 1
steps:
  - action: launchApp
  - action: loginIfNeeded
    value: "1234561122"
    text: "560102"
  - action: waitFor
    element: home_tab
  - action: tap
    element: common_shop_tab
```

## Available actions
`launchApp` `loginIfNeeded` `tap` `tapIfPresent` `waitFor` `enterText`
`pressKey` `verifyElement` `scanTags` `captureText` `assertStoredText`
`scrollDown` `scrollUp` `pullToRefresh` `tapByText` `verifyCLP`
`verifyCLPRedirects` `verifyCLPFilters`

## Element keys
Element keys live in `src/test/resources/elements/*.yaml`.
Use the **key name** (left side) in test steps, never the raw accessibilityId.

```yaml
# elements/shop.yaml
shop_search_button:
  android:
    - type: accessibilityId
      value: shop_search_button
```

```yaml
# test step
- action: tap
  element: shop_search_button   # ← use this
```
