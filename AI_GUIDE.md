# SDKMAN Project Rules & Guidelines

## Project Overview

This is a cross-platform GUI application for SDKMAN, built with JavaFX 25, Maven 4.0, and JDK 25.

**Design Inspiration**: Applite (Homebrew GUI for macOS)

**Key Features**:
- Sidebar navigation: Home, JDK, SDK pages
- Modern UI with AtlantaFX theme
- Full internationalization support (Chinese & English)
- Async operations with JavaFX Task framework

## Technology Stack

- **JDK**: 25
- **JavaFX**: 25.0.1
- **Maven**: 4.0
- **UI Framework**: AtlantaFX 2.1.0 (Primer Light/Dark themes)
- **Backend**: Retrieve data by calling the SDKMAN API via HttpClient.
- **Logging**: SLF4J + Log4j2
- **JSON**: Jackson 2.18.2

## Architecture

Code Structure

## 🚨 CRITICAL DEVELOPMENT RULES 🚨

### 1. Internationalization (I18n) - MUST FOLLOW

**Golden Rule: ALL user-facing text MUST use I18nManager. NO hardcoded strings!**

#### 4-Step I18n Checklist (MANDATORY for every new feature)

**Step 1: Define i18n keys**
```properties
# messages.properties (English)
home.welcome=Welcome to SDKMAN
jdk.action.install=Install

# messages_zh_CN.properties (Chinese)
home.welcome=欢迎使用SDKMAN
jdk.action.install=安装
```

**Step 2: FXML must have fx:id, NO hardcoded text**
```xml
<!-- ❌ WRONG -->
<Label text="欢迎使用SDKMAN"/>
<Button text="安装"/>

<!-- ✅ CORRECT -->
<Label fx:id="welcomeLabel"/>
<Button fx:id="installButton"/>
```

**Step 3: Controller implementation**
```java
@FXML private Label welcomeLabel;
@FXML private Button installButton;

@FXML
public void initialize() {
    setupI18n();
    // other initialization
}

private void setupI18n() {
    if (installButton != null) {
        installButton.setText(I18nManager.get("version.action.install"));
    }
}
```

**Step 4: Verify before commit**
```bash
# Check for hardcoded Chinese in FXML
grep -r "text=\"[^\"]*[\u4e00-\u9fa5]" src/main/resources/fxml/

# Check for hardcoded Chinese in Java setText()
grep -r "setText(\"[^\"]*[\u4e00-\u9fa5]" src/main/java/

# If any output appears, FIX IT IMMEDIATELY!
```

#### I18n Key Naming Convention

Use hierarchical dot-separated structure:
```
模块.组件.功能

Examples:
- home.welcome              # Home page welcome title
- home.stat.jdk            # Home page JDK stat label
- home.action.browse_jdk   # Home page browse JDK button action
- jdk.action.install       # JDK page install action
- message.error            # Error message
- settings.theme.dark      # Settings theme dark option
```

Action buttons: `module.action.verb`
Labels: `module.label`
Messages: `message.type`

#### Dynamic Content with Placeholders

```properties
message.installed=Successfully installed {0} {1}
```

```java
String msg = MessageFormat.format(
    I18nManager.get("message.installed"),
    "java",
    "21.0.0"
);
```

### 2. Code Style & Conventions

#### Use Modern Java 25 Features

**IMPORTANT**: This project targets JDK 25. Always use modern syntax features instead of legacy approaches.

**✅ Modern Java Features to Use:**

1. **Pattern Matching for instanceof** (JDK 16+)
```java
// ✅ GOOD - Modern pattern matching
if (obj instanceof String str) {
    return str.toUpperCase();
}

// ❌ BAD - Old style cast
if (obj instanceof String) {
    String str = (String) obj;
    return str.toUpperCase();
}
```

