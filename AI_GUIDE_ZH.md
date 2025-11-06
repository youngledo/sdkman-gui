# SDKMAN 项目规则与指南

## 项目概述

这是一个基于 SDKMAN 的跨平台 GUI 应用程序，使用 JavaFX 25、Maven 4.0 和 JDK 25 构建。

**设计灵感**: Applite (macOS 版 Homebrew GUI)

**核心功能**:
- 侧边栏导航：首页、JDK、SDK 页面
- 使用 AtlantaFX 主题的现代 UI
- 完整的国际化支持（中文和英文）
- 基于 JavaFX Task 框架的异步操作

## 技术栈

- **JDK**: 25
- **JavaFX**: 25.0.1
- **Maven**: 4.0
- **UI 框架**: AtlantaFX 2.1.0 (Primer Light/Dark 主题)
- **进程执行**: Apache Commons Exec 1.5.0（带 60 秒超时保护）
- **后端**: 通过 bash 命令封装 SDKMAN CLI
- **日志**: SLF4J + Logback
- **JSON**: Jackson 2.18.2

## 架构

```
src/main/java/io/sdkman/
├── App.java                    # 应用程序主入口
├── controller/                 # FXML 控制器（MVC 模式）
│   ├── MainController.java    # 主窗口侧边栏导航
│   ├── HomeController.java    # 首页统计信息
│   ├── JdkController.java     # JDK 管理页面
│   └── SdkController.java     # SDK 浏览页面
├── model/                      # 数据模型（Sdk、SdkVersion 等）
├── service/                    # 业务逻辑层
│   ├── SdkmanCliWrapper.java  # SDKMAN CLI 命令封装
│   └── SdkManagerService.java # 带异步 Task 支持的单例服务
└── util/                       # 工具类
    ├── I18nManager.java        # 国际化管理器
    ├── ConfigManager.java      # 配置管理
    ├── PlatformDetector.java   # 平台检测工具
    └── ThreadManager.java      # 线程管理器

src/main/resources/
├── fxml/                       # FXML 视图文件
├── css/                        # 自定义样式表
└── i18n/                       # 国际化资源
    ├── messages.properties     # 英语（默认）
    └── messages_zh_CN.properties  # 简体中文
```

## 🚨 关键开发规则 🚨

### 1. 国际化 (I18n) - 必须遵循

**黄金规则：所有面向用户的文本必须使用 I18nManager。禁止硬编码字符串！**

#### 4 步国际化检查清单（每个新功能强制执行）

**第1步：定义 i18n 键**
```properties
# messages.properties (英语)
home.welcome=Welcome to SDKMAN
jdk.action.install=Install

# messages_zh_CN.properties (中文)
home.welcome=欢迎使用SDKMAN
jdk.action.install=安装
```

**第2步：FXML 必须有 fx:id，禁止硬编码文本**
```xml
<!-- ❌ 错误 -->
<Label text="欢迎使用SDKMAN"/>
<Button text="安装"/>

<!-- ✅ 正确 -->
<Label fx:id="welcomeLabel"/>
<Button fx:id="installButton"/>
```

**第3步：控制器实现**
```java
@FXML private Label welcomeLabel;
@FXML private Button installButton;

@FXML
public void initialize() {
    setupI18n();
    // 其他初始化代码
}

private void setupI18n() {
    if (installButton != null) {
        installButton.setText(I18nManager.get("version.action.install"));
    }
}
```

**第4步：提交前验证**
```bash
# 检查 FXML 中的硬编码中文
grep -r "text=\"[^\"]*[\u4e00-\u9fa5]" src/main/resources/fxml/

# 检查 Java 中的硬编码中文 setText()
grep -r "setText(\"[^\"]*[\u4e00-\u9fa5]" src/main/java/

# 如果有任何输出，立即修复！
```

#### I18n 键命名约定

使用分层的点分隔结构：
```
模块.组件.功能

示例:
- home.welcome              # 首页欢迎标题
- home.stat.jdk            # 首页 JDK 统计标签
- home.action.browse_jdk   # 首页浏览 JDK 按钮动作
- jdk.action.install       # JDK 页面安装动作
- message.error            # 错误消息
- settings.theme.dark      # 设置主题深色选项
```

动作按钮：`模块.action.动词`
标签：`模块.label`
消息：`message.类型`

