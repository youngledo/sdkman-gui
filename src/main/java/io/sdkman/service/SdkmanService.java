package io.sdkman.service;

import io.sdkman.model.Category;
import io.sdkman.model.Installable;
import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.util.CacheManager;
import io.sdkman.util.ConfigManager;
import io.sdkman.util.I18nManager;
import io.sdkman.util.MetadataLoader;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * SDK管理服务
 * 提供高级的SDK管理功能，封装CLI操作
 */
public class SdkmanService {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanService.class);

    private final SdkmanCliWrapper cliWrapper;
    private static SdkmanService instance;

    // 缓存JDK版本列表，包括安装状态，key是identifier
    private final java.util.Map<String, SdkVersion> jdkVersionCache = new java.util.HashMap<>();

    // 缓存SDK列表，包括安装状态，key是candidate
    private final java.util.Map<String, Sdk> sdkCache = new java.util.HashMap<>();

    private SdkmanService() {
        this.cliWrapper = new SdkmanCliWrapper();
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
        return cliWrapper.isSdkmanInstalled();
    }

    /**
     * 异步获取JDK列表
     *
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return Task对象
     */
    public Task<List<SdkVersion>> loadJdkVersionsTask(boolean forceRefresh) {
        return new Task<>() {
            @Override
            protected List<SdkVersion> call() {
                logger.info("Loading JDK versions (forceRefresh: {})...", forceRefresh);
                List<SdkVersion> versions;

                // 如果不是强制刷新，先尝试从缓存读取
                if (!forceRefresh) {
                    versions = CacheManager.getCachedJdkVersions();

                    if (versions != null && !versions.isEmpty()) {
                        logger.info("Loaded {} JDK versions from cache", versions.size());
                        // 合并缓存中的安装状态
                        mergeInstallStateFromCache(versions);
                        return versions;
                    }

                    logger.info("Cache not available, loading from network...");
                }

                // 从网络加载
                versions = cliWrapper.listVersions("java");

                // 保存到缓存
                if (!versions.isEmpty()) {
                    CacheManager.cacheJdkVersions(versions);
                }

                // 合并缓存中的安装状态
                mergeInstallStateFromCache(versions);

                logger.info("Loaded {} JDK versions from network", versions.size());
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
            version -> "java",  // JDK的candidate固定为"java"
            (version, installing, progress) -> {
                version.setInstalling(installing);
                version.setInstallProgress(progress);
            }
        );

        // 同时更新本地缓存
        for (SdkVersion version : versions) {
            String identifier = version.getIdentifier();
            jdkVersionCache.put(identifier, version);
        }
    }

    /**
     * 更新缓存中JDK版本的安装状态
     */
    public void updateJdkInstallState(String identifier, boolean installing, String progress) {
        SdkVersion cached = jdkVersionCache.get(identifier);
        if (cached != null) {
            cached.setInstalling(installing);
            cached.setInstallProgress(progress);
            logger.debug("Updated install state for {}: installing={}, progress={}", identifier, installing, progress);
        }

        // 同时更新到统一的安装状态管理器
        io.sdkman.util.InstallStateManager.getInstance().updateInstallState(
            "jdk", identifier, "java", extractVersionFromIdentifier(identifier), installing, progress);
    }

    /**
     * 更新缓存中SDK的安装状态
     */
    public void updateSdkInstallState(String candidate, boolean installing, String progress) {
        Sdk cached = sdkCache.get(candidate);
        if (cached != null) {
            cached.setInstalling(installing);
            cached.setInstallProgress(progress);
            logger.debug("Updated SDK install state for {}: installing={}, progress={}", candidate, installing, progress);
        }

        // 同时更新到统一的安装状态管理器
        // 对于SDK，我们使用candidate作为标识符的一部分，版本从progress中提取
        String version = extractVersionFromProgress(progress);
        String identifier = candidate + "-" + (version != null ? version : "unknown");
        io.sdkman.util.InstallStateManager.getInstance().updateInstallState(
            "sdk", identifier, candidate, version, installing, progress);
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
            version -> candidate,   // 所有版本都属于同一个candidate
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
                boolean success = cliWrapper.install(candidate, version, line -> {
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
                return cliWrapper.uninstall(candidate, version);
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
                return cliWrapper.setDefault(candidate, version);
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
            // 方法1：尝试从缓存的SDK列表获取（最快）
            List<Sdk> cachedSdks = CacheManager.getCachedSdkList();
            if (cachedSdks != null && !cachedSdks.isEmpty()) {
                int count = (int) cachedSdks.stream()
                        .filter(Sdk::isInstalled)
                        .count();
                logger.debug("Counted {} installed SDKs from cache", count);
                return count;
            }

            // 方法2：缓存不可用，快速扫描本地candidates目录
            logger.debug("Cache not available, scanning local candidates directory");
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
     * 性能优化：优先使用缓存的JDK版本列表，如果缓存不可用则快速扫描本地目录
     *
     * @return 已安装JDK数量
     */
    public int getInstalledJdkCount() {
        try {
            // 方法1：尝试从缓存的JDK版本列表获取（最快）
            List<SdkVersion> cachedVersions = CacheManager.getCachedJdkVersions();
            if (cachedVersions != null && !cachedVersions.isEmpty()) {
                int count = (int) cachedVersions.stream()
                        .filter(SdkVersion::isInstalled)
                        .count();
                logger.debug("Counted {} installed JDKs from cache", count);
                return count;
            }

            // 方法2：缓存不可用，快速扫描本地安装目录
            logger.debug("Cache not available, scanning local java directory");
            String javaDir = ConfigManager.getSdkmanPath() + "/candidates/java";
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

    /**
     * 获取可更新的SDK数量
     *
     * @return 可更新SDK数量
     */
    public int getUpdatableCount() {
        // TODO: 实现检查更新逻辑
        return 0;
    }

    /**
     * 获取所有SDK列表
     *
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return SDK列表
     */
    public List<Sdk> getAllSdks(boolean forceRefresh) {
        logger.info("Loading all SDKs list (forceRefresh: {})...", forceRefresh);

        // 如果不是强制刷新，先尝试从缓存读取
        if (!forceRefresh) {
            List<Sdk> cachedSdks = CacheManager.getCachedSdkList();
            if (cachedSdks != null && !cachedSdks.isEmpty()) {
                logger.info("Loaded {} SDKs from cache", cachedSdks.size());
                // 合并内存缓存中的安装状态
                mergeSdkInstallStateFromCache(cachedSdks);
                return cachedSdks;
            }
            logger.info("Cache not available, loading from SDKMAN...");
        }

        try {
            // 获取候选列表（包含详细信息：名称、版本、网站、描述）
            List<Sdk> sdks = cliWrapper.listCandidates();

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

                // 如果从SDKMAN解析的信息不完整，从元数据文件补充
                var metadata = MetadataLoader.getMetadata(candidate);
                if (metadata != null) {
                    // 不为空，且为中文时才设置
                    if (metadata.description() != null
                            && I18nManager.getCurrentLocale() == Locale.SIMPLIFIED_CHINESE){
                        sdk.setDescription(metadata.description());
                    }
                }

                // 设置分类（基于候选名称自动分类）
                var category = Category.fromName(sdk.getName());
                sdk.setCategory(category);

                // 快速检查是否已安装（检查本地目录）
                boolean installed = isCandidateInstalled(candidate);
                sdk.setInstalled(installed);

                // 如果已安装，获取当前使用的版本
                if (installed) {
                    String currentVersion = cliWrapper.getCurrentVersion(candidate);
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

            // 保存到缓存
            if (!finalSdks.isEmpty()) {
                CacheManager.cacheSdkList(finalSdks);
            }

            // 合并内存缓存中的安装状态
            mergeSdkInstallStateFromCache(finalSdks);

            return finalSdks;

        } catch (Exception e) {
            logger.error("Failed to load SDK list", e);
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 快速检查候选是否已安装（本地目录检查）
     *
     * @param candidate 候选名称
     * @return 是否已安装
     */
    private boolean isCandidateInstalled(String candidate) {
        try {
            String candidateDir = ConfigManager.getSdkmanPath() +
                    "/candidates/" + candidate;
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

        } catch (Exception e) {
            logger.debug("Failed to check if {} is installed: {}", candidate, e.getMessage());
            return false;
        }
    }

    /**
     * 合并内存缓存中的SDK安装状态
     */
    private void mergeSdkInstallStateFromCache(List<Sdk> sdks) {
        for (Sdk sdk : sdks) {
            String candidate = sdk.getCandidate();
            Sdk cached = sdkCache.get(candidate);

            if (cached != null) {
                // 同步所有状态：安装状态、进度、已安装状态等
                sdk.setInstalling(cached.isInstalling());
                if (cached.getInstallProgress() != null) {
                    sdk.setInstallProgress(cached.getInstallProgress());
                }
                sdk.setInstalled(cached.isInstalled());

                logger.debug("Restored state for SDK: {} (installed={}, installing={})",
                           candidate, cached.isInstalled(), cached.isInstalling());
            }

            // 更新内存缓存
            sdkCache.put(candidate, sdk);
        }
    }

    /**
     * 加载指定SDK的版本列表（带缓存）
     *
     * @param candidate    SDK候选名称
     * @param forceRefresh 是否强制刷新（忽略缓存）
     * @return 版本列表
     */
    public List<SdkVersion> loadSdkVersions(String candidate, boolean forceRefresh) {
        logger.info("Loading versions for SDK: {} (forceRefresh: {})", candidate, forceRefresh);

        String cacheKey = "sdk-versions-" + candidate;

        // 如果不强制刷新，尝试从缓存加载
        if (!forceRefresh) {
            List<SdkVersion> cachedVersions = CacheManager.getCachedSdkVersions(cacheKey);
            if (cachedVersions != null) {
                logger.info("Loaded {} versions for {} from cache", cachedVersions.size(), candidate);
                // 恢复安装状态（如果有正在进行的安装）
                restoreSdkInstallState(candidate, cachedVersions);
                return cachedVersions;
            }
        }

        // 从API加载
        logger.info("Fetching versions for {} from SDKMAN API...", candidate);
        List<SdkVersion> versions = cliWrapper.listVersions(candidate);

        // 保存到缓存
        if (versions != null && !versions.isEmpty()) {
            CacheManager.cacheSdkVersions(cacheKey, versions);
            logger.info("Cached {} versions for {}", versions.size(), candidate);
        }

        // 恢复安装状态（对于从API加载的版本也要恢复状态，确保安装进度不丢失）
        restoreSdkInstallState(candidate, versions);

        return versions;
    }

    /**
     * 清除SDK内存缓存（用于状态同步）
     */
    public void clearSdkCache() {
        try {
            sdkCache.clear();
            logger.info("Cleared in-memory SDK cache");
        } catch (Exception e) {
            logger.error("Failed to clear SDK cache", e);
        }
    }

    /**
     * 实时检查SDK是否已安装（不依赖缓存）
     *
     * @param candidate SDK候选名称
     * @return 是否已安装
     */
    public boolean isSdkInstalledRealtime(String candidate) {
        try {
            // 使用SDKMAN CLI检查安装状态，而不是缓存
            return cliWrapper.isCandidateInstalled(candidate);
        } catch (Exception e) {
            logger.debug("Failed to check real-time installation status for {}: {}", candidate, e.getMessage());
            return false;
        }
    }

    /**
     * 获取SDK的实时安装状态（通过sdk list命令，与详情页一致）
     * 这是统一的方法，SDK页面和详情页都应该使用这个方法
     *
     * @param candidate SDK候选名称
     * @return 是否已安装
     */
    public boolean isSdkInstalledViaListCommand(String candidate) {
        try {
            logger.debug("Checking installation status for {} via sdk list command", candidate);

            // 使用与详情页相同的命令获取实时状态
            List<SdkVersion> versions = cliWrapper.listVersions(candidate, false);

            // 检查是否有任何已安装的版本
            for (SdkVersion version : versions) {
                if (version.isInstalled()) {
                    logger.debug("Found installed version for {}: {}", candidate, version.getVersion());
                    return true;
                }
            }

            logger.debug("No installed versions found for {}", candidate);
            return false;

        } catch (Exception e) {
            logger.debug("Failed to check installation status for {} via list command: {}", candidate, e.getMessage());
            return false;
        }
    }

    /**
     * 加载指定SDK的版本列表（默认使用缓存）
     *
     * @param candidate SDK候选名称
     * @return 版本列表
     */
    public List<SdkVersion> loadSdkVersions(String candidate) {
        return loadSdkVersions(candidate, false);
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
        try {
            logger.debug("Checking if {} {} is the only installed version", candidate, version);

            // 获取所有已安装版本
            List<SdkVersion> installedVersions = cliWrapper.listVersions(candidate, false).stream()
                    .filter(SdkVersion::isInstalled)
                    .toList();

            boolean isOnlyOne = installedVersions.size() == 1 &&
                    installedVersions.get(0).getVersion().equals(version);

            logger.debug("SDK {} has {} installed versions, {} {} is only one: {}",
                       candidate, installedVersions.size(), candidate, version, isOnlyOne);

            return isOnlyOne;
        } catch (Exception e) {
            logger.warn("Failed to check if {} {} is only installed version: {}", candidate, version, e.getMessage());
            return false;
        }
    }

    /**
     * 设置指定SDK的指定版本为默认版本（用于唯一版本情况）
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否设置成功
     */
    public boolean setDefaultForOnlyVersion(String candidate, String version) {
        try {
            logger.info("Setting {} {} as default (only installed version)", candidate, version);
            return cliWrapper.setDefault(candidate, version);
        } catch (Exception e) {
            logger.error("Failed to set {} {} as default: {}", candidate, version, e.getMessage());
            return false;
        }
    }

}
