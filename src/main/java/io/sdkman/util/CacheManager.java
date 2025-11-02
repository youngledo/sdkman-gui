package io.sdkman.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import io.sdkman.model.SdkVersion;
import io.sdkman.model.Sdk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * 缓存管理器
 * 用于缓存JDK版本列表等数据，减少网络请求
 */
public class CacheManager {
    private static final Logger logger = LoggerFactory.getLogger(CacheManager.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // 缓存目录
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.sdkman-gui/cache";

    // 缓存有效期（默认24小时）
    private static final Duration DEFAULT_CACHE_DURATION = Duration.ofDays(7);

    /**
     * 缓存元数据
     */
    public static class CacheMetadata {
        private String timestamp;  // ISO-8601格式的时间戳
        private long expiresInSeconds;  // 缓存有效期（秒）

        // Jackson需要无参构造函数
        public CacheMetadata() {
        }

        public CacheMetadata(Instant timestamp, Duration duration) {
            this.timestamp = timestamp.toString();
            this.expiresInSeconds = duration.getSeconds();
        }

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public long getExpiresInSeconds() {
            return expiresInSeconds;
        }

        public void setExpiresInSeconds(long expiresInSeconds) {
            this.expiresInSeconds = expiresInSeconds;
        }

        @JsonIgnore  // 告诉Jackson不要序列化这个方法
        public boolean isExpired() {
            try {
                Instant cacheTime = Instant.parse(timestamp);
                Instant now = Instant.now();
                Duration elapsed = Duration.between(cacheTime, now);
                return elapsed.getSeconds() > expiresInSeconds;
            } catch (Exception e) {
                logger.warn("Failed to check cache expiration: {}", e.getMessage());
                return true;  // 如果无法判断，认为已过期
            }
        }
    }

    /**
     * 缓存数据容器
     */
    public static class CacheData<T> {
        private CacheMetadata metadata;
        private T data;

        // Jackson需要无参构造函数
        public CacheData() {
        }

        public CacheData(T data, Duration duration) {
            this.metadata = new CacheMetadata(Instant.now(), duration);
            this.data = data;
        }

        public CacheMetadata getMetadata() {
            return metadata;
        }

        public void setMetadata(CacheMetadata metadata) {
            this.metadata = metadata;
        }

        public T getData() {
            return data;
        }

        public void setData(T data) {
            this.data = data;
        }
    }

    static {
        // 确保缓存目录存在
        try {
            Path cachePath = Paths.get(CACHE_DIR);
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
                logger.info("Created cache directory: {}", CACHE_DIR);
            }

            // 清理临时文件（如果有的话）
            File tempFile = new File(CACHE_DIR + "/jdk-versions.json.tmp");
            if (tempFile.exists()) {
                tempFile.delete();
                logger.info("Cleaned up temporary cache file");
            }
        } catch (IOException e) {
            logger.error("Failed to create cache directory: {}", CACHE_DIR, e);
        }
    }

    /**
     * 通用缓存方法
     *
     * @param cacheKey 缓存键（文件名，不含扩展名）
     * @param data     要缓存的数据
     * @param duration 缓存有效期
     * @param <T>      数据类型
     */
    public static <T> void cache(String cacheKey, T data, Duration duration) {
        if (data == null) {
            logger.warn("Attempting to cache null data for key: {}, skipping", cacheKey);
            return;
        }

        try {
            logger.info("Caching data for key: {}", cacheKey);

            File cacheFile = new File(CACHE_DIR + "/" + cacheKey + ".json");
            CacheData<T> cacheData = new CacheData<>(data, duration);

            // 写入到临时文件，然后重命名，确保原子性
            File tempFile = new File(CACHE_DIR + "/" + cacheKey + ".json.tmp");
            objectMapper.writeValue(tempFile, cacheData);

            // 验证临时文件不为空
            if (tempFile.length() == 0) {
                logger.error("Cache file is empty after writing, deleting temp file");
                tempFile.delete();
                return;
            }

            // 重命名临时文件为正式缓存文件
            if (cacheFile.exists()) {
                cacheFile.delete();
            }
            if (!tempFile.renameTo(cacheFile)) {
                logger.error("Failed to rename temp cache file");
                tempFile.delete();
                return;
            }

            logger.info("Cached data to {}, expires in {} hours",
                    cacheFile.getAbsolutePath(), duration.toHours());

        } catch (Exception e) {
            logger.error("Failed to cache data for key: {}", cacheKey, e);
        }
    }

