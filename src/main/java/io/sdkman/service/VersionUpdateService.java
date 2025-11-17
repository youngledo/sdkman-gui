package io.sdkman.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sdkman.util.PlatformDetector;
import io.sdkman.util.ProxyUtil;
import io.sdkman.util.ThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

///
/// # VersionUpdateService
///
/// Service for checking application version updates from GitHub Releases
/// 应用版本更新检测服务，从GitHub Releases检查更新
///
/// ## Features
/// - Fetches latest release from GitHub API
/// - Compares semantic versions
/// - Provides update information
///
/// ## Usage
/// ```java
/// var service = VersionUpdateService.getInstance();
/// var updateInfo = service.checkForUpdates();
/// if (updateInfo.hasUpdate()) {
///     System.out.println("New version available: " + updateInfo.latestVersion());
/// }
/// ```
///
public class VersionUpdateService {
    private static final Logger logger = LoggerFactory.getLogger(VersionUpdateService.class);

    private static final String CURRENT_VERSION;
    private static final String GITHUB_API_URL = "https://api.github.com/repos/youngledo/sdkman-gui/releases/latest";

    // 静态初始化块：从MANIFEST.MF读取版本号
    static {
        var version = "1.0.0"; // 默认版本
        try {
            // 方法1：从Package获取版本信息（推荐方式，读取当前类所在jar的MANIFEST.MF）
            var pkg = VersionUpdateService.class.getPackage();
            logger.debug("Package info - Name: {}, Title: {}, Version: {}",
                    pkg != null ? pkg.getName() : "null",
                    pkg != null ? pkg.getImplementationTitle() : "null",
                    pkg != null ? pkg.getImplementationVersion() : "null");

            if (pkg != null && pkg.getImplementationVersion() != null) {
                version = pkg.getImplementationVersion();
                logger.info("Loaded application version from Package: {}", version);
            } else {
                // 方法2：备用方案 - 尝试获取当前类所在jar的URL，直接读取其MANIFEST.MF
                logger.debug("Package.getImplementationVersion() is null, trying to read MANIFEST.MF directly");
                try {
                    var classUrl = VersionUpdateService.class.getResource(
                            VersionUpdateService.class.getSimpleName() + ".class"
                    );

                    if (classUrl != null && classUrl.toString().startsWith("jar:")) {
                        // 从 jar:file:/path/to/app.jar!/com/example/Class.class 提取 jar URL
                        var jarUrl = classUrl.toString();
                        var manifestUrl = jarUrl.substring(0, jarUrl.lastIndexOf("!") + 1)
                                + "/META-INF/MANIFEST.MF";

                        logger.debug("Reading MANIFEST.MF from: {}", manifestUrl);

                        try (var input = new URI(manifestUrl).toURL().openStream()) {
                            var manifest = new java.util.jar.Manifest(input);
                            var manifestVersion = manifest.getMainAttributes().getValue("Implementation-Version");
                            if (manifestVersion != null && !manifestVersion.trim().isEmpty()) {
                                version = manifestVersion.trim();
                                logger.info("Loaded application version from MANIFEST.MF: {}", version);
                            } else {
                                logger.info("Implementation-Version not found in MANIFEST.MF, using default: {}", version);
                            }
                        }
                    } else {
                        // 在IDE中运行，不在jar包中
                        logger.info("Running in IDE development mode, using default version: {}", version);
                    }
                } catch (Exception e2) {
                    logger.debug("Failed to read MANIFEST.MF directly: {}, using default: {}", e2.getMessage(), version);
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load version: {}, using default: {}", e.getMessage(), version);
        }

        CURRENT_VERSION = version;
        logger.info("Application version initialized: {}", CURRENT_VERSION);
    }

    private static VersionUpdateService instance;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private VersionUpdateService() {
        this.httpClient = ProxyUtil.createHttpClient("VersionUpdateService");
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized VersionUpdateService getInstance() {
        if (instance == null) {
            instance = new VersionUpdateService();
        }
        return instance;
    }

    ///
    /// Checks for application updates
    /// 检查应用更新
    ///
    /// @return UpdateInfo containing update status and version information
    ///
    public UpdateInfo checkForUpdates() {
        try {
            logger.info("Checking for updates from GitHub: {}", GITHUB_API_URL);

            var requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .header("Accept", "application/vnd.github.v3+json")
                    .timeout(Duration.ofSeconds(10));

            // 可选：如果用户设置了GitHub Token，使用认证请求以提高速率限制
            // 环境变量 GITHUB_TOKEN 可以提升速率限制从 60/hour 到 5000/hour
            var githubToken = System.getenv("GITHUB_TOKEN");
            if (githubToken != null && !githubToken.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + githubToken.trim());
                logger.debug("Using GitHub token for authenticated request");
            }

            var request = requestBuilder.GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseGitHubRelease(response.body());
            } else if (response.statusCode() == 404) {
                logger.info("No releases found on GitHub");
                return new UpdateInfo(CURRENT_VERSION, CURRENT_VERSION, false, null, null, null, null);
            } else if (response.statusCode() == 403) {
                // GitHub API速率限制
                logger.warn("GitHub API rate limit exceeded: {}", response.statusCode());
                var errorMsg = "GitHub API速率限制已达上限，请稍后再试";

                // 尝试解析响应获取更详细的错误信息
                try {
                    var jsonNode = objectMapper.readTree(response.body());
                    if (jsonNode.has("message")) {
                        errorMsg = jsonNode.get("message").asText();
                    }
                } catch (Exception ignored) {
                    // 解析失败，使用默认错误消息
                }

                return new UpdateInfo(CURRENT_VERSION, CURRENT_VERSION, false, null, null, null, errorMsg);
            } else {
                logger.warn("GitHub API returned status code: {}", response.statusCode());
                return new UpdateInfo(CURRENT_VERSION, CURRENT_VERSION, false, null, null, null,
                        "GitHub API返回错误状态码: " + response.statusCode());
            }

        } catch (Exception e) {
            logger.error("Failed to check for updates", e);
            return new UpdateInfo(CURRENT_VERSION, CURRENT_VERSION, false, null, null, null, e.getMessage());
        }
    }

    ///
    /// Parses GitHub release JSON response
    /// 解析GitHub release的JSON响应
    ///
    private UpdateInfo parseGitHubRelease(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            var tagName = root.path("tag_name").asText();
            var latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            var releaseUrl = root.path("html_url").asText();
            var releaseNotes = root.path("body").asText();

            // 获取适合当前操作系统的下载URL
            var downloadUrl = findDownloadUrlForCurrentOS(root);

            var hasUpdate = compareVersions(latestVersion, CURRENT_VERSION) > 0;

            logger.info("Current version: {}, Latest version: {}, Has update: {}",
                    CURRENT_VERSION, latestVersion, hasUpdate);
            if (downloadUrl != null) {
                logger.info("Download URL: {}", downloadUrl);
            }

            return new UpdateInfo(CURRENT_VERSION, latestVersion, hasUpdate, releaseUrl, downloadUrl, releaseNotes, null);

        } catch (Exception e) {
            logger.error("Failed to parse GitHub release response", e);
            return new UpdateInfo(CURRENT_VERSION, CURRENT_VERSION, false, null, null, null, "Failed to parse response");
        }
    }