2. **Switch Expressions** (JDK 14+)
```java
// ✅ GOOD - Switch expression with arrow syntax
String message = switch (status) {
    case "installed" -> I18nManager.get("jdk.status.installed");
    case "default" -> I18nManager.get("jdk.status.default");
    case "not_installed" -> I18nManager.get("jdk.status.not_installed");
    default -> I18nManager.get("jdk.status.unknown");
};

// ✅ GOOD - Concise if-return for simple conditions
private static String detectOS(String os) {
    if (os.contains("win")) return "windows";
    if (os.contains("mac")) return "darwin";
    if (os.contains("nux")) return "linux";
    return "universal";
}

// ❌ BAD - Old switch statement
String message;
switch (status) {
    case "installed":
        message = I18nManager.get("jdk.status.installed");
        break;
    case "default":
        message = I18nManager.get("jdk.status.default");
        break;
    // ...
}
```

3. **Text Blocks** (JDK 15+)
```java
// ✅ GOOD - Text block for multi-line strings
String command = """
    source ~/.sdkman/bin/sdkman-init.sh && \
    sdk install java %s
    """.formatted(version);

// ❌ BAD - String concatenation
String command = "source ~/.sdkman/bin/sdkman-init.sh && " +
                 "sdk install java " + version;
```

4. **Records** (JDK 16+)
```java
// ✅ GOOD - Use records for immutable data carriers
public record JdkStatistics(int installed, int available, int updateable) {}

// ❌ BAD - Verbose POJO with boilerplate
public class JdkStatistics {
    private final int installed;
    private final int available;
    // ... getters, equals, hashCode, toString
}
```

5. **Var for Local Variables** (JDK 10+) - **STRONGLY RECOMMENDED**
```java
// ✅ GOOD - Use var when type is obvious (RECOMMENDED)
var versions = cliWrapper.listVersions("java");
var installedCount = sdkManagerService.getInstalledJdkCount();
var os = System.getProperty("os.name").toLowerCase();

// ✅ GOOD - Use var for complex generic types
var map = new HashMap<String, List<SdkVersion>>();
var future = CompletableFuture.supplyAsync(() -> calculateResult());

// ❌ BAD - Redundant type declaration
List<SdkVersion> versions = cliWrapper.listVersions("java");
Integer installedCount = sdkManagerService.getInstalledJdkCount();
Map<String, List<SdkVersion>> map = new HashMap<String, List<SdkVersion>>();
```

6. **Sequenced Collections** (JDK 21+)
```java
// ✅ GOOD - Use reversed() for reverse iteration
for (var version : versions.reversed()) {
    // process in reverse order
}

// ❌ BAD - Manual reversal or index-based loop
Collections.reverse(versions);
for (var version : versions) {
    // ...
}
```

7. **Stream API with Modern Syntax**
```java
// ✅ GOOD - Use toList() (JDK 16+) instead of collect
List<String> vendors = versions.stream()
    .map(SdkVersion::getVendor)
    .filter(v -> v != null && !v.isEmpty())
    .distinct()
    .sorted()
    .toList();  // More concise

// ❌ BAD - Verbose collect()
List<String> vendors = versions.stream()
    .map(SdkVersion::getVendor)
    .filter(v -> v != null && !v.isEmpty())
    .distinct()
    .sorted()
    .collect(Collectors.toList());
```

8. **Unnamed Patterns and Variables** (JDK 22+, Preview in 25)
```java
// ✅ GOOD - Use _ for unused variables
try {
    // operation
} catch (IOException _) {
    logger.error("IO operation failed");
}

// Pattern matching with unnamed patterns
if (obj instanceof Point(var x, _)) {  // Don't care about y
    return x;
}
```

**DO NOT use outdated patterns:**
- ❌ Anonymous inner classes (use lambdas)
- ❌ Explicit type arguments when inference works
- ❌ Verbose null checks (use Optional when appropriate)
- ❌ Traditional for loops (use enhanced for-each or Stream API)

#### Java Naming
- Classes: `PascalCase` (HomeController, SdkManagerService)
- Methods: `camelCase` (loadStatistics, setupI18n)
- Variables: `camelCase` (jdkCountLabel, sdkManagerService)
- Constants: `UPPER_SNAKE_CASE` (DEFAULT_LOCALE, MAX_RETRY_COUNT)