    /**
     * 缓存JDK版本列表
     *
     * @param versions JDK版本列表
     */
    public static void cacheJdkVersions(List<SdkVersion> versions) {
        cacheJdkVersions(versions, DEFAULT_CACHE_DURATION);
    }

    /**
     * 缓存JDK版本列表，指定缓存有效期
     *
     * @param versions JDK版本列表
     * @param duration 缓存有效期
     */
    public static void cacheJdkVersions(List<SdkVersion> versions, Duration duration) {
        if (versions == null || versions.isEmpty()) {
            logger.warn("Attempting to cache empty or null JDK versions list, skipping");
            return;
        }

        long installedCount = versions.stream().filter(SdkVersion::isInstalled).count();
        logger.info("Caching {} JDK versions ({} installed)", versions.size(), installedCount);

        cache("jdk-versions", versions, duration);
    }

    /**
     * 通用读取缓存方法
     *
     * @param cacheKey      缓存键（文件名，不含扩展名）
     * @param typeReference Jackson TypeReference用于泛型反序列化
     * @param <T>           数据类型
     * @return 缓存的数据，如果缓存不存在或已过期则返回null
     */
    public static <T> T getCache(String cacheKey, TypeReference<CacheData<T>> typeReference) {
        try {
            File file = new File(CACHE_DIR + "/" + cacheKey + ".json");

            if (!file.exists()) {
                logger.debug("Cache file does not exist for key: {}", cacheKey);
                return null;
            }

            // 检查文件是否为空
            if (file.length() == 0) {
                logger.warn("Cache file is empty for key: {}, deleting it", cacheKey);
                file.delete();
                return null;
            }

            CacheData<T> cacheData = objectMapper.readValue(file, typeReference);

            if (cacheData == null || cacheData.getMetadata() == null) {
                logger.warn("Invalid cache data structure for key: {}", cacheKey);
                return null;
            }

            if (cacheData.getMetadata().isExpired()) {
                logger.info("Cache is expired for key: {}", cacheKey);
                return null;
            }

            logger.info("Loaded cached data for key: {}", cacheKey);
            return cacheData.getData();

        } catch (Exception e) {
            logger.error("Failed to read cache for key: {}", cacheKey, e);
            // 删除损坏的缓存文件
            try {
                File file = new File(CACHE_DIR + "/" + cacheKey + ".json");
                if (file.exists()) {
                    file.delete();
                    logger.info("Deleted corrupted cache file for key: {}", cacheKey);
                }
            } catch (Exception ex) {
                logger.debug("Failed to delete corrupted cache file", ex);
            }
            return null;
        }
    }

    /**
     * 读取缓存的JDK版本列表
     *
     * @return 缓存的版本列表，如果缓存不存在或已过期则返回null
     */
    public static List<SdkVersion> getCachedJdkVersions() {
        List<SdkVersion> versions = getCache("jdk-versions", new TypeReference<>() {});

        if (versions != null) {
            long installedCount = versions.stream().filter(SdkVersion::isInstalled).count();
            logger.info("Loaded {} JDK versions from cache ({} installed)", versions.size(), installedCount);
        }

        return versions;
    }