    ///
    /// Finds the appropriate download URL for the current operating system and architecture
    /// 根据当前操作系统和架构查找合适的下载URL
    ///
    private String findDownloadUrlForCurrentOS(JsonNode releaseNode) {
        try {
            var assets = releaseNode.path("assets");
            if (!assets.isArray() || assets.isEmpty()) {
                logger.warn("No assets found in release");
                return null;
            }

            // 使用PlatformDetector检测当前平台
            var isMac = PlatformDetector.isMac();
            var isWindows = PlatformDetector.isWindows();
            var isLinux = PlatformDetector.isLinux();
            var platform = PlatformDetector.detectPlatform();

            logger.debug("Detecting platform: {}", platform);

            // 提取架构信息
            var archPattern = Pattern.compile("(?:arm64|aarch64)");
            var isArm64 = archPattern.matcher(platform).find();

            logger.debug("Platform detection: mac={}, windows={}, linux={}, arm64={}",
                    isMac, isWindows, isLinux, isArm64);

            // 优先级评分系统
            var bestScore = -1;
            String bestUrl = null;
            String bestName = null;

            // 遍历assets查找最佳匹配的安装包
            for (var asset : assets) {
                var name = asset.path("name").asText().toLowerCase();
                var browserDownloadUrl = asset.path("browser_download_url").asText();

                logger.debug("Checking asset: {}", name);

                var score = calculateAssetScore(name, isMac, isWindows, isLinux, isArm64);

                if (score > bestScore) {
                    bestScore = score;
                    bestUrl = browserDownloadUrl;
                    bestName = name;
                }
            }

            if (bestUrl != null) {
                logger.info("Found best installer: {} (score: {})", bestName, bestScore);
                return bestUrl;
            }

            logger.warn("No suitable installer found for platform: {}", platform);
            return null;

        } catch (Exception e) {
            logger.error("Failed to find download URL", e);
            return null;
        }
    }