#### 带占位符的动态内容

```properties
message.installed=成功安装 {0} {1}
```

```java
String msg = MessageFormat.format(
    I18nManager.get("message.installed"),
    "java",
    "21.0.0"
);
```

### 2. 代码风格与约定

#### 使用现代 Java 25 特性

**重要**：本项目目标为 JDK 25。始终使用现代语法特性，而不是旧版方法。

**✅ 使用的现代 Java 特性：**

1. **instanceof 的模式匹配** (JDK 16+)
```java
// ✅ 好 - 现代模式匹配
if (obj instanceof String str) {
    return str.toUpperCase();
}

// ❌ 差 - 旧式强制转换
if (obj instanceof String) {
    String str = (String) obj;
    return str.toUpperCase();
}
```

2. **Switch 表达式** (JDK 14+)
```java
// ✅ 好 - 带箭头语法的 switch 表达式
String message = switch (status) {
    case "installed" -> I18nManager.get("jdk.status.installed");
    case "default" -> I18nManager.get("jdk.status.default");
    case "not_installed" -> I18nManager.get("jdk.status.not_installed");
    default -> I18nManager.get("jdk.status.unknown");
};

// ✅ 好 - 简单条件的简洁 if-return
private static String detectOS(String os) {
    if (os.contains("win")) return "windows";
    if (os.contains("mac")) return "darwin";
    if (os.contains("nux")) return "linux";
    return "universal";
}

// ❌ 差 - 旧式 switch 语句
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

3. **文本块** (JDK 15+)
```java
// ✅ 好 - 多行字符串的文本块
String command = """
    source ~/.sdkman/bin/sdkman-init.sh && \
    sdk install java %s
    """.formatted(version);

// ❌ 差 - 字符串连接
String command = "source ~/.sdkman/bin/sdkman-init.sh && " +
                 "sdk install java " + version;
```

4. **记录** (JDK 16+)
```java
// ✅ 好 - 使用记录作为不可变数据载体
public record JdkStatistics(int installed, int available, int updateable) {}

// ❌ 差 - 冗长的 POJO 样板代码
public class JdkStatistics {
    private final int installed;
    private final int available;
    // ... getters, equals, hashCode, toString
}
```

5. **局部变量的 var** (JDK 10+) - **强烈推荐**
```java
// ✅ 好 - 类型明显时使用 var（强烈推荐）
var versions = cliWrapper.listVersions("java");
var installedCount = sdkManagerService.getInstalledJdkCount();
var os = System.getProperty("os.name").toLowerCase();

// ✅ 好 - 复杂泛型类型使用 var
var map = new HashMap<String, List<SdkVersion>>();
var future = CompletableFuture.supplyAsync(() -> calculateResult());

// ❌ 差 - 冗余的类型声明
List<SdkVersion> versions = cliWrapper.listVersions("java");
Integer installedCount = sdkManagerService.getInstalledJdkCount();
Map<String, List<SdkVersion>> map = new HashMap<String, List<SdkVersion>>();
```

6. **有序集合** (JDK 21+)
```java
// ✅ 好 - 使用 reversed() 进行反向迭代
for (var version : versions.reversed()) {
    // 反向处理
}

// ❌ 差 - 手动反转或基于索引的循环
Collections.reverse(versions);
for (var version : versions) {
    // ...
}
```

7. **带现代语法的 Stream API**
```java
// ✅ 好 - 使用 toList() (JDK 16+) 而不是 collect
List<String> vendors = versions.stream()
    .map(SdkVersion::getVendor)
    .filter(v -> v != null && !v.isEmpty())
    .distinct()
    .sorted()
    .toList();  // 更简洁

// ❌ 差 - 冗长的 collect()
List<String> vendors = versions.stream()
    .map(SdkVersion::getVendor)
    .filter(v -> v != null && !v.isEmpty())
    .distinct()
    .sorted()
    .collect(Collectors.toList());
```

8. **未命名模式和变量** (JDK 22+, 25 中预览)
```java
// ✅ 好 - 对未使用的变量使用 _
try {
    // 对未使用的变量使用 _
} catch (IOException _) {
    logger.error("IO 操作失败");
}