    /**
     * 检查JDK版本缓存是否有效
     *
     * @return true表示缓存存在且未过期
     */
    public static boolean isJdkVersionsCacheValid() {
        try {
            File file = new File(CACHE_DIR + "/jdk-versions.json");

            if (!file.exists() || file.length() == 0) {
                return false;
            }

            CacheData<List<SdkVersion>> cacheData = objectMapper.readValue(
                    file, new TypeReference<>() {
                    }
            );

            return cacheData != null &&
                    cacheData.getMetadata() != null &&
                    !cacheData.getMetadata().isExpired();

        } catch (Exception e) {
            logger.debug("Failed to check cache validity: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 清除JDK版本缓存
     */
    public static void clearJdkVersionsCache() {
        try {
            String cacheFile = CACHE_DIR + "/jdk-versions.json";
            File file = new File(cacheFile);

            if (file.exists()) {
                file.delete();
                logger.info("Cleared JDK versions cache");
            }

        } catch (Exception e) {
            logger.error("Failed to clear JDK versions cache", e);
        }
    }

    /**
     * 缓存SDK列表
     *
     * @param sdks SDK列表
     */
    public static void cacheSdkList(List<Sdk> sdks) {
        cacheSdkList(sdks, DEFAULT_CACHE_DURATION);
    }

    /**
     * 缓存SDK列表，指定缓存有效期
     *
     * @param sdks     SDK列表
     * @param duration 缓存有效期
     */
    public static void cacheSdkList(List<Sdk> sdks, Duration duration) {
        if (sdks == null || sdks.isEmpty()) {
            logger.warn("Attempting to cache empty or null SDK list, skipping");
            return;
        }

        logger.info("Caching {} SDKs", sdks.size());
        cache("sdk-list", sdks, duration);
    }

    /**
     * 读取缓存的SDK列表
     *
     * @return 缓存的SDK列表，如果缓存不存在或已过期则返回null
     */
    public static List<Sdk> getCachedSdkList() {
        List<Sdk> sdks = getCache("sdk-list", new TypeReference<>() {});

        if (sdks != null) {
            logger.info("Loaded {} SDKs from cache", sdks.size());
        }

        return sdks;
    }

    /**
     * 缓存SDK版本列表
     *
     * @param cacheKey 缓存键（例如 "sdk-versions-maven"）
     * @param versions SDK版本列表
     */
    public static void cacheSdkVersions(String cacheKey, List<SdkVersion> versions) {
        cacheSdkVersions(cacheKey, versions, DEFAULT_CACHE_DURATION);
    }

    /**
     * 缓存SDK版本列表，指定缓存有效期
     *
     * @param cacheKey 缓存键（例如 "sdk-versions-maven"）
     * @param versions SDK版本列表
     * @param duration 缓存有效期
     */
    public static void cacheSdkVersions(String cacheKey, List<SdkVersion> versions, Duration duration) {
        if (versions == null || versions.isEmpty()) {
            logger.warn("Attempting to cache empty or null SDK versions list for key: {}, skipping", cacheKey);
            return;
        }

        long installedCount = versions.stream().filter(SdkVersion::isInstalled).count();
        logger.info("Caching {} SDK versions for key: {} ({} installed)", versions.size(), cacheKey, installedCount);

        cache(cacheKey, versions, duration);
    }

    /**
     * 读取缓存的SDK版本列表
     *
     * @param cacheKey 缓存键（例如 "sdk-versions-maven"）
     * @return 缓存的版本列表，如果缓存不存在或已过期则返回null
     */
    public static List<SdkVersion> getCachedSdkVersions(String cacheKey) {
        List<SdkVersion> versions = getCache(cacheKey, new TypeReference<>() {});

        if (versions != null) {
            long installedCount = versions.stream().filter(SdkVersion::isInstalled).count();
            logger.info("Loaded {} SDK versions from cache for key: {} ({} installed)",
                    versions.size(), cacheKey, installedCount);
        }

        return versions;
    }

    /**
     * 清除特定缓存文件
     */
    public static void clearCache(String cacheKey) {
        try {
            String cacheFile = CACHE_DIR + "/" + cacheKey + ".json";
            File file = new File(cacheFile);

            if (file.exists()) {
                if (file.delete()) {
                    logger.info("Cleared cache for key: {}", cacheKey);
                } else {
                    logger.warn("Failed to delete cache file: {}", cacheFile);
                }
            } else {
                logger.debug("Cache file does not exist for key: {}", cacheKey);
            }
        } catch (Exception e) {
            logger.error("Failed to clear cache for key: {}", cacheKey, e);
        }
    }

    
    /**
     * 清除所有缓存
     */
    public static void clearAllCache() {
        try {
            Path cachePath = Paths.get(CACHE_DIR);
            if (Files.exists(cachePath)) {
                try (Stream<Path> pathStream = Files.walk(cachePath)) {
                    pathStream.filter(Files::isRegularFile)
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException e) {
                                    logger.error("Failed to delete cache file: {}", path, e);
                                }
                            });
                }
                logger.info("Cleared all cache");
            }
        } catch (IOException e) {
            logger.error("Failed to clear all cache", e);
        }
    }
}