    ///
    /// Calculate asset matching score based on platform and architecture
    /// 根据平台和架构计算资源匹配分数
    ///
    /// @param name      Asset filename / 资源文件名
    /// @param isMac     macOS platform / macOS平台
    /// @param isWindows Windows platform / Windows平台
    /// @param isLinux   Linux platform / Linux平台
    /// @param isArm64   ARM64 architecture / ARM64架构
    /// @return Matching score (higher is better) / 匹配分数（越高越好）
    ///
    private int calculateAssetScore(String name, boolean isMac, boolean isWindows, boolean isLinux, boolean isArm64) {
        var score = 0;

        // 操作系统匹配（基础分100）
        if (isMac && (name.endsWith(".dmg") || name.endsWith(".pkg"))) {
            score = 100;
        } else if (isWindows && (name.endsWith(".exe") || name.endsWith(".msi"))) {
            score = 100;
        } else if (isLinux && (name.endsWith(".deb") || name.endsWith(".rpm") ||
                name.endsWith(".appimage"))) {
            score = 100;
        } else {
            return 0; // OS不匹配，直接排除
        }

        // 架构匹配（加分项50）
        if (isArm64 && (name.contains("arm64") || name.contains("aarch64") ||
                name.contains("m1") || name.contains("m2") || name.contains("m3"))) {
            score += 50;
            logger.debug("ARM64 architecture matched for asset: {}", name);
        } else if (!isArm64 && (name.contains("x64") || name.contains("x86_64") ||
                name.contains("intel") || name.contains("amd64"))) {
            score += 40; // x64架构匹配，分数稍低但也是好的匹配
            logger.debug("x64 architecture matched for asset: {}", name);
        }

        // 平台特定优化（加分项）
        if (isMac) {
            // macOS特定文件名优化
            if (name.contains("darwin") || name.contains("macos")) {
                score += 10;
            }
        } else if (isWindows) {
            // Windows特定文件名优化
            if (name.contains("win")) {
                score += 10;
            }
        } else if (isLinux && name.contains("linux")) {
            score += 10;
        }
        return score;
    }

