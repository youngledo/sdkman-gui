package io.sdkman.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 配置管理器 - 负责应用配置的读取、保存和管理
 * 配置文件存储在用户主目录下的 .sdkman-gui/config.json
 */
public class ConfigManager {
    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static final String CONFIG_DIR = ".sdkman-gui";
    private static final String CONFIG_FILE = "config.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static Map<String, Object> config;
    private static Path configPath;

    // 配置键常量
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THEME = "theme";
    private static final String KEY_SDKMAN_PATH = "sdkmanPath";
    private static final String KEY_PROXY_TYPE = "proxyType";
    private static final String KEY_PROXY_HOST = "proxyHost";
    private static final String KEY_PROXY_PORT = "proxyPort";

    static {
        initConfig();
    }

    /**
     * 初始化配置
     */
    private static void initConfig() {
        try {
            // 获取用户主目录
            String userHome = PlatformDetector.userHome();
            Path configDir = Paths.get(userHome, CONFIG_DIR);
            configPath = configDir.resolve(CONFIG_FILE);

            // 如果配置目录不存在，创建它
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                logger.info("Created config directory: {}", configDir);
            }

            // 加载或创建配置文件
            if (Files.exists(configPath)) {
                loadConfig();
            } else {
                createDefaultConfig();
            }

        } catch (IOException e) {
            logger.error("Failed to initialize config", e);
            config = getDefaultConfig();
        }
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try {
            String json = Files.readString(configPath);
            config = objectMapper.readValue(json, new TypeReference<HashMap<String, Object>>() {
            });
            logger.info("Config loaded from: {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to load config, using defaults", e);
            config = getDefaultConfig();
        }
    }

    /**
     * 创建默认配置文件
     */
    private static void createDefaultConfig() {
        config = getDefaultConfig();
        saveConfig();
        logger.info("Created default config at: {}", configPath);
    }

    /**
     * 获取默认配置
     */
    private static Map<String, Object> getDefaultConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();
        defaultConfig.put(KEY_LANGUAGE, null); // null 表示自动检测
        defaultConfig.put(KEY_THEME, "auto"); // auto, light, dark
        defaultConfig.put(KEY_SDKMAN_PATH, Paths.get(PlatformDetector.userHome(), ".sdkman"));
        defaultConfig.put(KEY_PROXY_TYPE, "none"); // none, auto, manual
        defaultConfig.put(KEY_PROXY_HOST, "");
        defaultConfig.put(KEY_PROXY_PORT, "");
        return defaultConfig;
    }

    /**
     * 保存配置到文件
     */
    public static void saveConfig() {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(config);
            Files.writeString(configPath, json);
            logger.info("Config saved to: {}", configPath);
        } catch (IOException e) {
            logger.error("Failed to save config", e);
        }
    }

    /**
     * 获取配置值
     */
    public static Object get(String key) {
        return config.get(key);
    }

    /**
     * 获取字符串配置值
     */
    public static String getString(String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * 设置配置值
     */
    public static void set(String key, Object value) {
        config.put(key, value);
        saveConfig();
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取保存的语言设置
     *
     * @return Locale 或 null（表示自动检测）
     */
    public static Locale getSavedLocale() {
        String language = getString(KEY_LANGUAGE, null);
        if (language == null) {
            return null;
        }

        return switch (language) {
            case "zh_CN" -> Locale.SIMPLIFIED_CHINESE;
            case "en_US" -> Locale.US;
            case "en" -> Locale.ENGLISH;
            default -> null;
        };
    }

    /**
     * 保存语言设置
     */
    public static void saveLocale(Locale locale) {
        String languageCode;
        if (locale.equals(Locale.SIMPLIFIED_CHINESE)) {
            languageCode = "zh_CN";
        } else if (locale.equals(Locale.US)) {
            languageCode = "en_US";
        } else {
            languageCode = "en";
        }
        set(KEY_LANGUAGE, languageCode);
    }

    /**
     * 获取主题设置
     */
    public static String getTheme() {
        return getString(KEY_THEME, "auto");
    }

    /**
     * 保存主题设置
     */
    public static void saveTheme(String theme) {
        set(KEY_THEME, theme);
    }

    /**
     * 获取 SDKMAN 安装路径
     */
    public static String getSdkmanPath() {
        return getString(KEY_SDKMAN_PATH, Paths.get(PlatformDetector.userHome(), ".sdkman").toString());
    }

    /**
     * 获取代理类型
     *
     * @return "none", "auto", "manual"
     */
    public static String getProxyType() {
        return getString(KEY_PROXY_TYPE, "none");
    }

    /**
     * 设置代理类型
     */
    public static void setProxyType(String proxyType) {
        set(KEY_PROXY_TYPE, proxyType);
    }

    /**
     * 获取代理主机
     */
    public static String getProxyHost() {
        return getString(KEY_PROXY_HOST, "");
    }

    /**
     * 设置代理主机
     */
    public static void setProxyHost(String host) {
        set(KEY_PROXY_HOST, host);
    }

    /**
     * 获取代理端口
     */
    public static String getProxyPort() {
        return getString(KEY_PROXY_PORT, "");
    }

    /**
     * 设置代理端口
     */
    public static void setProxyPort(String port) {
        set(KEY_PROXY_PORT, port);
    }

}
