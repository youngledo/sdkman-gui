package io.sdkman.service;

import io.sdkman.model.JdkCategory;
import io.sdkman.model.SdkVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SdkmanHttpClient 版本列表解析单元测试
 * 覆盖SDKMAN API的两种Java表格格式（4列现行格式、6列旧格式）以及其他SDK的空格分隔格式
 */
class SdkmanHttpClientVersionsParseTest {

    private final SdkmanHttpClient client = new SdkmanHttpClient();

    ///
    /// 现行4列格式：Vendor | Use | Version | Identifier
    /// Use列标记：`>` 使用中、`*` 已安装、`+` 仅本地
    ///
    @Test
    void testParseCurrentFourColumnJavaFormat() {
        var tableText = """
                ================================================================================
                Available Java Versions for macOS ARM 64bit
                ================================================================================
                 Vendor         | Use | Version            | Identifier
                --------------------------------------------------------------------------------
                 Corretto       |     | 27.0.0             | 27.0.0-amzn
                                |     | 26.0.2             | 26.0.2-amzn
                 GraalVM CE     | > * | 25.3.4+1.r25       | 25.3.4+1.r25-graalce
                 Temurin        |   + | 26.0.1             | 26.0.1-tem
                 Zulu           |   * | 27.0.0-fx+35       | 27.0.0-fx+35-zulu
                ================================================================================
                 > in use   * installed   + local only
                --------------------------------------------------------------------------------
                 $ sdk install java <Identifier>    install a specific version
                ================================================================================
                """;

        List<SdkVersion> versions = client.parseVersionsCsv(tableText, "java");

        assertEquals(5, versions.size());

        // 第一行：带vendor的普通版本
        var corrtto = versions.getFirst();
        assertEquals("Corretto", corrtto.getVendor());
        assertEquals("27.0.0", corrtto.getVersion());
        assertEquals("27.0.0-amzn", corrtto.getIdentifier());
        assertFalse(corrtto.isInstalled());
        assertFalse(corrtto.isInUse());

        // 第二行：vendor为空，应继承上一行的vendor
        var corretto26 = versions.get(1);
        assertEquals("Corretto", corretto26.getVendor(), "空vendor应继承上一行的vendor");
        assertEquals("26.0.2-amzn", corretto26.getIdentifier());

        // 第三行：> * 使用中且已安装
        var graalce = versions.get(2);
        assertEquals("GraalVM CE", graalce.getVendor());
        assertTrue(graalce.isInUse());
        assertTrue(graalce.isInstalled());
        assertTrue(graalce.isDefault());
        assertTrue(graalce.hasCategory(JdkCategory.NIK), "graalce标识符应识别为NIK分类");

        // 第四行：+ 仅本地，也应视为已安装
        var temurin = versions.get(3);
        assertTrue(temurin.isInstalled(), "+ local only应视为已安装");
        assertFalse(temurin.isInUse());

        // 第五行：* 已安装
        var zuluFx = versions.get(4);
        assertTrue(zuluFx.isInstalled());
        assertFalse(zuluFx.isInUse());
        assertTrue(zuluFx.hasCategory(JdkCategory.JAVAFX), "fx标识符应识别为JavaFX分类");
    }

