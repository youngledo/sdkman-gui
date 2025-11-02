package io.sdkman.util;

import java.util.Comparator;

/**
 * 语义版本号比较器
 * Semantic version number comparator
 *
 * 支持常见的版本号格式：
 * - 数字版本：1.0.0, 2.3.4, 11.0.21
 * - 带后缀版本：1.0.0-alpha, 2.1.0-SNAPSHOT
 * - 特殊标识：swan-lake-p3, graalce-21.0.0+35.1
 */
public class VersionComparator implements Comparator<String> {

    private static final VersionComparator INSTANCE = new VersionComparator();

    /**
     * 获取单例实例
     */
    public static VersionComparator getInstance() {
        return INSTANCE;
    }

    /**
     * 降序比较器（最新版本在前）
     */
    public static Comparator<String> descending() {
        return INSTANCE.reversed();
    }

    /**
     * 升序比较器（最旧版本在前）
     */
    public static Comparator<String> ascending() {
        return INSTANCE;
    }

    @Override
    public int compare(String v1, String v2) {
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return -1;
        if (v2 == null) return 1;
        if (v1.equals(v2)) return 0;

        // 尝试提取数字版本部分进行比较
        return compareVersionStrings(v1, v2);
    }

    /**
     * 比较版本字符串
     * Compares two version strings using semantic versioning rules
     */
    private int compareVersionStrings(String v1, String v2) {
        // 分离数字部分和后缀部分
        // 例如：21.0.0+35.1 -> parts=[21, 0, 0], suffix="+35.1"
        //      1.0.0-alpha -> parts=[1, 0, 0], suffix="-alpha"

        VersionParts parts1 = parseVersion(v1);
        VersionParts parts2 = parseVersion(v2);

        // 先比较数字部分
        int result = compareParts(parts1.numbers, parts2.numbers);
        if (result != 0) {
            return result;
        }

        // 数字部分相同，比较后缀
        return compareSuffix(parts1.suffix, parts2.suffix);
    }

    /**
     * 解析版本字符串为数字部分和后缀部分
     */
    private VersionParts parseVersion(String version) {
        // 查找第一个非数字、非点号的字符作为后缀起点
        int suffixStart = -1;
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                suffixStart = i;
                break;
            }
        }

        String numberPart;
        String suffix;

        if (suffixStart == -1) {
            // 纯数字版本
            numberPart = version;
            suffix = "";
        } else {
            numberPart = version.substring(0, suffixStart);
            suffix = version.substring(suffixStart);
        }

        // 解析数字部分
        String[] tokens = numberPart.split("\\.");
        int[] numbers = new int[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                numbers[i] = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                numbers[i] = 0;
            }
        }

        return new VersionParts(numbers, suffix);
    }

    /**
     * 比较数字部分数组
     */
    private int compareParts(int[] parts1, int[] parts2) {
        int maxLen = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLen; i++) {
            int n1 = i < parts1.length ? parts1[i] : 0;
            int n2 = i < parts2.length ? parts2[i] : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }

        return 0;
    }

    /**
     * 比较后缀部分
     * 规则：无后缀 > 有后缀（正式版 > 预发布版）
     * 同为后缀时，按字典序比较
     */
    private int compareSuffix(String s1, String s2) {
        if (s1.isEmpty() && s2.isEmpty()) return 0;
        if (s1.isEmpty()) return 1;  // 无后缀（正式版）更大
        if (s2.isEmpty()) return -1;

        // 都有后缀，按字典序
        return s1.compareTo(s2);
    }

    /**
     * 版本部分数据结构
     */
    private static class VersionParts {
        final int[] numbers;
        final String suffix;

        VersionParts(int[] numbers, String suffix) {
            this.numbers = numbers;
            this.suffix = suffix;
        }
    }
}
