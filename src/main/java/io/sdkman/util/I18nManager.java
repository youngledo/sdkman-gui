package io.sdkman.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.*;
import java.util.function.Consumer;

/**
 * 国际化管理器 - 负责应用的多语言支持
 * 支持中英文，自动检测系统语言
 */
public class I18nManager {
    private static final Logger logger = LoggerFactory.getLogger(I18nManager.class);

    private static final String BUNDLE_BASE_NAME = "i18n/messages";
    private static ResourceBundle bundle;
    private static Locale currentLocale;
    private static final List<Consumer<Locale>> localeChangeListeners = new ArrayList<>();

    static {
        initialize();
    }

    /**
     * 初始化国际化
     */
    private static void initialize() {
        // 1. 检查用户保存的语言设置
        Locale savedLocale = ConfigManager.getSavedLocale();

        if (savedLocale != null) {
            // 使用用户设置的语言
            currentLocale = savedLocale;
            logger.info("Using saved locale: {}", currentLocale);
        } else {
            // 2. 自动检测系统语言
            Locale systemLocale = Locale.getDefault();
            String language = systemLocale.getLanguage();
            String country = systemLocale.getCountry();
            String script = systemLocale.getScript(); // 对于繁体中文可能需要

            logger.info("System locale: {} (language: {}, country: {}, script: {})",
                    systemLocale, language, country, script);

            // 3. 根据系统语言选择
            if ("zh".equals(language)) {
                // 中文环境，根据地区选择
                if ("CN".equals(country) || "SG".equals(country)) {
                    // 中国大陆、新加坡 -> 简体中文
                    currentLocale = Locale.SIMPLIFIED_CHINESE;
                    logger.info("Detected Simplified Chinese system (country: {})", country);
                } else if ("TW".equals(country) || "HK".equals(country) || "MO".equals(country)) {
                    // 台湾、香港、澳门 -> 繁体中文（但我们也使用简体中文，因为只支持简体）
                    currentLocale = Locale.SIMPLIFIED_CHINESE;
                    logger.info("Detected Traditional Chinese system (country: {}), using Simplified Chinese", country);
                } else {
                    // 其他中文地区，默认使用简体中文
                    currentLocale = Locale.SIMPLIFIED_CHINESE;
                    logger.info("Detected Chinese system (country: {}), using Simplified Chinese", country);
                }
            } else {
                // 其他语言，默认使用英文
                currentLocale = Locale.ENGLISH;
                logger.info("Using English as default locale (language: {})", language);
            }
        }

        loadBundle();
    }

    /**
     * 加载资源包
     */
    private static void loadBundle() {
        try {
            bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, currentLocale);
            logger.info("Loaded resource bundle for locale: {}", currentLocale);
        } catch (MissingResourceException e) {
            logger.error("Failed to load resource bundle for locale: {}, falling back to default",
                    currentLocale, e);
            bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
            currentLocale = Locale.ENGLISH;
        }
    }

    /**
     * 获取翻译文本
     *
     * @param key 资源键
     * @return 翻译后的文本，如果键不存在则返回键本身
     */
    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            logger.debug("Missing resource key: {}", key);
            return key;
        }
    }

    /**
     * 获取带参数的翻译文本
     *
     * @param key  资源键
     * @param args 格式化参数
     * @return 格式化后的文本
     */
    public static String get(String key, Object... args) {
        String message = get(key);
        try {
            return MessageFormat.format(message, args);
        } catch (Exception e) {
            logger.error("Failed to format message: {} with args: {}", message, args, e);
            return message;
        }
    }

    /**
     * 切换语言
     *
     * @param locale 新的语言环境
     */
    public static void setLocale(Locale locale) {
        if (locale == null) {
            logger.warn("Attempted to set null locale, ignoring");
            return;
        }

        Locale oldLocale = currentLocale;
        currentLocale = locale;
        ConfigManager.saveLocale(locale);
        loadBundle();

        // 通知所有监听器语言已变化
        notifyLocaleChanged(locale);

        logger.info("Locale changed from {} to: {}", oldLocale, currentLocale);
    }

    /**
     * 添加语言变化监听器
     *
     * @param listener 监听器
     */
    public static void addLocaleChangeListener(Consumer<Locale> listener) {
        localeChangeListeners.add(listener);
    }

    /**
     * 通知所有监听器语言已变化
     *
     * @param newLocale 新的语言环境
     */
    private static void notifyLocaleChanged(Locale newLocale) {
        for (Consumer<Locale> listener : localeChangeListeners) {
            try {
                listener.accept(newLocale);
            } catch (Exception e) {
                logger.error("Error notifying locale change listener", e);
            }
        }
    }

    /**
     * 获取当前语言环境
     *
     * @return 当前的Locale
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }

}