#### Chinese-English Spacing
**NO spaces between Chinese and English characters** (user's explicit requirement)

```java
// ✅ CORRECT
"欢迎使用SDKMAN"

// ❌ WRONG
"欢迎使用 SDKMAN"
```

#### Exception Handling

**CRITICAL: Use try-catch only when necessary**

**❌ BAD - Overusing try-catch for non-exceptional operations:**
```java
// AVOID - Wrapping operations that don't throw checked exceptions
try {
    String result = someObject.toString();
    list.add(item);
    return true;
} catch (Exception e) {
    // This is unnecessary and makes code hard to read
    return false;
}
```

**✅ GOOD - Only catch exceptions that can actually occur:**
```java
// CORRECT - Only catch specific, expected exceptions
try {
    String content = Files.readString(path);  // Can throw IOException
    return content;
} catch (IOException e) {
    logger.error("Failed to read file: {}", path, e);
    return null;
}

// CORRECT - Stream operations don't need try-catch
List<String> result = list.stream()
    .filter(item -> item != null)
    .map(String::toUpperCase)
    .toList();  // No try-catch needed
```

**Exception Handling Guidelines:**

1. **Only catch checked exceptions** that methods actually declare
2. **Prefer specific exceptions** over generic `Exception`
3. **Use functional streams** when possible - they're more readable
4. **Don't catch exceptions** that won't actually occur in your use case
5. **Let runtime exceptions bubble up** unless you have a specific recovery strategy

**When to Use try-catch:**
- ✅ File I/O operations (`Files`, `FileInputStream`, etc.)
- ✅ Network operations (`HttpClient`, URL connections)
- ✅ Reflection or dynamic class loading
- ✅ External process execution
- ✅ Database operations

**When NOT to Use try-catch:**
- ❌ Simple object operations (`toString()`, getters/setters)
- ❌ Collection operations (`add()`, `stream()`, `toList()`)
- ❌ String operations (`substring()`, `split()`)
- ❌ Basic arithmetic or logic operations
- ❌ Operations that don't declare checked exceptions

#### JavaDoc Comments

**MANDATORY: Use Java 23+ Markdown-Enhanced JavaDoc Comments**

Starting from Java 23, JavaDoc supports Markdown syntax. All documentation comments MUST use Markdown format instead of HTML tags.

**✅ CORRECT - Use Markdown (Java 23+):**
```java
///
/// # PlatformDetector
///
/// Platform detection utility for SDKMAN format
/// 平台检测工具类，用于SDKMAN格式
///
/// ## Usage
/// ```java
/// String platform = PlatformDetector.detectPlatform();
/// // Returns: "darwinarm64", "linuxx64", "windowsx64", etc.
/// ```
///
/// @since 1.0
///
public class PlatformDetector {

    ///
    /// Detects current platform in SDKMAN format
    /// 检测当前平台（SDKMAN格式）
    ///
    /// **Supported platforms:**
    /// - `darwinarm64` - macOS on Apple Silicon
    /// - `darwinx64` - macOS on Intel
    /// - `linuxx64` - Linux x86_64
    /// - `windowsx64` - Windows x86_64
    ///
    /// @return Platform identifier (e.g., "darwinarm64")
    ///
    public static String detectPlatform() {
        // implementation
    }
}
```

**❌ WRONG - Old HTML-style JavaDoc:**
```java
/**
 * Detects current platform in SDKMAN format
 * <p>
 * Supported platforms:
 * <ul>
 *   <li>darwinarm64 - macOS on Apple Silicon</li>
 *   <li>linuxx64 - Linux x86_64</li>
 * </ul>
 *
 * @return Platform identifier
 */
public static String detectPlatform() {
    // implementation
}
```

**Markdown JavaDoc Guidelines:**

1. **Use `///` triple-slash for all JavaDoc comments**
   - Each line starts with `///`
   - More readable and cleaner than `/** */` blocks

2. **Use Markdown syntax for formatting:**
   - `# Heading` for main titles
   - `## Subheading` for sections
   - `**bold**` for emphasis
   - `` `code` `` for inline code
   - ` ```java ` for code blocks
   - `- item` for unordered lists
   - `1. item` for ordered lists

3. **Standard JavaDoc tags still work:**
   - `@param` - Parameter description
   - `@return` - Return value description
   - `@throws` - Exception description
   - `@since` - Version information
   - `@see` - Cross-references

4. **Bilingual comments (Chinese + English):**
   - Primary description in English
   - Chinese translation on the next line
   - NO spaces between Chinese and English characters

**Example - Method with Parameters:**
```java
///
/// Installs an SDK version with progress tracking
/// 安装指定版本的SDK并跟踪进度
///
/// @param candidate SDK candidate name (e.g., "java", "maven")
/// @param version Version identifier (e.g., "21.0.0")
/// @param progressCallback Callback for progress updates
/// @return `true` if installation succeeded, `false` otherwise
/// @throws IOException if download or extraction fails
///
public boolean install(String candidate, String version,
                      ProgressCallback progressCallback) throws IOException {
    // implementation
}
```

**Example - Class Documentation:**
```java
///
/// # SdkmanService
///
/// Core service for managing SDKs via SDKMAN
/// SDKMAN核心服务，用于管理SDK
///
/// ## Features
/// - Install/uninstall SDKs
/// - Set default versions
/// - Track installation progress
/// - HTTP API integration
///
/// ## Thread Safety
/// This class is **thread-safe** and uses a singleton pattern.
///
/// @see SdkmanClient
/// @since 1.0
///
public class SdkmanService {
    // ...
}
```

**Why Markdown JavaDoc?**
- ✅ More readable in source code
- ✅ Better IDE preview rendering
- ✅ Easier to write lists, code blocks, and formatting
- ✅ Modern standard (Java 23+)
- ✅ No need to escape HTML characters

### 3. Async Operations

**All time-consuming operations MUST use JavaFX Task:**

```java
Task<Integer> task = new Task<>() {
    @Override
    protected Integer call() {
        // Background work here
        return sdkManagerService.getInstalledJdkCount();
    }
};

task.setOnSucceeded(event -> {
    // UI update here (on JavaFX Application Thread)
    Integer count = task.getValue();
    jdkCountLabel.setText(String.valueOf(count));
});

task.setOnFailed(event -> {
    // Error handling
    logger.error("Failed to load count", task.getException());
});

new Thread(task).start();
```

**When to use Task:**
- SDKMAN CLI commands
- File I/O operations
- Network requests
- Any operation > 50ms

### 4. Logging

Use SLF4J Logger, NOT System.out.println:

```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);

