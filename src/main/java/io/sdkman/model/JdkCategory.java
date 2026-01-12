package io.sdkman.model;

import java.util.HashSet;
import java.util.Set;

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
     * 根据identifier识别JDK类别集合
     * 一个JDK可能同时属于多个分类(例如: 既支持JavaFX又是NIK)
     *
     * @param identifier JDK标识符
     * @return JDK类别集合
     */
    public static Set<JdkCategory> fromIdentifier(String identifier) {
        Set<JdkCategory> categories = new HashSet<>();

        if (identifier == null || identifier.isEmpty()) {
            categories.add(JDK);
            return categories;
        }

        String lowerIdentifier = identifier.toLowerCase();

        // 检查是否包含JavaFX
        if (lowerIdentifier.contains(".fx")) {
            categories.add(JAVAFX);
        }

        // 检查是否为NIK
        // 包含 GraalVM 相关产品：
        // - Liberica NIK: 标识符以 -nik 结尾（已包含在 contains("-nik") 中）
        // - Mandrel: 标识符包含 -mandrel
        // - GraalVM: 标识符包含 -graal 或 -graalvm
        if (lowerIdentifier.contains("-nik")
                || lowerIdentifier.contains("-mandrel")
                || lowerIdentifier.contains("-graal")
                || lowerIdentifier.contains("-graalvm")) {
            categories.add(NIK);
        }

        // 如果没有特殊分类，则归为普通JDK
        if (categories.isEmpty()) {
            categories.add(JDK);
        }

        return categories;
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
