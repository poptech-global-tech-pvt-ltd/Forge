You are a Principal QA Architect. Convert the Playwright codegen spec below into enterprise-grade Java Page Object Model classes for the existing Forge framework.

## Before you write anything

1. Read every file under `src/main/java/com/popclub/web/pages/` and `src/test/java/com/popclub/web/tests/`
2. Read `src/main/java/com/popclub/web/utils/` and `src/main/java/com/popclub/web/constants/`
3. For each class the spec touches, decide: **create new** or **extend existing**

## Rules

1. **Package**: all files under `com.popclub.web` — pages in `com.popclub.web.pages`, tests in `com.popclub.web.tests`
2. **Do NOT recreate** existing infrastructure: `WebBaseTest`, `PlaywrightFactory`, `ConfigReader`, `ScreenshotUtil`, `PlaywrightContext`, or any action class
3. **Existing page classes**: if a page class already exists, add only the missing locators and methods — do not rewrite the whole class. Output the full updated file.
4. **Existing test classes**: if a test class already covers the same flow, add new `@Test` methods to it rather than creating a duplicate class. Output the full updated file.
5. **Utils and constants**: if `AppConstants` or a utility already has the needed value/helper, reuse it. If a new constant or helper is genuinely needed, add it to the existing file — do not create a parallel util.
6. **Page classes** must accept `Page page` in constructor, use method chaining (return `this`), and include `isPageLoaded()` returning boolean
7. **Test classes** must extend `WebBaseTest`, use `@Test` with `description`, `groups`, and `retryAnalyzer = RetryAnalyzer.class`, and annotate each test method with `@TestCaseId("PO-XXXX")`
8. **Logging**: every class must have `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` and use `log.info(...)` for actions, `log.debug(...)` for visibility checks
9. **No ExtentReports** — do not import or reference `ExtentTestListener` or any Extent class
10. **No YAML** — pure Java POM only, no YAML test data files
11. **Selectors**: prefer `page.locator("text=...")`, `page.getByRole(...)`, `page.getByPlaceholder(...)` over raw XPath
12. Follow real user flow — do not navigate directly to intermediate URLs unless the spec does

## Spec to convert

```
$SPEC_CONTENT
```

## Output

For each file that is new or modified, output the **full file content** (not a diff):
```
// FILE: src/main/java/com/popclub/web/pages/XxxPage.java
<full file content>
```
```
// FILE: src/test/java/com/popclub/web/tests/XxxTest.java
<full file content>
```
```
// FILE: src/main/java/com/popclub/web/constants/AppConstants.java   ← only if changed
<full file content>
```
