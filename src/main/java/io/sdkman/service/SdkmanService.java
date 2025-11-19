package io.sdkman.service;

import io.sdkman.model.Category;
import io.sdkman.model.Installable;
import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.util.ConfigManager;
import io.sdkman.util.I18nManager;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SDK管理服务
 * 提供高级的SDK管理功能，封装CLI操作
 */
public class SdkmanService {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanService.class);

    private final SdkmanHttpClient client;
    private static SdkmanService instance;

    private SdkmanService() {
        this.client = createClient();
    }

    /**
     * 根据类型创建客户端
     */
    private SdkmanHttpClient createClient() {
        return new SdkmanHttpClient();
    }

    /**
     * 获取单例实例
     */
    public static synchronized SdkmanService getInstance() {
        if (instance == null) {
            instance = new SdkmanService();
        }
        return instance;
    }

    /**
     * 检查SDKMAN是否已安装
     */
    public boolean isSdkmanInstalled() {
        return client.isSdkmanInstalled();
    }

    /**
     * 异步获取JDK列表
     *
     * @return Task对象
     */
    public Task<List<SdkVersion>> loadJdkVersionsTask() {
        return new Task<>() {
            @Override
            protected List<SdkVersion> call() {
                logger.info("Loading JDK versions from HTTP API...");

                // HTTP API很快（~1秒），直接加载，数据总是最新的
                // 不再使用缓存，避免状态同步问题
                List<SdkVersion> versions = client.listVersions("java");

                // 恢复"正在安装"的临时状态（必须保留，避免刷新时丢失安装进度）
                mergeInstallStateFromCache(versions);

                logger.info("Loaded {} JDK versions", versions != null ? versions.size() : 0);
                return versions;
            }
        };
    }

    /**
     * 合并缓存中的安装状态（使用统一的安装状态管理器）
     */
    private void mergeInstallStateFromCache(List<SdkVersion> versions) {
        // 使用统一的安装状态管理器恢复状态
        io.sdkman.util.InstallStateManager.getInstance().restoreInstallStates(
                "jdk",
                versions,
                SdkVersion::getIdentifier,
                SdkVersion::getVersion,
                _ -> "java",  // JDK的candidate固定为"java"
                (version, installing, progress) -> {
                    version.setInstalling(installing);
                    version.setInstallProgress(progress);
                }
        );
    }

    /**
     * 更新JDK版本的安装状态
     */
    public void updateJdkInstallState(String identifier, boolean installing, String progress) {
        // 更新到统一的安装状态管理器
        io.sdkman.util.InstallStateManager.getInstance().updateInstallState(
                "jdk", identifier, "java", extractVersionFromIdentifier(identifier), installing, progress);

        logger.debug("Updated install state for {}: installing={}, progress={}", identifier, installing, progress);
    }

    ///
    /// Updates SDK installation state
    /// 更新SDK安装状态
    ///
    /// @param candidate  SDK candidate name / SDK候选名称
    /// @param installing Whether currently installing / 是否正在安装
    /// @param progress   Installation progress message / 安装进度消息
    ///
    public void updateSdkInstallState(String candidate, boolean installing, String progress) {
        // 更新到统一的安装状态管理器
        // 对于SDK，我们使用candidate作为标识符的一部分，版本从progress中提取
        var version = extractVersionFromProgress(progress);
        var identifier = candidate + "-" + (version != null ? version : "unknown");
        io.sdkman.util.InstallStateManager.getInstance().updateInstallState(
                "sdk", identifier, candidate, version, installing, progress);

        logger.debug("Updated SDK install state for {}: installing={}, progress={}", candidate, installing, progress);
    }

    /**
     * 更新SDK版本的安装状态（用于SDK详情页面）
     */
    public void updateSdkVersionInstallState(String candidate, String version, boolean installing, String progress) {
        // 更新到统一的安装状态管理器，使用与restoreSdkInstallState相同的key格式
        String identifier = candidate + "-" + version;
        io.sdkman.util.InstallStateManager.getInstance().updateInstallState(
                "sdk", identifier, candidate, version, installing, progress);

        logger.debug("Updated SDK version install state for {}: installing={}, progress={}", identifier, installing, progress);
    }

    /**
     * 恢复SDK版本的安装状态（使用统一的安装状态管理器）
     *
     * @param candidate SDK候选名称
     * @param versions  版本列表
     */
    private void restoreSdkInstallState(String candidate, List<SdkVersion> versions) {
        // 使用统一的安装状态管理器恢复状态
        io.sdkman.util.InstallStateManager.getInstance().restoreInstallStates(
                "sdk",
                versions,
                version -> candidate + "-" + version.getVersion(),  // 使用与updateSdkInstallState相同的key格式
                SdkVersion::getVersion,
                _ -> candidate,   // 所有版本都属于同一个candidate
                (version, installing, progress) -> {
                    version.setInstalling(installing);
                    version.setInstallProgress(progress);
                }
        );
    }

    /**
     * 更新Installable的安装状态（通用方法）
     */
    public void updateInstallState(Installable item, boolean installing, String progress) {
        if (item instanceof SdkVersion version) {
            // 检查是JDK还是SDK：JDK的candidate固定为"java"，SDK的candidate是其他值
            if ("java".equals(version.getCandidate())) {
                updateJdkInstallState(item.getVersionIdentifier(), installing, progress);
            } else {
                // 对于SDK版本，使用candidate和version来更新状态
                updateSdkVersionInstallState(version.getCandidate(), version.getVersion(), installing, progress);
            }
        } else if (item instanceof Sdk) {
            updateSdkInstallState(item.getCandidate(), installing, progress);
        }
    }

    /**
     * 异步安装Installable（通用安装方法）
     *
     * @param item 要安装的项（JDK或SDK）
     * @return Task对象
     */
    public Task<Boolean> installTask(Installable item) {
        String candidate = item.getCandidate();
        String version = item.getVersionIdentifier();

        return new Task<>() {
            @Override
            protected Boolean call() {
                // 使用进度回调来更新任务消息
                boolean success = client.install(candidate, version, line -> {
                    // 实时更新进度信息到Task的message属性
                    logger.debug("Installation progress for {}: {}", candidate, line);
                });

                if (!success) {
                    updateMessage(String.format("安装 %s %s 失败", candidate, version));
                }
                return success;
            }
        };
    }

    /**
     * 异步卸载SDK
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return Task对象
     */
    public Task<Boolean> uninstallSdkTask(String candidate, String version) {
        return new Task<>() {
            @Override
            protected Boolean call() {
                return client.uninstall(candidate, version);
            }
        };
    }

    /**
     * 异步设置默认版本
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return Task对象
     */
    public Task<Boolean> setDefaultTask(String candidate, String version) {
        return new Task<>() {
            @Override
            protected Boolean call() {
                return client.setDefault(candidate, version);
            }
        };
    }

    /**
     * 获取已安装的SDK数量
     * 性能优化：优先使用缓存的SDK列表，如果缓存不可用则快速扫描本地目录
     *
     * @return 已安装SDK数量
     */
    public int getInstalledSdkCount() {
        try {
            // 快速扫描本地candidates目录
            logger.debug("Scanning local candidates directory");
            String candidatesDir = ConfigManager.getSdkmanPath() + "/candidates";
            java.io.File dir = new java.io.File(candidatesDir);

            if (!dir.exists() || !dir.isDirectory()) {
                logger.debug("Candidates directory does not exist");
                return 0;
            }

            java.io.File[] candidateDirs = dir.listFiles(java.io.File::isDirectory);
            if (candidateDirs == null) {
                return 0;
            }

            int count = 0;
            for (java.io.File candidateDir : candidateDirs) {
                String candidateName = candidateDir.getName();

                // 跳过java（在JDK页面单独统计）
                if ("java".equalsIgnoreCase(candidateName)) {
                    continue;
                }

                // 检查是否有已安装的版本
                java.io.File[] versionDirs = candidateDir.listFiles(java.io.File::isDirectory);
                if (versionDirs != null) {
                    for (java.io.File versionDir : versionDirs) {
                        // 排除current软链接
                        if (!"current".equals(versionDir.getName())) {
                            count++;
                            logger.debug("Found installed SDK: {}", candidateName);
                            break; // 每个候选只统计一次
                        }
                    }
                }
            }

            logger.debug("Found {} installed SDKs by directory scan", count);
            return count;

        } catch (Exception e) {
            logger.warn("Failed to count installed SDKs: {}", e.getMessage());
            logger.debug("Full stack trace:", e);
            return 0;
        }
    }

    /**
     * 获取已安装的JDK数量
     * 快速扫描本地目录
     *
     * @return 已安装JDK数量
     */
    public int getInstalledJdkCount() {
        try {
            // 快速扫描本地安装目录
            logger.debug("Scanning local java directory");
            String javaDir = ConfigManager.getSdkmanPath() + File.separator + "candidates"+ File.separator + "java";
            java.io.File javaDirFile = new java.io.File(javaDir);

            if (!javaDirFile.exists() || !javaDirFile.isDirectory()) {
                logger.debug("Java directory does not exist");
                return 0;
            }

            java.io.File[] files = javaDirFile.listFiles();
            if (files == null) {
                return 0;
            }

            // 统计目录数量（排除current软链接）
            int count = 0;
            for (java.io.File file : files) {
                if (file.isDirectory() && !file.getName().equals("current")) {
                    count++;
                }
            }

            logger.debug("Found {} installed JDKs by directory scan", count);
            return count;

        } catch (Exception e) {
            logger.error("Failed to count installed JDKs", e);
            return 0;
        }
    }

    ///
    /// Gets the count of updatable SDKs
    /// 获取可更新的SDK数量
    ///
    /// This method checks all installed SDKs and compares their current
    /// version with the latest available version to determine if updates exist.
    /// 此方法检查所有已安装的SDK，并将当前版本与最新可用版本比较以确定是否存在更新。
    ///
    /// @return Number of SDKs that have updates available / 可更新的SDK数量
    ///
    public int getUpdatableCount() {
        int updateCount = 0;

        // Get all SDK candidates first
        // Get all SDK candidates first
        var allSdks = getAllSdks();

        // Filter to only check candidates that are actually installed
        // This improves performance by not checking ~50 candidates unnecessarily
        List<String> installedCandidates = new ArrayList<>();

        for (var sdk : allSdks) {
            String candidate = sdk.getCandidate();
            if (isCandidateInstalled(candidate)) {
                installedCandidates.add(candidate);
                logger.debug("Found installed SDK candidate: {}", candidate);
            }
        }

        logger.info("Checking updates for {} installed SDK candidates", installedCandidates.size());

        for (String candidate : installedCandidates) {
            try {
                // Get all available versions (with installation status from API)
                List<SdkVersion> versions = client.listVersions(candidate);

                if (versions != null && !versions.isEmpty()) {
                    // Find the latest version (first in sorted list - versions are sorted descending)
                    String latestVersion = versions.getFirst().getVersion();

                    // Check if there's a newer version than what's installed
                    if (hasNewerVersionAvailable(candidate, versions, latestVersion)) {
                        updateCount++;
                        logger.info("Update available for {}: latest {}", candidate, latestVersion);
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to check updates for {}: {}", candidate, e.getMessage());
                // Continue with other candidates
            }
        }

        logger.info("Update check completed. {} SDKs have updates available.", updateCount);
        return updateCount;
    }

    ///
    /// Checks if there's a newer version available for a candidate
    /// 检查候选是否有更新的版本可用
    ///
    /// @param candidate     SDK candidate name / SDK候选名称
    /// @param allVersions   All available versions (including installed status) / 所有可用版本（包括安装状态）
    /// @param latestVersion Latest available version / 最新可用版本
    /// @return `true` if newer version is available / 如果有更新版本则返回 `true`
    ///
    private boolean hasNewerVersionAvailable(String candidate, List<SdkVersion> allVersions, String latestVersion) {
        return allVersions.stream()
                .filter(SdkVersion::isInstalled)
                .map(SdkVersion::getVersion)
                .anyMatch(installedVersion -> {
                    // Compare versions using the existing version comparator
                    // If latest version > installed version, update is available
                    boolean hasUpdate = io.sdkman.util.VersionComparator.descending()
                            .compare(latestVersion, installedVersion) > 0;

                    if (hasUpdate) {
                        logger.debug("Update available for {}: {} -> {}", candidate, installedVersion, latestVersion);
                    }

                    return hasUpdate;
                });
    }

    /**
     * 获取所有SDK列表
     *
     * @return SDK列表
     */
    public List<Sdk> getAllSdks() {
        logger.info("Loading all SDKs list from HTTP API...");

        // 获取候选列表（包含详细信息：名称、版本、网站、描述）
        List<Sdk> sdks = client.listCandidates();

        // 去重（使用candidate作为key）
        var uniqueSdks = new java.util.LinkedHashMap<String, Sdk>();
        for (var sdk : sdks) {
            if (sdk.getCandidate() != null) {
                uniqueSdks.putIfAbsent(sdk.getCandidate(), sdk);
            }
        }

        var finalSdks = new java.util.ArrayList<Sdk>();

        for (var sdk : uniqueSdks.values()) {
            String candidate = sdk.getCandidate();

            // 如果是中文环境，尝试从国际化文件获取翻译的描述
            if (I18nManager.getCurrentLocale() == Locale.SIMPLIFIED_CHINESE) {
                String i18nKey = "sdk.description." + candidate;
                String translatedDescription = I18nManager.get(i18nKey);
                // 如果找到了翻译（不是返回key本身），则使用翻译
                if (!translatedDescription.equals(i18nKey)) {
                    sdk.setDescription(translatedDescription);
                }
                // 否则保持API返回的英文描述
            }
            // 英文环境直接使用API返回的英文描述，无需处理

            // 设置分类（基于候选名称自动分类，使用candidate而非name）
            var category = Category.fromName(sdk.getCandidate());
            sdk.setCategory(category);

            // 快速检查是否已安装（检查本地目录）
            boolean installed = isCandidateInstalled(candidate);
            sdk.setInstalled(installed);

            // 如果已安装，获取当前使用的版本
            if (installed) {
                String currentVersion = client.getCurrentVersion(candidate);
                if (currentVersion != null && !currentVersion.isEmpty()) {
                    sdk.setInstalledVersion(currentVersion);
                }
                // 如果SDKMAN没有提供latest version，使用当前版本作为fallback
                if ((sdk.getLatestVersion() == null || sdk.getLatestVersion().isEmpty()) && currentVersion != null) {
                    sdk.setLatestVersion(currentVersion);
                }
            }

            finalSdks.add(sdk);
            logger.debug("Added SDK: {} (category={}, installed={})",
                    candidate, category, installed);
        }

        logger.info("Loaded {} SDKs (excluding java)", finalSdks.size());

        return finalSdks;
    }

    /**
     * 快速检查候选是否已安装（本地目录检查）
     * Fast check if candidate is installed (local directory check)
     *
     * @param candidate 候选名称
     * @return 是否已安装
     */
    public boolean isCandidateInstalled(String candidate) {
        String candidateDir = ConfigManager.getSdkmanPath() +
                File.separator + "candidates" + File.separator + candidate;
        java.io.File dir = new java.io.File(candidateDir);

        if (!dir.exists() || !dir.isDirectory()) {
            return false;
        }

        // 检查是否有版本安装（排除current软链接）
        java.io.File[] files = dir.listFiles();
        if (files == null) {
            return false;
        }

        for (java.io.File file : files) {
            if (file.isDirectory() && !file.getName().equals("current")) {
                return true;
            }
        }

        return false;
    }


    /**
     * 加载指定SDK的版本列表（带缓存）
     *
     * @param candidate SDK候选名称
     * @return 版本列表
     */
    public List<SdkVersion> loadSdkVersions(String candidate) {
        logger.info("Loading versions for SDK: {} from HTTP API", candidate);

        // HTTP API很快（~1秒），直接加载，数据总是最新的
        // 不再使用缓存，避免状态同步问题
        List<SdkVersion> versions = client.listVersions(candidate);

        // 恢复"正在安装"的临时状态（必须保留，避免刷新时丢失安装进度）
        restoreSdkInstallState(candidate, versions);

        logger.debug("Loaded {} versions for {}", versions != null ? versions.size() : 0, candidate);
        return versions;
    }


    /**
     * 从标识符中提取版本号
     */
    private String extractVersionFromIdentifier(String identifier) {
        // 简单的版本提取逻辑，可以根据需要调整
        if (identifier.contains("-")) {
            return identifier.substring(identifier.indexOf("-") + 1);
        }
        return identifier;
    }

    /**
     * 从进度文本中提取版本号
     */
    private String extractVersionFromProgress(String progress) {
        if (progress == null) return null;

        // 查找版本号模式（例如：21.0.0-open, 3.9.0等）
        java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)*(?:-[^\\s]*)?)");
        java.util.regex.Matcher matcher = versionPattern.matcher(progress);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 检查指定SDK的指定版本是否是唯一已安装版本
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否是唯一已安装版本
     */
    public boolean isOnlyInstalledVersion(String candidate, String version) {
        // 获取所有已安装版本
        List<SdkVersion> installedVersions = client.listVersions(candidate).stream()
                .filter(SdkVersion::isInstalled)
                .toList();

        boolean isOnlyOne = installedVersions.size() == 1 &&
                installedVersions.getFirst().getVersion().equals(version);

        logger.debug("SDK {} has {} installed versions, {} {} is only one: {}",
                candidate, installedVersions.size(), candidate, version, isOnlyOne);

        return isOnlyOne;
    }

    /**
     * 设置指定SDK的指定版本为默认版本（用于唯一版本情况）
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否设置成功
     */
    public boolean setDefaultForOnlyVersion(String candidate, String version) {
        return client.setDefault(candidate, version);
    }

}