// 带未命名模式的模式匹配
if (obj instanceof Point(var x, _)) {  // 不关心 y
    return x;
}
```

**不要使用过时的模式**：
- ❌ 匿名内部类（使用 lambda）
- ❌ 推断有效时的显式类型参数
- ❌ 冗长的空值检查（适当时使用 Optional）
- ❌ 传统 for 循环（使用增强的 for-each 或 Stream API）

#### Java 命名
- 类：`PascalCase` (HomeController, SdkManagerService)
- 方法：`camelCase` (loadStatistics, setupI18n)
- 变量：`camelCase` (jdkCountLabel, sdkManagerService)
- 常量：`UPPER_SNAKE_CASE` (DEFAULT_LOCALE, MAX_RETRY_COUNT)

#### 中英文间距
**中英文字符之间不要有空格**（用户的明确要求）

```java
// ✅ 正确
"欢迎使用SDKMAN"

// ❌ 错误
"欢迎使用 SDKMAN"
```

#### 异常处理

**关键：仅在必要时使用 try-catch**

**❌ 错误 - 对不会抛出异常的操作过度使用 try-catch：**
```java
// 避免这种情况 - 包装不会抛出受检异常的操作
try {
    String result = someObject.toString();
    list.add(item);
    return true;
} catch (Exception e) {
    // 这是不必要的，让代码难以阅读
    return false;
}
```

**✅ 正确 - 只捕获可能发生的异常：**
```java
// 正确 - 只捕获特定的、预期的异常
try {
    String content = Files.readString(path);  // 可能抛出 IOException
    return content;
} catch (IOException e) {
    logger.error("Failed to read file: {}", path, e);
    return null;
}

// 正确 - Stream 操作不需要 try-catch
List<String> result = list.stream()
    .filter(item -> item != null)
    .map(String::toUpperCase)
    .toList();  // 不需要 try-catch
```

**异常处理指南：**

1. **只捕获受检异常** - 方法实际声明的异常
2. **优先使用特定异常** - 而不是泛型 `Exception`
3. **使用函数式流** - 更易读且更简洁
4. **不要捕获异常** - 在你的用例中不会发生的异常
5. **让运行时异常冒泡** - 除非你有特定的恢复策略

**何时使用 try-catch：**
- ✅ 文件 I/O 操作（`Files`、`FileInputStream` 等）
- ✅ 网络操作（`HttpClient`、URL 连接）
- ✅ 反射或动态类加载
- ✅ 外部进程执行
- ✅ 数据库操作

**何时不使用 try-catch：**
- ❌ 简单对象操作（`toString()`、getter/setter）
- ❌ 集合操作（`add()`、`stream()`、`toList()`）
- ❌ 字符串操作（`substring()`、`split()`）
- ❌ 基本算术或逻辑操作
- ❌ 不声明受检异常的操作

#### JavaDoc 注释

**强制要求：使用 Java 23+ Markdown 增强版 JavaDoc 注释**

从 Java 23 开始，JavaDoc 支持 Markdown 语法。所有文档注释必须使用 Markdown 格式，而不是 HTML 标签。

**✅ 正确 - 使用 Markdown (Java 23+):**
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

**❌ 错误 - 旧式 HTML JavaDoc:**
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

**Markdown JavaDoc 指南：**

1. **所有 JavaDoc 注释使用 `///` 三斜杠**
   - 每行以 `///` 开始
   - 比 `/** */` 块更清晰可读

2. **使用 Markdown 语法进行格式化：**
   - `# Heading` 表示主标题
   - `## Subheading` 表示子标题
   - `**bold**` 表示强调
   - `` `code` `` 表示内联代码
   - ` ```java ` 表示代码块
   - `- item` 表示无序列表
   - `1. item` 表示有序列表

3. **标准 JavaDoc 标签仍然有效：**
   - `@param` - 参数描述
   - `@return` - 返回值描述
   - `@throws` - 异常描述
   - `@since` - 版本信息
   - `@see` - 交叉引用

4. **双语注释（中文 + 英文）：**
   - 主要描述使用英文
   - 中文翻译在下一行
   - 中英文字符之间不要有空格

### 3. 异步操作

**所有耗时操作必须使用 JavaFX Task：**

```java
Task<Integer> task = new Task<>() {
    @Override
    protected Integer call() {
        // 后台工作
        return sdkManagerService.getInstalledJdkCount();
    }
};

task.setOnSucceeded(event -> {
    // UI 更新（在 JavaFX Application Thread 上）
    Integer count = task.getValue();
    jdkCountLabel.setText(String.valueOf(count));
});