    ///
    /// 旧6列格式：Vendor | Use | Version | Dist | Status | Identifier
    ///
    @Test
    void testParseLegacySixColumnJavaFormat() {
        var tableText = """
                ================================================================================
                 Vendor      | Use | Version | Dist | Status   | Identifier
                --------------------------------------------------------------------------------
                 Temurin     |     | 21.0.2  | tem  |          | 21.0.2-tem
                             | > * | 17.0.9  | tem  | installed | 17.0.9-tem
                 Amazon      |     | 21.0.3  | amzn | installed | 21.0.3-amzn
                ================================================================================
                """;

        List<SdkVersion> versions = client.parseVersionsCsv(tableText, "java");

        assertEquals(3, versions.size());

        var temurin21 = versions.getFirst();
        assertEquals("Temurin", temurin21.getVendor());
        assertEquals("21.0.2-tem", temurin21.getIdentifier());
        assertFalse(temurin21.isInstalled());

        var temurin17 = versions.get(1);
        assertEquals("Temurin", temurin17.getVendor());
        assertTrue(temurin17.isInUse());
        assertTrue(temurin17.isInstalled(), "旧格式Status列的installed标记应生效");

        var amazon21 = versions.get(2);
        assertEquals("Amazon", amazon21.getVendor());
        assertEquals("21.0.3-amzn", amazon21.getIdentifier());
        assertTrue(amazon21.isInstalled());
        assertFalse(amazon21.isInUse());
    }

    ///
    /// 其他SDK格式（空格分隔，无|分隔符）不应被误判为Java格式
    ///
    @Test
    void testParseOtherSdkFormat() {
        var tableText = """
                ================================================================================
                Available Maven Versions for macOS ARM 64bit
                ================================================================================
                 > * 3.9.9              3.9.8              3.9.7
                     3.9.6              3.9.5
                ================================================================================
                """;

        List<SdkVersion> versions = client.parseVersionsCsv(tableText, "maven");

        assertEquals(5, versions.size());

        var inUse = versions.getFirst();
        assertEquals("3.9.9", inUse.getVersion());
        assertTrue(inUse.isInUse());
        assertTrue(inUse.isInstalled());

        // 后续版本不应继承行首标记
        assertFalse(versions.get(1).isInstalled());
        assertFalse(versions.get(1).isInUse());
    }

    @Test
    void testParseEmptyResponse() {
        assertTrue(client.parseVersionsCsv("", "java").isEmpty());
        assertTrue(client.parseVersionsCsv(null, "java").isEmpty());
    }

    ///
    /// 基于真实API完整响应（2026-09抓取，src/test/resources/java-versions-api-response.txt）的回归测试
    /// installed=21.0.10-tem,26.0.1-tem,25.3.4+1.r25-graalce,27.0.0-fx+35-zulu&current=25.3.4+1.r25-graalce
    ///
    @Test
    void testParseRealApiResponse() throws Exception {
        var text = Files.readString(Path.of("src/test/resources/java-versions-api-response.txt"));

        List<SdkVersion> versions = client.parseVersionsCsv(text, "java");

        assertEquals(89, versions.size(), "数据行总数");

        // 15个供应商：Corretto, GraalVM CE, GraalVM Oracle, Java.net, JetBrains, Liberica,
        // Liberica NIK, Mandrel, Microsoft, Oracle, SapMachine, Semeru, Temurin, Tencent, Zulu
        assertEquals(15, versions.stream().map(SdkVersion::getVendor).distinct().count(), "供应商数量");
        assertTrue(versions.stream().allMatch(v -> v.getVendor() != null && !v.getVendor().isEmpty()),
                "所有版本的vendor都应非空");

        var installed = versions.stream()
                .filter(SdkVersion::isInstalled)
                .map(SdkVersion::getIdentifier)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(
                "21.0.10-tem", "26.0.1-tem", "25.3.4+1.r25-graalce", "27.0.0-fx+35-zulu"), installed);

        var inUse = versions.stream()
                .filter(SdkVersion::isInUse)
                .map(SdkVersion::getIdentifier)
                .toList();
        assertEquals(List.of("25.3.4+1.r25-graalce"), inUse);

        assertEquals(16, versions.stream().filter(v -> v.hasCategory(JdkCategory.JAVAFX)).count(),
                "JavaFX分类数量（含.fx与-fx两种标识符形式）");
        assertEquals(11, versions.stream().filter(v -> v.hasCategory(JdkCategory.NIK)).count(),
                "NIK分类数量");

        assertTrue(versions.stream().noneMatch(v -> v.getVersion().contains("|")),
                "不应有包含|的垃圾条目");
    }
}