logger.info("Operation started");        // Normal flow
logger.warn("Potential issue");          // Warnings
logger.error("Error occurred", e);       // Errors with exception
```

### 5. Controller Communication

Use callback pattern for inter-controller navigation:

```java
// In parent controller (MainController)
homeController.setNavigationCallback(this::navigateFromHome);

// In child controller (HomeController)
private Consumer<String> navigationCallback;

public void setNavigationCallback(Consumer<String> callback) {
    this.navigationCallback = callback;
}

private void navigateToJdkPage() {
    if (navigationCallback != null) {
        navigationCallback.accept("jdk");
    }
}
```

### 6. SDKMAN CLI Commands

All SDKMAN commands must be executed via `SdkmanCliWrapper`:

```bash
# Command template
source ~/.sdkman/bin/sdkman-init.sh && sdk <command>

# Examples
sdk list              # List all candidates
sdk list java         # List Java versions
sdk install java 21   # Install Java 21
sdk default java 21   # Set default Java
```

**Process Execution with Apache Commons Exec:**

The `SdkmanCliWrapper` uses Apache Commons Exec 1.5.0 for executing bash commands:

**Why Commons Exec over ProcessBuilder:**
- ✅ Built-in timeout protection (60 seconds) prevents hanging
- ✅ Cleaner code, less boilerplate
- ✅ Better stream handling (stdout + stderr)
- ✅ Watchdog mechanism for process management
- ✅ More robust error handling

**Key features in executeCommand():**
```java
// Timeout protection
ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
    .setTimeout(java.time.Duration.ofSeconds(60))
    .get();

// Separate stdout and stderr capture
ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);

