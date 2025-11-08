package io.sdkman.model;

/**
 * JDK分类枚举
 */
public enum JdkCategory {
    /**
     * 普通JDK
     */
    JDK,

    /**
     * 带JavaFX支持的JDK
     */
    JAVAFX,

    /**
     * Native Image Kit (NIK)
     */
    NIK;

    /**
     * 根据identifier识别JDK类别
     *
     * @param identifier JDK标识符
     * @return JDK类别
     */
    public static JdkCategory fromIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return JDK;
        }

        String lowerIdentifier = identifier.toLowerCase();
        // NIK: 以-nik结尾
        if (lowerIdentifier.endsWith("-nik")) {
            return NIK;
        }
        // JavaFX: 包含.fx
        if (lowerIdentifier.contains(".fx")) {
            return JAVAFX;
        }
        // 默认为普通JDK
        return JDK;
    }

    /**
     * 获取显示名称（用于i18n）
     *
     * @return i18n key
     */
    public String getI18nKey() {
        return "jdk.category." + name().toLowerCase();
    }
}