task.setOnFailed(event -> {
    // 错误处理
    logger.error("Failed to load count", task.getException());
});

new Thread(task).start();
```

**何时使用 Task：**
- SDKMAN CLI 命令
- 文件 I/O 操作
- 网络请求
- 任何超过 50ms 的操作

### 4. 日志记录

使用 SLF4J Logger，不要使用 System.out.println：

```java
private static final Logger logger = LoggerFactory.getLogger(ClassName.class);

logger.info("Operation started");        // 正常流程
logger.warn("Potential issue");          // 警告
logger.error("Error occurred", e);       // 带异常的错误
```

### 5. 控制器通信

使用回调模式进行控制器间导航：

```java
// 在父控制器中 (MainController)
homeController.setNavigationCallback(this::navigateFromHome);

// 在子控制器中 (HomeController)
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

### 6. SDKMAN CLI 命令

所有 SDKMAN 命令必须通过 `SdkmanCliWrapper` 执行：

```bash
# 命令模板
source ~/.sdkman/bin/sdkman-init.sh && sdk <command>

# 示例
sdk list              # 列出所有候选
sdk list java         # 列出 Java 版本
sdk install java 21   # 安装 Java 21
sdk default java 21   # 设置默认 Java
```

### 7. 错误处理

始终处理成功和失败情况：

```java
try {
    // 操作
} catch (Exception e) {
    logger.error("Operation failed", e);
    // 显示用户友好的错误消息
    Platform.runLater(() -> {
        // 更新 UI 为错误状态
    });
}
```

## 开发工作流

### 推荐的实现顺序

1. ✅ CLI 封装器 (SdkmanCliWrapper, SdkManagerService)
2. ✅ 带统计的首页
3. 🚧 JDK 管理页面（当前阶段）
4. ⏳ SDK 浏览页面
5. ⏳ 设置页面

### 创建任何新功能前

1. 在两个 `.properties` 文件中定义 i18n 键
2. 创建带 `fx:id` 属性的 FXML（无硬编码文本）
3. 创建带 `@FXML` 字段和 `setupI18n()` 方法的控制器
4. 测试中文和英文环境
5. 运行验证命令检查硬编码文本

### 每次提交前

- [ ] 代码中没有硬编码中文文本
- [ ] 所有新文本都有 i18n 键（英文 + 中文）
- [ ] `mvn clean compile` 成功无错误
- [ ] 手动测试功能
- [ ] 日志语句清晰有意义
- [ ] 代码格式正确，带有 JavaDoc

## 常见反模式要避免

### ❌ 硬编码文本
```xml
<Label text="欢迎"/>  <!-- 绝对不要这样做 -->
```

### ❌ 阻塞 UI 线程
```java
// 错误 - 阻塞 UI
String result = sdkmanCliWrapper.executeCommand("sdk list");
```

### ❌ 使用 System.out
```java
System.out.println("Debug info");  // 使用 logger
```

### ❌ 忽略空值检查
```java
welcomeLabel.setText(...);  // 添加空值检查！
```

## 项目文件参考

**关键配置文件：**
- `pom.xml` - Maven 配置
- `src/main/resources/i18n/messages*.properties` - I18n 资源
- `src/main/resources/css/custom-theme.css` - 自定义样式
- `PROJECT_DESIGN.md` - 技术设计文档
- `DEVELOPMENT_CHECKLIST.md` - 详细开发检查清单

**主入口点：**
- `src/main/java/io/sdkman/App.java`

**FXML 视图：**
- `src/main/resources/fxml/main-view.fxml` - 主窗口布局
- `src/main/resources/fxml/home-view.fxml` - 首页
- `src/main/resources/fxml/jdk-view.fxml` - JDK 管理页面
- `src/main/resources/fxml/sdk-view.fxml` - SDK 浏览页面

## 有疑问时

1. 检查 `DEVELOPMENT_CHECKLIST.md` 获取详细指南
2. 检查 `PROJECT_DESIGN.md` 了解架构决策
3. 查看现有代码（例如 `HomeController.java`）获取模式
4. 提交前运行验证 grep 命令

---

**记住：每次会话开始时都会自动加载此 CLAUDE.md 文件。始终遵循这些规则以保持代码质量和一致性！**