// Timeout detection
if (watchdog.killedProcess()) {
    throw new IOException("Command execution timed out");
}
```

**DO NOT:**
- Bypass SdkmanCliWrapper and use ProcessBuilder directly
- Execute SDKMAN commands without timeout protection
- Ignore the 60-second timeout limit (adjust if needed for large installations)

### 7. Error Handling

Always handle both success and failure cases:

```java
try {
    // operation
} catch (Exception e) {
    logger.error("Operation failed", e);
    // Show user-friendly error message
    Platform.runLater(() -> {
        // Update UI with error state
    });
}
```

### 8. Performance Optimization

**CRITICAL: SDK Statistics Performance**

SDKMAN has ~50 SDK candidates. Querying all of them on startup is too slow and resource-intensive.

**Implemented Strategy (Lazy Loading + Common SDKs):**

1. **Home Page Initial Load**: Only query JDK count
   - SDK count displays "--" (not loaded)
   - Update count displays "0" (feature not implemented)

2. **Manual Refresh**: User clicks "检查更新" button to load SDK statistics
   - Only queries COMMON_SDKS list (~10 items: maven, gradle, kotlin, scala, groovy, springboot, micronaut, quarkus, ant, sbt)
   - Avoids querying all ~50 candidates

3. **Why This Approach:**
   ```java
   // ❌ BAD: Queries ALL ~50 SDK candidates on startup
   List<String> candidates = cliWrapper.listCandidates(); // ~50 items
   for (String candidate : candidates) {
       cliWrapper.listVersions(candidate); // 50+ commands!
   }

   // ✅ GOOD: Only queries common SDKs when user requests
   private static final List<String> COMMON_SDKS = List.of(
       "maven", "gradle", "kotlin", "scala", "groovy",
       "springboot", "micronaut", "quarkus", "ant", "sbt"
   );
   for (String candidate : COMMON_SDKS) {
       cliWrapper.listVersions(candidate); // Only ~10 commands
   }
   ```

4. **Performance Impact:**
   - Before: 50+ `sdk list <candidate>` commands on startup
   - After: 1 `sdk list java` command on startup, 10 commands on manual refresh
   - Startup time: Fast (only JDK query)
   - User experience: Responsive, statistics available on demand

**DO NOT:**
- Query all SDK candidates on initialization
- Block UI thread with synchronous statistics loading
- Auto-refresh statistics without user action

**Implementation Reference:**
- `SdkManagerService.COMMON_SDKS` - List of common SDKs to check
- `SdkManagerService.getInstalledSdkCount()` - Only checks COMMON_SDKS
- `HomeController.loadStatistics()` - Only loads JDK count initially
- `HomeController.onCheckUpdateClicked()` - Loads all statistics on demand

**CRITICAL: JavaFX ListView Performance - Avoid Frequent Rebuilds**

When updating dynamic content in ListView (like installation progress), **NEVER** repeatedly call methods that rebuild the entire list:

**❌ BAD - Causes Performance Issues:**
```java
// Every progress update rebuilds the entire list!
task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
    jdk.setInstallProgress(newMsg);
    applyFilters();  // ❌ Rebuilds entire list, triggers all Cell updates!
    // OR
    jdkListView.refresh();  // ❌ Still refreshes all visible cells unnecessarily!
});
```

**Problem:**
- `applyFilters()` regenerates the entire list structure (grouping, filtering) - extremely expensive
- `jdkListView.refresh()` refreshes all visible Cells - wasteful for updating one label
- `ListCell.updateItem()` gets called repeatedly for ALL visible items
- Causes layout thrashing and UI stuttering

**✅ GOOD - Use JavaFX Property Binding:**
```java
// 1. Model: Use StringProperty instead of String
public class SdkVersion {
    @JsonIgnore  // Don't serialize Property itself
    private StringProperty installProgress;

    public String getInstallProgress() {
        return installProgress == null ? null : installProgress.get();
    }

    public void setInstallProgress(String progress) {
        if (this.installProgress == null) {
            this.installProgress = new SimpleStringProperty(progress);
        } else {
            this.installProgress.set(progress);  // Only sets value, no UI refresh!
        }
    }

    @JsonIgnore
    public StringProperty installProgressProperty() {
        if (this.installProgress == null) {
            this.installProgress = new SimpleStringProperty();
        }
        return this.installProgress;
    }
}

