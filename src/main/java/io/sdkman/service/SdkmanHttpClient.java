package io.sdkman.service;

import io.sdkman.model.JdkCategory;
import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.util.ConfigManager;
import io.sdkman.util.PlatformDetector;
import io.sdkman.util.ProxyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

///
/// SDKMAN HTTP API客户端实现
/// 使用SDKMAN的REST API进行跨平台SDK管理
/// API文档：[sdkman-state](https://github.com/sdkman/sdkman-state)
///
public class SdkmanHttpClient implements SdkmanClient {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanHttpClient.class);

    // SDKMAN API基础URL
    private static final String API_BASE_URL = "https://api.sdkman.io/2";

    // 常量
    private static final String VENDOR_HEADER_NAME = "Vendor";

    // 正则表达式：解析Java版本表格（带|分隔符）
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("(.*?)\\|(.*?)\\|(.*?)\\|(.*?)\\|(.*?)\\|(.*)");
    // 正则表达式：解析候选列表
    // 格式: ---\n候选名称(版本)  网址\n\n描述...\n$ sdk install candidate_id\n
    private static final Pattern CANDIDATE_PATTERN = Pattern.compile(
            "---\\r*\\n(.+?)\\r*\\n\\r*\\n(.*?)\\$ sdk install(.*?)\\r*\\n",
            Pattern.MULTILINE | Pattern.DOTALL);

    private final HttpClient httpClient;

    public SdkmanHttpClient() {
        this.httpClient = ProxyUtil.createHttpClient("SdkmanHttpClient");
        logger.info("Initialized SDKMAN HTTP API client");
    }

    @Override
    public List<Sdk> listCandidates() {
        logger.info("Fetching candidates list from HTTP API");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/candidates/list"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = null;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            logger.warn("IOException!", e);
            return List.of();
        } catch (InterruptedException e) {
            logger.warn("Interrupted!", e);
            Thread.currentThread().interrupt();
            return List.of();
        }

        if (response.statusCode() != 200) {
            logger.error("Failed to fetch candidates: HTTP {}", response.statusCode());
            return new ArrayList<>();
        }

        // SDKMAN API 返回 CSV 格式，不是 JSON
        return parseCandidates(response.body());
    }

    @Override
    public List<SdkVersion> listVersions(String candidate) {
        return listVersions(candidate, true);
    }

    @Override
    public List<SdkVersion> listVersions(String candidate, boolean useProxy) {
        logger.info("=== listVersions CALLED for {} ===", candidate);

        try {
            String platform = PlatformDetector.detectPlatform();

            // 获取本地已安装的版本列表
            String installedVersions = getInstalledVersionsForCandidate(candidate);

            // 获取当前使用的版本
            String currentVersion = getCurrentVersion(candidate);
            if (currentVersion == null) {
                currentVersion = "";
            }

            logger.info("Current version from symlink: '{}'", currentVersion);
            logger.info("Installed versions: '{}'", installedVersions);

            // SDKMAN API 需要平台参数，?installed= 和 ?current= 参数告诉API哪些版本已安装和正在使用
            String url = MessageFormat.format(
                    "{0}/candidates/{1}/{2}/versions/list?installed={3}&current={4}",
                    API_BASE_URL, candidate, platform, installedVersions, currentVersion);

            logger.info("API URL: {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Failed to fetch versions for {}: HTTP {}", candidate, response.statusCode());
                return new ArrayList<>();
            }

            // SDKMAN API 返回表格格式，根据installed和current参数标记已安装版本和当前版本

            return parseVersionsCsv(response.body(), candidate);

        } catch (Exception e) {
            logger.error("Failed to fetch versions for {} from API", candidate, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取本地已安装的版本列表（逗号分隔）
     * 用于传递给 API 的 ?installed= 参数
     */
    private String getInstalledVersionsForCandidate(String candidate) {
        try {
            String candidateDir = ConfigManager.getSdkmanPath() + "/candidates/" + candidate;
            File dir = new File(candidateDir);

            if (!dir.exists() || !dir.isDirectory()) {
                return "";
            }

            // 获取已安装的版本目录（排除current）
            File[] versionDirs = dir.listFiles(file ->
                    file.isDirectory() && !"current".equals(file.getName())
            );

            if (versionDirs == null || versionDirs.length == 0) {
                return "";
            }

            // 拼接成逗号分隔的字符串
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < versionDirs.length; i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(versionDirs[i].getName());
            }

            String installed = sb.toString();
            logger.debug("{} installed versions: {}", candidate, installed);
            return installed;

        } catch (Exception e) {
            logger.debug("Failed to get installed versions for {}", candidate, e);
            return "";
        }
    }

    @Override
    public boolean install(String candidate, String version, ProgressCallback progressCallback) {
        logger.info("Installing {} version {} via HTTP API", candidate, version);

        try {
            // 1. 下载SDK
            if (progressCallback != null) {
                progressCallback.onProgress("正在下载 " + candidate + " " + version + "...");
            }

            Path tempFile = downloadSdk(candidate, version, progressCallback);
            if (tempFile == null) {
                logger.error("Failed to download {} {}", candidate, version);
                return false;
            }

            // 2. 解压到SDKMAN目录
            if (progressCallback != null) {
                progressCallback.onProgress("正在解压安装...");
            }

            boolean extracted = extractSdk(candidate, version, tempFile);

            // 4. 清理临时文件
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temp file: {}", tempFile);
            }

            if (extracted) {
                if (progressCallback != null) {
                    progressCallback.onProgress("安装完成");
                }
                logger.info("Successfully installed {} {}", candidate, version);
                return true;
            } else {
                if (progressCallback != null) {
                    progressCallback.onProgress("安装失败");
                }
                return false;
            }

        } catch (Exception e) {
            logger.error("Failed to install {} {}", candidate, version, e);
            if (progressCallback != null) {
                progressCallback.onProgress("安装异常: " + e.getMessage());
            }
            return false;
        }
    }

    @Override
    public boolean uninstall(String candidate, String version) {
        logger.info("Uninstalling {} version {} via HTTP API", candidate, version);

        try {
            String installPath = ConfigManager.getSdkmanPath() + "/candidates/" + candidate + "/" + version;
            Path path = Paths.get(installPath);

            if (!Files.exists(path)) {
                logger.warn("Version {} {} is not installed", candidate, version);
                return false;
            }

            // 递归删除目录
            deleteDirectory(path);

            logger.info("Successfully uninstalled {} {}", candidate, version);
            return true;

        } catch (Exception e) {
            logger.error("Failed to uninstall {} {}", candidate, version, e);
            return false;
        }
    }

    @Override
    public boolean setDefault(String candidate, String version) {
        logger.info("Setting default version for {} to {}", candidate, version);

        try {
            String candidatePath = ConfigManager.getSdkmanPath() + "/candidates/" + candidate;
            Path currentLink = Paths.get(candidatePath, "current");
            Path versionPath = Paths.get(candidatePath, version);

            if (!Files.exists(versionPath)) {
                logger.error("Version {} {} is not installed", candidate, version);
                return false;
            }

            // 删除旧的软链接
            if (Files.exists(currentLink)) {
                Files.delete(currentLink);
            }

            // 创建新的软链接
            // 注意：使用绝对路径作为目标，确保链接在任何位置都能正确解析
            Files.createSymbolicLink(currentLink, versionPath);

            // 验证链接是否创建成功
            if (!Files.exists(currentLink)) {
                logger.error("Failed to create symbolic link for {} {}", candidate, version);
                return false;
            }

            // 验证链接是否指向正确的目标
            if (Files.isSymbolicLink(currentLink)) {
                Path actualTarget = Files.readSymbolicLink(currentLink);
                logger.debug("Symbolic link created: {} -> {}", currentLink, actualTarget);
            }

            logger.info("Successfully set {} {} as default", candidate, version);
            return true;

        } catch (UnsupportedOperationException e) {
            // Windows系统可能不支持符号链接（需要管理员权限）
            logger.error("Symbolic links not supported on this system. " +
                    "On Windows, please run with administrator privileges or enable Developer Mode", e);
            return false;
        } catch (Exception e) {
            logger.error("Failed to set default for {} {}", candidate, version, e);
            return false;
        }
    }

    @Override
    public String getCurrentVersion(String candidate) {
        logger.debug("Getting current version for {}", candidate);

        try {
            String candidatePath = ConfigManager.getSdkmanPath() + "/candidates/" + candidate;
            Path currentLink = Paths.get(candidatePath, "current");

            if (!Files.exists(currentLink)) {
                return null;
            }

            // 读取符号链接指向的路径
            if (Files.isSymbolicLink(currentLink)) {
                Path target = Files.readSymbolicLink(currentLink);
                return target.getFileName().toString();
            }

            return null;

        } catch (Exception e) {
            logger.debug("Failed to get current version for {}", candidate, e);
            return null;
        }
    }

    @Override
    public boolean isCandidateInstalled(String candidate) {
        try {
            String candidateDir = ConfigManager.getSdkmanPath() + "/candidates/" + candidate;
            File dir = new File(candidateDir);

            if (!dir.exists() || !dir.isDirectory()) {
                return false;
            }

            // 检查是否有版本安装（排除current）
            File[] files = dir.listFiles();
            if (files == null) {
                return false;
            }

            for (File file : files) {
                if (file.isDirectory() && !"current".equals(file.getName())) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            logger.debug("Failed to check if {} is installed", candidate, e);
            return false;
        }
    }

    @Override
    public boolean isSdkmanInstalled() {
        // HTTP API方式不需要SDKMAN本地安装，只需要目录结构
        File sdkmanDir = new File(ConfigManager.getSdkmanPath());
        File candidatesDir = new File(sdkmanDir, "candidates");

        // 如果目录不存在，尝试创建
        if (!candidatesDir.exists()) {
            try {
                candidatesDir.mkdirs();
                logger.info("Created SDKMAN directory structure at {}", candidatesDir);
                return true;
            } catch (Exception e) {
                logger.error("Failed to create SDKMAN directory", e);
                return false;
            }
        }

        return true;
    }

    @Override
    public String getClientType() {
        return "HTTP API";
    }

    @Override
    public boolean isAvailable() {
        // HTTP API方式始终可用（跨平台）
        try {
            // 测试API连接
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/candidates/list"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean available = response.statusCode() == 200;

            if (available) {
                logger.info("HTTP API client is available and connected");
            } else {
                logger.warn("HTTP API responded with status {}", response.statusCode());
            }

            return available;

        } catch (Exception e) {
            logger.debug("HTTP API client not available: {}", e.getMessage());
            return false;
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 解析候选列表响应（表格格式）
     * 使用正则表达式解析，参考 sdkman-ui 的实现
     * 格式示例：
     * <pre>
     * --------------------------------------------------------------------------------
     * Apache ActiveMQ (Classic) (5.17.1)                  https://activemq.apache.org/
     *
     * Apache ActiveMQ® is a popular open source...
     *
     *                                                   $ sdk install activemq
     * --------------------------------------------------------------------------------
     * </pre>
     */
    private List<Sdk> parseCandidates(String tableText) {
        List<Sdk> sdks = new ArrayList<>();

        if (tableText == null || tableText.trim().isEmpty()) {
            return sdks;
        }

        Matcher matcher = CANDIDATE_PATTERN.matcher(tableText);

        while (matcher.find()) {
            try {
                // group(1): 候选名称（包含版本和网址的第一行）
                // group(2): 描述文本
                // group(3): candidate ID（安装命令中的标识符）

                String firstLine = matcher.group(1).trim();
                String description = matcher.group(2).trim().replace("\n", " ");
                String candidateId = matcher.group(3).trim();

                Sdk sdk = new Sdk();
                sdk.setCandidate(candidateId);
                sdk.setDescription(description);

                // 从第一行解析名称、版本和网址
                // 格式: 名称 (版本) [可能还有副标题]  网址

                // 提取网址（最后一个 http 开头的部分）
                int httpIndex = firstLine.lastIndexOf("http");
                if (httpIndex > 0) {
                    sdk.setWebsite(firstLine.substring(httpIndex).trim());
                    firstLine = firstLine.substring(0, httpIndex).trim();
                }

                // 提取版本（最后一对括号中的内容）
                int lastOpenParen = firstLine.lastIndexOf("(");
                int lastCloseParen = firstLine.lastIndexOf(")");
                if (lastOpenParen > 0 && lastCloseParen > lastOpenParen) {
                    sdk.setLatestVersion(firstLine.substring(lastOpenParen + 1, lastCloseParen).trim());
                    firstLine = firstLine.substring(0, lastOpenParen).trim();
                }

                // 剩下的就是名称
                sdk.setName(firstLine);

                sdks.add(sdk);

            } catch (Exception e) {
                logger.debug("Failed to parse candidate section", e);
            }
        }

        logger.info("Parsed {} candidates from API response", sdks.size());
        return sdks;
    }

    /**
     * 解析版本列表响应（表格格式）
     * 使用正则表达式解析，参考 sdkman-ui 的实现
     */
    private List<SdkVersion> parseVersionsCsv(String tableText, String candidate) {
        List<SdkVersion> versions = new ArrayList<>();

        if (tableText == null || tableText.trim().isEmpty()) {
            return versions;
        }

        // 检测是Java格式还是其他格式
        Matcher javaMatcher = JAVA_VERSION_PATTERN.matcher(tableText);
        if (javaMatcher.find()) {
            // Java格式：带|分隔符的表格
            logger.debug("Detected Java format for {}", candidate);
            versions = parseJavaVersions(tableText, candidate);
        } else {
            // 其他SDK格式：空格分隔
            logger.debug("Detected other SDK format for {}", candidate);
            versions = parseOtherVersions(tableText, candidate);
        }

        logger.debug("Parsed {} versions for {} (installed: {})",
                versions.size(), candidate,
                versions.stream().filter(SdkVersion::isInstalled).count());
        return versions;
    }

    /**
     * 解析Java版本（表格格式，|分隔）
     * 格式: Vendor | Use | Version | Dist | Status | Identifier
     */
    private List<SdkVersion> parseJavaVersions(String tableText, String candidate) {
        List<SdkVersion> versions = new ArrayList<>();
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(tableText);
        String lastVendor = null;

        while (matcher.find()) {
            try {
                String vendorCol = matcher.group(1).trim();
                String useCol = matcher.group(2).trim();
                String versionCol = matcher.group(3).trim();
                String distCol = matcher.group(4).trim();
                String statusCol = matcher.group(5).trim();
                String identifierCol = matcher.group(6).trim();

                // 跳过表头
                if (VENDOR_HEADER_NAME.equals(vendorCol)) {
                    continue;
                }

                // 处理vendor（可能为空，使用上一行的vendor）
                if (!vendorCol.isEmpty()) {
                    lastVendor = vendorCol;
                } else {
                    vendorCol = lastVendor;
                }

                // identifier 必须非空
                if (identifierCol.isEmpty()) {
                    continue;
                }

                SdkVersion sdkVersion = new SdkVersion(versionCol);
                sdkVersion.setCandidate(candidate);
                sdkVersion.setIdentifier(identifierCol);
                sdkVersion.setVersion(versionCol);
                sdkVersion.setVendor(vendorCol);
                sdkVersion.setCategory(JdkCategory.fromIdentifier(identifierCol));

                // 解析状态
                boolean isInstalled = statusCol.contains("installed") || useCol.contains("*");
                boolean isInUse = useCol.contains(">");

                sdkVersion.setInstalled(isInstalled);
                sdkVersion.setInUse(isInUse);
                sdkVersion.setDefault(isInUse);

                versions.add(sdkVersion);

            } catch (Exception e) {
                logger.debug("Failed to parse Java version line", e);
            }
        }

        return versions;
    }

    /**
     * 解析其他SDK版本（空格分隔格式）
     * 参考 sdkman-ui 的实现逻辑
     */
    private List<SdkVersion> parseOtherVersions(String tableText, String candidate) {
        List<SdkVersion> versions = new ArrayList<>();

        logger.debug("=== parseOtherVersions called for {} ===", candidate);
        logger.debug("Full API response:\n{}", tableText);

        // 逐行解析（跳过前3行表头和最后的分隔符行）
        String[] lines = tableText.split("\n");

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // 跳过表头（前3行）
            if (lineNum < 3) {
                continue;
            }

            // 跳过分隔符行
            if (line.trim().startsWith("=====")) {
                break;
            }

            // 跳过空行
            if (line.trim().isEmpty()) {
                continue;
            }

            logger.debug("Processing line {}: '{}'", lineNum, line);

            // 每行开头的5个字符是标记区域，格式如: " > * " 或 "   * " 或 "     "
            String markerArea = line.length() >= 5 ? line.substring(0, 5) : line;
            boolean isInUse = markerArea.contains(">");
            boolean isInstalled = markerArea.contains("*");

            logger.debug("  Marker area: '{}', isInUse={}, isInstalled={}", markerArea, isInUse, isInstalled);

            // 提取版本区域（从第5个字符开始），按空格分割
            String versionsArea = line.length() > 5 ? line.substring(5) : "";

            // 按多个空格分割，提取所有版本号
            String[] versionParts = versionsArea.split("\\s+");

            for (String versionText : versionParts) {
                versionText = versionText.trim();

                if (versionText.isEmpty()) {
                    continue;
                }

                logger.debug("  Found version: '{}', isInUse={}, isInstalled={}", versionText, isInUse, isInstalled);

                SdkVersion sdkVersion = new SdkVersion(versionText);
                sdkVersion.setCandidate(candidate);
                sdkVersion.setIdentifier(versionText);
                sdkVersion.setVersion(versionText);
                sdkVersion.setInstalled(isInstalled || isInUse);
                sdkVersion.setInUse(isInUse);
                sdkVersion.setDefault(isInUse);

                versions.add(sdkVersion);

                logger.debug("  Created SdkVersion: version={}, installed={}, inUse={}, isDefault={}",
                        sdkVersion.getVersion(), sdkVersion.isInstalled(),
                        sdkVersion.isInUse(), sdkVersion.isDefault());
            }

            // 标记只应用于该行的第一个版本（最左边的版本）
            // 如果一行有多个版本，只有第一个版本带标记
            // 所以我们需要修正逻辑：只有第一个版本继承标记
            if (versionParts.length > 0 && !versions.isEmpty()) {
                // 获取刚添加的版本数量
                int justAddedCount = Math.min(versionParts.length, versions.size());
                // 只有第一个版本保留标记，其他版本移除标记
                for (int i = versions.size() - justAddedCount + 1; i < versions.size(); i++) {
                    SdkVersion v = versions.get(i);
                    v.setInstalled(false);
                    v.setInUse(false);
                    v.setDefault(false);
                    logger.debug("  Cleared markers for subsequent version: {}", v.getVersion());
                }
            }
        }

        logger.debug("=== Parsed {} versions for {} ===", versions.size(), candidate);
        versions.stream()
                .filter(v -> v.isInstalled() || v.isInUse())
                .forEach(v -> logger.debug("  Installed/InUse version: {} - installed={}, inUse={}, isDefault={}",
                        v.getVersion(), v.isInstalled(), v.isInUse(), v.isDefault()));

        return versions;
    }

    /**
     * 下载SDK
     */
    private Path downloadSdk(String candidate, String version, ProgressCallback progressCallback) {
        try {
            String platform = PlatformDetector.detectPlatform();
            // SDKMAN broker API: /broker/download/{candidate}/{version}/{platform}
            String url = API_BASE_URL + "/broker/download/" + candidate + "/" + version + "/" + platform;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(10))
                    .GET()
                    .build();

            Path tempFile = Files.createTempFile("sdkman-download-", ".zip");

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                logger.error("Download failed: HTTP {}", response.statusCode());
                return null;
            }

            try (InputStream in = response.body();
                 FileOutputStream out = new FileOutputStream(tempFile.toFile())) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;

                    if (progressCallback != null && totalBytes % (1024 * 1024) == 0) {
                        progressCallback.onProgress("已下载: " + (totalBytes / 1024 / 1024) + " MB");
                    }
                }
            }

            logger.info("Downloaded SDK to {}", tempFile);
            return tempFile;

        } catch (Exception e) {
            logger.error("Failed to download SDK", e);
            return null;
        }
    }

    /**
     * 解压SDK到安装目录
     */
    private boolean extractSdk(String candidate, String version, Path zipFile) {
        try {
            String installPath = ConfigManager.getSdkmanPath() + "/candidates/" + candidate + "/" + version;
            Path targetDir = Paths.get(installPath);

            // 创建目标目录
            Files.createDirectories(targetDir);

            // 解压zip文件
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    Path targetPath = targetDir.resolve(entry.getName());

                    if (entry.isDirectory()) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(zis, targetPath);
                    }

                    zis.closeEntry();
                }
            }

            logger.info("Extracted SDK to {}", installPath);
            return true;

        } catch (Exception e) {
            logger.error("Failed to extract SDK", e);
            return false;
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try {
                        deleteDirectory(p);
                    } catch (IOException e) {
                        logger.warn("Failed to delete {}", p, e);
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }

}
