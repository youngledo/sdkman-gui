package io.sdkman.util;

import io.sdkman.model.SdkVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 安装状态管理器
 * 统一管理所有类型的安装状态持久化和恢复，消除重复代码
 */
public class InstallStateManager {
    private static final Logger logger = LoggerFactory.getLogger(InstallStateManager.class);

    // 单例实例
    private static volatile InstallStateManager instance;

    // 内存缓存：存储正在进行的安装状态
    // Key格式：type-identifier (例如: "jdk-21.0.0-open", "sdk-maven-3.9.0")
    private final Map<String, InstallState> installStateCache = new ConcurrentHashMap<>();

    /**
     * 安装状态数据类
     */
    public static class InstallState {
        private final String type; // "jdk" 或 "sdk"
        private final String identifier;
        private final String candidate;
        private final String version;
        private boolean installing;
        private String progress;
        private long timestamp;

        public InstallState(String type, String identifier, String candidate, String version) {
            this.type = type;
            this.identifier = identifier;
            this.candidate = candidate;
            this.version = version;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and setters
        public String getType() { return type; }
        public String getIdentifier() { return identifier; }
        public String getCandidate() { return candidate; }
        public String getVersion() { return version; }
        public boolean isInstalling() { return installing; }
        public void setInstalling(boolean installing) { this.installing = installing; }
        public String getProgress() { return progress; }
        public void setProgress(String progress) { this.progress = progress; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }

    private InstallStateManager() {}

    /**
     * 获取单例实例
     */
    public static InstallStateManager getInstance() {
        if (instance == null) {
            synchronized (InstallStateManager.class) {
                if (instance == null) {
                    instance = new InstallStateManager();
                }
            }
        }
        return instance;
    }

    /**
     * 更新安装状态
     *
     * @param type 类型 ("jdk" 或 "sdk")
     * @param identifier 唯一标识符
     * @param candidate SDK候选名称
     * @param version 版本号
     * @param installing 是否正在安装
     * @param progress 安装进度
     */
    public void updateInstallState(String type, String identifier, String candidate,
                                 String version, boolean installing, String progress) {
        String key = type + "-" + identifier;
        InstallState state = installStateCache.computeIfAbsent(key,
            k -> new InstallState(type, identifier, candidate, version));

        state.setInstalling(installing);
        state.setProgress(progress);
        state.setTimestamp(System.currentTimeMillis());

        logger.debug("Updated install state for {}: installing={}, progress={}",
                    identifier, installing, progress);

        // 如果安装完成或失败，一段时间后清理缓存
        if (!installing) {
            scheduleCleanup(key, 30000); // 30秒后清理
        }
    }

    /**
     * 恢复版本列表的安装状态
     *
     * @param type 类型 ("jdk" 或 "sdk")
     * @param versions 版本列表
     * @param identifierExtractor 从版本对象提取标识符的函数
     * @param versionExtractor 从版本对象提取版本号的函数
     * @param candidateExtractor 从版本对象提取候选名称的函数
     * @param stateSetter 状态设置函数
     */
    public <T> void restoreInstallStates(String type, List<T> versions,
                                        Function<T, String> identifierExtractor,
                                        Function<T, String> versionExtractor,
                                        Function<T, String> candidateExtractor,
                                        StateSetter<T> stateSetter) {

        logger.info("Restoring install states for {} versions", type);

        for (T version : versions) {
            String identifier = identifierExtractor.apply(version);
            String versionStr = versionExtractor.apply(version);
            String candidate = candidateExtractor.apply(version);

            // 尝试精确匹配
            String key = type + "-" + identifier;
            InstallState state = installStateCache.get(key);

            // 如果精确匹配失败，尝试模糊匹配（用于SDK）
            if (state == null && "sdk".equals(type)) {
                state = findMatchingSdkState(candidate, versionStr);
            }

            if (state != null && state.isInstalling()) {
                logger.info("Restoring install state for {}: installing={}, progress={}",
                          identifier, state.isInstalling(), state.getProgress());

                stateSetter.setState(version, true, state.getProgress());
            }
        }
    }

    /**
     * 清除指定类型的所有安装状态
     */
    public void clearStates(String type) {
        installStateCache.entrySet().removeIf(entry ->
            entry.getKey().startsWith(type + "-"));
        logger.info("Cleared all install states for type: {}", type);
    }

    /**
     * 清除过期的安装状态
     */
    public void cleanupExpiredStates() {
        long now = System.currentTimeMillis();
        long expireTime = 5 * 60 * 1000; // 5分钟过期

        installStateCache.entrySet().removeIf(entry -> {
            InstallState state = entry.getValue();
            boolean expired = !state.isInstalling() && (now - state.getTimestamp()) > expireTime;
            if (expired) {
                logger.debug("Removed expired install state: {}", entry.getKey());
            }
            return expired;
        });
    }

    /**
     * 获取所有安装状态（用于调试）
     */
    public Map<String, InstallState> getAllStates() {
        return new ConcurrentHashMap<>(installStateCache);
    }

    /**
     * 查找匹配的SDK状态（模糊匹配）
     */
    private InstallState findMatchingSdkState(String candidate, String version) {
        return installStateCache.values().stream()
            .filter(state -> "sdk".equals(state.getType()))
            .filter(state -> candidate.equals(state.getCandidate()))
            .filter(state -> state.getVersion().equals(version))
            .findFirst()
            .orElse(null);
    }

    /**
     * 计划清理任务
     */
    private void scheduleCleanup(String key, long delay) {
        Thread cleanupThread = new Thread(() -> {
            try {
                Thread.sleep(delay);
                InstallState state = installStateCache.get(key);
                if (state != null && !state.isInstalling()) {
                    installStateCache.remove(key);
                    logger.debug("Cleaned up install state: {}", key);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    /**
     * 状态设置函数式接口
     */
    @FunctionalInterface
    public interface StateSetter<T> {
        void setState(T item, boolean installing, String progress);
    }
}