// 2. Controller: Bind Label to Property in Cell factory
private HBox createJdkVersionCell(SdkVersion jdk) {
    Label progressLabel = new Label();
    // Bind Label's text to Property - auto-updates when Property changes!
    progressLabel.textProperty().bind(jdk.installProgressProperty());
    // ...
}

// 3. Update progress without ANY ListView refresh
task.messageProperty().addListener((obs, oldMsg, newMsg) -> {
    jdk.setInstallProgress(newMsg);  // ✅ Label auto-updates via binding!
    // NO jdkListView.refresh() needed!
    // NO applyFilters() needed!
});
```

**Benefits of Property Binding:**
1. **Zero UI Refresh Cost**: Only the bound Label updates, no Cell recreation
2. **Automatic Synchronization**: Property changes immediately reflect in UI
3. **Cell Reuse Safe**: ListView's Cell reuse mechanism handles bindings correctly
4. **No Layout Thrashing**: Only the specific Label reflows, not entire Cell

**When to Use Each Approach:**
- ✅ Use `applyFilters()`: When filter criteria change (search text, status filter, vendor filter)
- ✅ Use Property Binding: For dynamic content that changes frequently (progress, status text)
- ❌ Never use `applyFilters()` or `refresh()` for rapid content updates

**Implementation Reference:**
- `SdkVersion.installProgressProperty()` - Property for installation progress
- `JdkController.createJdkVersionCell()` - Label bound to progress property
- `JdkController.installJdk()` - Updates progress via Property, no refresh calls

## Development Workflow

### Recommended Implementation Order

1. ✅ CLI Wrapper (SdkmanCliWrapper, SdkManagerService)
2. ✅ Home Page with statistics
3. 🚧 JDK Management Page (current phase)
4. ⏳ SDK Browsing Page
5. ⏳ Settings Page

### Before Creating Any New Feature

1. Define i18n keys in both `.properties` files
2. Create FXML with `fx:id` attributes (NO hardcoded text)
3. Create Controller with `@FXML` fields and `setupI18n()` method
4. Test with both Chinese and English locales
5. Run verification commands to check for hardcoded text

### Before Every Commit

- [ ] No hardcoded Chinese text in code
- [ ] All new text has i18n keys (English + Chinese)
- [ ] `mvn clean compile` succeeds with no errors
- [ ] Manually tested the feature
- [ ] Logging statements are clear and meaningful
- [ ] Code is properly formatted with JavaDoc

## Common Anti-Patterns to Avoid

### ❌ Hardcoding Text
```xml
<Label text="欢迎"/>  <!-- NEVER DO THIS -->
```

### ❌ Blocking UI Thread
```java
// WRONG - blocks UI
String result = sdkmanCliWrapper.executeCommand("sdk list");
```

### ❌ Using System.out
```java
System.out.println("Debug info");  // Use logger instead
```

### ❌ Ignoring Null Checks
```java
welcomeLabel.setText(...);  // Add null check!
```

## Project Files Reference

**Key Configuration Files:**
- `pom.xml` - Maven configuration
- `src/main/resources/i18n/messages*.properties` - I18n resources
- `src/main/resources/css/custom-theme.css` - Custom styles
- `PROJECT_DESIGN.md` - Technical design document
- `DEVELOPMENT_CHECKLIST.md` - Detailed development checklist

**Main Entry Point:**
- `src/main/java/com/sdkgui/App.java`

**FXML Views:**
- `src/main/resources/fxml/main-view.fxml` - Main window layout
- `src/main/resources/fxml/home-view.fxml` - Home page
- `src/main/resources/fxml/jdk-view.fxml` - JDK management page
- `src/main/resources/fxml/sdk-view.fxml` - SDK browsing page

## When in Doubt

1. Check `DEVELOPMENT_CHECKLIST.md` for detailed guidelines
2. Check `PROJECT_DESIGN.md` for architectural decisions
3. Look at existing code (e.g., `HomeController.java`) for patterns
4. Run the verification grep commands before committing

---

**Remember: This CLAUDE.md file is automatically loaded at the start of every session. Always follow these rules to maintain code quality and consistency!**
