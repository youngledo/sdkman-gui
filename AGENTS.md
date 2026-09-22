# AGENTS.md — SDKMAN GUI Development Guide

Project rules for AI coding agents. This file is loaded automatically at the start of every session; read it before modifying code.

## Project Overview

A cross-platform GUI application for SDKMAN, built with JavaFX + Maven.

**Core features**:

- Sidebar navigation: Home, JDK, SDK, and Settings pages
- Browse/install/uninstall/switch JDKs and other SDKs via the SDKMAN REST API
- AtlantaFX themes (Primer Light/Dark), Ikonli icons
- Full internationalization (Chinese/English)
- Async operations built on the JavaFX Task framework

## Tech Stack & Build Commands

| Item | Value |
|---|---|
| JDK | 27 (`<java.version>` in `pom.xml`) |
| JavaFX | 27 |
| Maven | 4.x (POM 4.1.0, CI-friendly `${revision}`) |
| UI framework | AtlantaFX 2.1.0 |
| JSON | Jackson 2.18.2 |
| Logging | SLF4J 2.0.7 + Log4j2 2.21.1 |
| Testing | JUnit 5.11.3 |
| Packaging | javafx-maven-plugin (dev run), shade + jpackage (release) |

```bash
# The build requires JDK 27 (an older JDK fails with "Illegal version number")
JAVA_HOME=<jdk27-path> mvn clean test        # compile + full test suite
JAVA_HOME=<jdk27-path> mvn javafx:run        # run the app
JAVA_HOME=<jdk27-path> mvn clean package     # package (shade produces the fat jar for jpackage)
```

## Project Structure

```
src/main/java/io/sdkman/
├── SdkmanApplication.java      # Main entry (JavaFX Application)
├── controller/                 # FXML controllers
│   ├── MainController          # Sidebar navigation (talks to child controllers via callbacks)
│   ├── HomeController           # Home statistics
│   ├── JdkController           # JDK list page (vendor grouping, filters)
│   ├── JdkDetailController     # JDK detail page
│   ├── SdkController           # SDK browse page
│   ├── SdkDetailController     # SDK detail page
│   ├── SettingsController      # Settings page
│   ├── InstallationHandler     # Install/uninstall flows
│   ├── VersionListManager      # Generic version-list management
│   └── VersionListCellFactory  # List cell rendering
├── service/
│   ├── SdkmanHttpClient        # SDKMAN REST API client + text table parsing (core)
│   ├── SdkmanService           # Business service layer
│   └── VersionUpdateService    # Version update detection
├── model/                      # Sdk, SdkVersion, JdkCategory, JdkListItem, Installable
└── util/                       # ConfigManager, I18nManager, ThemeManager,
                                # PlatformDetector, ProxyUtil, VersionComparator, ...
src/main/resources/
├── fxml/                       # Views (main/home/jdk/sdk/settings-detail, ...)
├── i18n/messages.properties    # English (default)
├── i18n/messages_zh_CN.properties # Chinese
├── css/                        # custom-theme.css, jdk-management.css
└── log4j2.xml
src/test/                       # JUnit 5 tests + resources/ (API response fixtures)
```

## Data Source: The SDKMAN API (Important)

The backend is `https://api.sdkman.io/2`, which returns **plain-text tables, not JSON**. All parsing lives in `SdkmanHttpClient`.

### Version list format drift (a pitfall we already hit)

The Java table returned by `/candidates/{candidate}/{platform}/versions/list?installed=...&current=...` has changed over time:

- **Legacy 6 columns**: `Vendor | Use | Version | Dist | Status | Identifier`
- **Current 4 columns**: `Vendor | Use | Version | Identifier` (Dist/Status columns removed)

Use-column markers: `>` in use, `*` installed, `+` local only (installed locally but delisted remotely — still counts as installed).

**Rules**:

1. Parsing must tolerate both column layouts (split on `|` and adapt by column count; see `parseJavaVersions`)
2. Java format is detected by the header row starting with `Vendor |`, not by counting pipes
3. JavaFX identifiers come in two forms: legacy `.fx` (e.g. `21.0.9.fx-zulu`) and current `-fx` (e.g. `27.0.0-fx+35-zulu`); `JdkCategory.fromIdentifier` must recognize both
4. **The API format may change again at any time.** When touching the parser, run the regression test against the real-response fixture at `src/test/resources/java-versions-api-response.txt`. If the live format changes again: curl the real response first, update the fixture, then fix the parser

```bash
# Fetch the current live response for comparison / fixture updates
# (platform values, see PlatformDetector: darwinarm64/darwinx64/linuxx64/windowsx64)
curl -s "https://api.sdkman.io/2/candidates/java/darwinarm64/versions/list?installed=21.0.10-tem&current=21.0.10-tem"
```

