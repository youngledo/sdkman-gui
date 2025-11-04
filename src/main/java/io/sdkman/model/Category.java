package io.sdkman.model;

import java.util.Set;

/**
 * SDK分类枚举
 * 基于SDKMAN候选列表的智能分类
 */
public enum Category {
    ALL("all"),
    LANGUAGES("languages"),
    BUILD_TOOLS("build_tools"),
    FRAMEWORKS("frameworks"),
    SERVERS("servers"),
    TOOLS("tools"),
    MQ("mq"),
    OTHER("other");

    private final String key;

    Category(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    // 预定义的分类映射
    private static final Set<String> LANGUAGES_CANDIDATES = Set.of(
            "java", "kotlin", "scala", "groovy", "clojure", "jruby", "jython",
            "ceylon", "ballerina", "crash", "sbl"
    );

    private static final Set<String> BUILD_TOOLS_CANDIDATES = Set.of(
            "maven", "mvnd", "gradle", "sbt", "ant", "bld"
    );

    private static final Set<String> FRAMEWORKS_CANDIDATES = Set.of(
            "springboot", "micronaut", "quarkus", "vertx"
    );

    private static final Set<String> SERVERS_CANDIDATES = Set.of(
            "tomcat", "jetty", "payara", "wildfly", "tomee", "liberty",
            "glowroot", "tackle", "akka"
    );

    private static final Set<String> MQ_CANDIDATES = Set.of(
            "activemq", "kcctl"
    );

    private static final Set<String> TOOLS_CANDIDATES = Set.of(
            "visualvm", "jmc", "jmeter", "gatling", "selenide", "cucumber", "testng", "mockito", "asciidoctorj",
            "jbang", "lombok", "cdi", "jakartaee", "leiningen", "helidon",
            "apache", "zookeeper", "consul", "etcd", "redis"
    );

    /**
     * 根据候选名称自动分类
     * 基于候选名称和描述的智能分类
     */
    public static Category fromName(String name, String description) {
        if (name == null || name.isEmpty()) {
            return OTHER;
        }

        var normalized = name.toLowerCase();

        // 直接匹配
        if (LANGUAGES_CANDIDATES.contains(normalized)) {
            return LANGUAGES;
        }
        if (BUILD_TOOLS_CANDIDATES.contains(normalized)) {
            return BUILD_TOOLS;
        }
        if (FRAMEWORKS_CANDIDATES.contains(normalized)) {
            return FRAMEWORKS;
        }
        if (SERVERS_CANDIDATES.contains(normalized)) {
            return SERVERS;
        }
        if (TOOLS_CANDIDATES.contains(normalized)) {
            return TOOLS;
        }
        if (MQ_CANDIDATES.contains(normalized)) {
            return MQ;
        }

        // 基于描述的关键词匹配
        if (description != null) {
            var descLower = description.toLowerCase();

            // 语言相关关键词
            if (descLower.matches(".*(?:programming language|jvm language|language|编译器).*")) {
                return LANGUAGES;
            }

            // 构建工具相关关键词
            if (descLower.matches(".*(?:build tool|build automation|project management|构建).*")) {
                return BUILD_TOOLS;
            }

            // 框架相关关键词
            if (descLower.matches(".*(?:framework|microframework|application framework).*")) {
                return FRAMEWORKS;
            }

            // 服务器相关关键词
            if (descLower.matches(".*(?:server|application server|servlet container|web server).*")) {
                return SERVERS;
            }

            // 工具相关关键词
            if (descLower.matches(".*(?:tool|utility|monitoring|testing|debugging|documentation).*")) {
                return TOOLS;
            }
        }

        // 基于候选名称模式的最后匹配
        if (normalized.matches(".*(?:lang|language).*")) {
            return LANGUAGES;
        }
        if (normalized.matches(".*(?:build|gradle|maven|ant).*")) {
            return BUILD_TOOLS;
        }
        if (normalized.matches(".*(?:framework|spring|micro|quarkus).*")) {
            return FRAMEWORKS;
        }
        if (normalized.matches(".*(?:server|tomcat|jetty|wildfly).*")) {
            return SERVERS;
        }

        return OTHER;
    }

    /**
     * 兼容性方法：仅基于名称分类
     */
    public static Category fromName(String name) {
        return fromName(name, null);
    }

    /**
     * 获取显示名称（用于国际化）
     */
    public String getDisplayNameKey() {
        return "sdk.category." + key;
    }

    /**
     * 获取分类描述
     */
    public String getDescriptionKey() {
        return "sdk.category." + key + ".description";
    }
}