    ///
    /// Compares two semantic versions
    /// 比较两个语义化版本号
    ///
    /// @param v1 First version (e.g., "1.2.3")
    /// @param v2 Second version (e.g., "1.2.0")
    /// @return Positive if v1 > v2, negative if v1 < v2, zero if equal
    ///
    private int compareVersions(String v1, String v2) {
        // 移除可能的v前缀
        v1 = v1.startsWith("v") ? v1.substring(1) : v1;
        v2 = v2.startsWith("v") ? v2.substring(1) : v2;

        var parts1 = v1.split("\\.");
        var parts2 = v2.split("\\.");

        var maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            var num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            var num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return Integer.compare(num1, num2);
            }
        }

        return 0;
    }

    ///
    /// Parses a version part (handles alphanumeric suffixes)
    /// 解析版本号部分（处理字母数字后缀）
    ///
    private int parseVersionPart(String part) {
        // 提取数字部分（忽略alpha/beta等后缀）
        var matcher = Pattern.compile("^(\\d+)").matcher(part);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    ///
    /// Gets current application version
    /// 获取当前应用版本
    ///
    public String getCurrentVersion() {
        return CURRENT_VERSION;
    }

    ///
    /// Download progress callback interface
    /// 下载进度回调接口
    ///
    public interface DownloadProgressCallback {
        void onProgress(long bytesDownloaded, long totalBytes, int percentage);

        void onCompleted(java.io.File downloadedFile);

        void onFailed(String errorMessage);
    }

    ///
    /// Downloads the update installer
    /// 下载更新安装包
    ///
    /// @param downloadUrl URL to download from / 下载URL
    /// @param callback    Progress callback / 进度回调
    ///
    public void downloadUpdate(String downloadUrl, DownloadProgressCallback callback) {
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            callback.onFailed("Download URL is empty");
            return;
        }
        ThreadManager.getInstance().executeTask(() -> {
            try {
                logger.info("Starting download from: {}", downloadUrl);

                var request = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .GET()
                        .build();

                var tempDir = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sdkman-gui-updates");
                java.nio.file.Files.createDirectories(tempDir);

                // 从URL提取文件名
                var fileName = downloadUrl.substring(downloadUrl.lastIndexOf('/') + 1);
                var outputFile = tempDir.resolve(fileName).toFile();

                logger.info("Downloading to: {}", outputFile.getAbsolutePath());

                // 发送请求获取输入流
                var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    callback.onFailed("HTTP " + response.statusCode());
                    return;
                }

                // 获取文件大小
                var contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                logger.info("File size: {} bytes", contentLength);

                // 下载文件
                try (var inputStream = response.body();
                     var outputStream = new java.io.FileOutputStream(outputFile)) {

                    var buffer = new byte[8192];
                    long totalBytesRead = 0;
                    int bytesRead;
                    long lastReportTime = System.currentTimeMillis();

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytesRead += bytesRead;

                        // 每500ms报告一次进度
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastReportTime > 500 || totalBytesRead == contentLength) {
                            int percentage = contentLength > 0
                                    ? (int) ((totalBytesRead * 100) / contentLength)
                                    : 0;
                            callback.onProgress(totalBytesRead, contentLength, percentage);
                            lastReportTime = currentTime;
                        }
                    }
                }

                logger.info("Download completed: {}", outputFile.getAbsolutePath());
                callback.onCompleted(outputFile);

            } catch (Exception e) {
                logger.error("Failed to download update", e);
                callback.onFailed(e.getMessage());
            }
        });
    }

    ///
    /// Opens the installer and exits the application
    /// 打开安装程序并退出应用
    ///
    /// @param installerFile Downloaded installer file / 下载的安装包文件
    /// @return true if installer was opened successfully / 如果成功打开安装程序则返回true
    ///
    public boolean installUpdate(java.io.File installerFile) {
        try {
            if (!installerFile.exists()) {
                logger.error("Installer file not found: {}", installerFile.getAbsolutePath());
                return false;
            }

            var osName = System.getProperty("os.name").toLowerCase();
            logger.info("Opening installer on {}: {}", osName, installerFile.getAbsolutePath());

            if (osName.contains("mac")) {
                // macOS: 使用open命令打开.dmg或.pkg
                var pb = new ProcessBuilder("open", installerFile.getAbsolutePath());
                pb.start();
                logger.info("macOS installer opened");
                return true;

            } else if (osName.contains("win")) {
                // Windows: 直接运行.exe或.msi
                var pb = new ProcessBuilder(installerFile.getAbsolutePath());
                pb.start();
                logger.info("Windows installer started");
                return true;

            } else if (osName.contains("linux")) {
                // Linux: 使用xdg-open打开文件
                var pb = new ProcessBuilder("xdg-open", installerFile.getAbsolutePath());
                pb.start();
                logger.info("Linux installer opened");
                return true;
            }

            logger.warn("Unsupported OS: {}", osName);
            return false;

        } catch (Exception e) {
            logger.error("Failed to open installer", e);
            return false;
        }
    }

    ///
    /// Update information record
    /// 更新信息记录
    ///
    /// @param currentVersion Current installed version / 当前安装的版本
    /// @param latestVersion  Latest available version / 最新可用版本
    /// @param hasUpdate      Whether update is available / 是否有可用更新
    /// @param releaseUrl     URL to release page / Release页面URL
    /// @param downloadUrl    URL to download installer / 安装包下载URL
    /// @param releaseNotes   Release notes / 发布说明
    /// @param errorMessage   Error message if check failed / 检查失败时的错误消息
    ///
    public record UpdateInfo(
            String currentVersion,
            String latestVersion,
            boolean hasUpdate,
            String releaseUrl,
            String downloadUrl,
            String releaseNotes,
            String errorMessage
    ) {
        public boolean isSuccess() {
            return errorMessage == null;
        }
    }

    // ==================== 辅助方法 ====================

}