### Other key endpoints

- `/candidates/list`: candidate list (custom delimited text, parsed by `parseCandidates`)
- `/broker/download/{candidate}/{version}/{platform}`: SDK binary download (zip)
- Install = download, unzip into `~/.sdkman/candidates/{candidate}/{version}`, set executable permissions; `current` is a symlink to the active version
- HTTP clients must go through `ProxyUtil.createHttpClient()` (respects the user's proxy settings)

## 🚨 Critical Development Rules 🚨

### 1. Internationalization (i18n) — mandatory

**Golden rule: every user-facing string must go through `I18nManager`. No hardcoded strings!**

Four-step checklist (enforced for every new feature):

1. **Define keys**: add them to BOTH `messages.properties` (English) and `messages_zh_CN.properties` (Chinese)
2. **No hardcoded text in FXML**: use `fx:id`, never `text="..."`
3. **Controller**: `@FXML` fields + a `setupI18n()` call inside `initialize()`
4. **Verify before committing**:

```bash
grep -rn 'text="[^"]*[\u4e00-\u9fa5]' src/main/resources/fxml/   # must output nothing
grep -rn 'setText("[^"]*[\u4e00-\u9fa5]' src/main/java/           # must output nothing
```

Key naming: `module.component.feature` (e.g. `jdk.action.install`). Dynamic text uses `MessageFormat.format(I18nManager.get("key"), args...))`.

### 2. Code style (modern Java)

The target is JDK 27; **always use modern syntax**:

- `instanceof` pattern matching: `if (obj instanceof String s)` — no manual casts
- Switch expressions (arrow syntax); concise if-return for simple conditions
- Text blocks `"""` + `.formatted()` for multi-line strings
- Records for immutable data carriers
- Use `var` whenever the type is obvious (strongly recommended), including complex generics: `var map = new HashMap<String, List<SdkVersion>>();`
- Streams: `.toList()` instead of `.collect(Collectors.toList())`; use `getFirst()/reversed()` on sequenced collections
- Use `_` for unused variables: `catch (IOException _)`
- No anonymous inner classes, no legacy for loops

Naming: classes `PascalCase`, methods/variables `camelCase`, constants `UPPER_SNAKE_CASE`.

**No space between Chinese and Latin characters** (explicit user requirement): write `欢迎使用SDKMAN`, not `欢迎使用 SDKMAN`. This applies to Chinese strings in the i18n properties files and in code.

### 3. JavaDoc — Markdown syntax (Java 23+)

All doc comments use `///` triple-slash + Markdown, never the legacy `/** */` HTML style:

```java
///
/// # PlatformDetector
///
/// Platform detection utility for SDKMAN format
/// 平台检测工具类，用于SDKMAN格式
///
/// ## Supported platforms
/// - `darwinarm64` - macOS on Apple Silicon
///
/// @since 1.0
///
public class PlatformDetector { ... }
```

Write descriptions in English, with the Chinese translation on the following line (bilingual).

### 4. Async operations — never block the UI thread

Anything slower than ~50ms (API calls, file I/O, install commands) must use a JavaFX Task:

```java
Task<List<SdkVersion>> task = new Task<>() {
    @Override
    protected List<SdkVersion> call() {
        return sdkmanService.listJdkVersions();   // background thread
    }
};
task.setOnSucceeded(e -> updateUi(task.getValue()));  // UI thread
task.setOnFailed(e -> logger.error("Load failed", task.getException()));
new Thread(task).start();
```

Background threads must touch the UI only through `Platform.runLater(...)`.

### 5. Logging

Use SLF4J; never `System.out.println`:

```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);
logger.info("Operation started");
logger.warn("Potential issue: {}", detail);
logger.error("Error occurred", e);
```

### 6. Exception handling — restraint

Only catch checked exceptions that can actually occur (file I/O, network, external processes, reflection); prefer specific exception types. Simple object/collection/string operations and stream pipelines need no try-catch; let runtime exceptions bubble up when there is no recovery strategy.

### 7. Controller communication

Child controllers talk to `MainController` through the callback pattern (`setNavigationCallback(Consumer<String>)`); they never hold a direct reference to the parent controller.

## Pre-commit Checklist

- [ ] No hardcoded user-visible text; every new string has both English and Chinese i18n keys
- [ ] `JAVA_HOME=<jdk27> mvn clean test` passes
- [ ] Manually verified the UI in both English and Chinese
- [ ] If API parsing was touched: the real-response fixture regression test passes
- [ ] Logging is clear, no System.out
- [ ] Comments follow the `///` Markdown JavaDoc bilingual style
