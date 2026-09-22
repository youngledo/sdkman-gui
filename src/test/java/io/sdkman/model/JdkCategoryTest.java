package io.sdkman.model;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JdkCategory 单元测试
 */
class JdkCategoryTest {

    @Test
    void testStandardNik() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-nik");
        assertTrue(categories.contains(JdkCategory.NIK));
        assertFalse(categories.contains(JdkCategory.JAVAFX));
    }

    @Test
    void testJavaFxWithNik() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1.fx-nik");
        assertTrue(categories.contains(JdkCategory.NIK));
        assertTrue(categories.contains(JdkCategory.JAVAFX));
    }

    @Test
    void testLibericaNik() {
        // Liberica NIK 应该以 -nik 结尾
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-nik");
        assertTrue(categories.contains(JdkCategory.NIK),
                "Liberica NIK should be recognized as NIK");
    }

    @Test
    void testRegularLiberica() {
        // 普通 Liberica 不应该是 NIK
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-librca");
        assertFalse(categories.contains(JdkCategory.NIK),
                "Regular Liberica should not be recognized as NIK");
        assertTrue(categories.contains(JdkCategory.JDK));
    }

    @Test
    void testLibericaNikWithJavaFx() {
        // Liberica NIK with JavaFX (这个标识符格式可能需要根据实际情况调整)
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("21.0.9.fx-nik");
        assertTrue(categories.contains(JdkCategory.NIK),
                "Liberica NIK with JavaFX should be recognized as NIK");
        assertTrue(categories.contains(JdkCategory.JAVAFX),
                "Should also be recognized as JavaFX");
    }

    @Test
    void testLibericaWithJavaFx() {
        // 普通 Liberica with JavaFX (不应该是 NIK)
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("21.0.9.fx-librca");
        assertTrue(categories.contains(JdkCategory.JAVAFX),
                "Should be recognized as JavaFX");
        assertFalse(categories.contains(JdkCategory.NIK),
                "Regular Liberica with JavaFX should not be recognized as NIK");
    }

    @Test
    void testMandrel() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-mandrel");
        assertTrue(categories.contains(JdkCategory.NIK),
                "Mandrel should be recognized as NIK");
    }

    @Test
    void testGraalVmCe() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-graal");
        assertTrue(categories.contains(JdkCategory.NIK),
                "GraalVM CE should be recognized as NIK");
    }

    @Test
    void testGraalVmOracle() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-graalvm");
        assertTrue(categories.contains(JdkCategory.NIK),
                "GraalVM Oracle should be recognized as NIK");
    }

    @Test
    void testRegularJdk() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.1-tem");
        assertFalse(categories.contains(JdkCategory.NIK),
                "Regular JDK should not be recognized as NIK");
        assertTrue(categories.contains(JdkCategory.JDK));
    }

    @Test
    void testJavaFxOnly() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("21.0.9.fx-zulu");
        assertTrue(categories.contains(JdkCategory.JAVAFX),
                "Should be recognized as JavaFX");
        assertFalse(categories.contains(JdkCategory.NIK),
                "Should not be recognized as NIK");
    }

    @Test
    void testNewDashFxJavaFxOnly() {
        // 新版SDKMAN标识符的JavaFX形式：-fx（如27.0.0-fx+35-zulu）
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("27.0.0-fx+35-zulu");
        assertTrue(categories.contains(JdkCategory.JAVAFX),
                "-fx标识符应识别为JavaFX分类");
        assertFalse(categories.contains(JdkCategory.NIK));
    }

    @Test
    void testNewDashFxLiberica() {
        // 新版Liberica的JavaFX标识符：26.0.2-fx+1.1-librca
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("26.0.2-fx+1.1-librca");
        assertTrue(categories.contains(JdkCategory.JAVAFX));
        assertFalse(categories.contains(JdkCategory.NIK));
    }

    @Test
    void testCracIsNotJavaFx() {
        // -crac标识符不应被误判为JavaFX
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("27.0.0-crac+35-zulu");
        assertFalse(categories.contains(JdkCategory.JAVAFX),
                "-crac不应识别为JavaFX分类");
        assertTrue(categories.contains(JdkCategory.JDK));
    }

    @Test
    void testNewDashFxDotRNik() {
        // 新版NIK的JavaFX标识符：25.0.4-fx+1.1.r25-nik
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("25.0.4-fx+1.1.r25-nik");
        assertTrue(categories.contains(JdkCategory.JAVAFX));
        assertTrue(categories.contains(JdkCategory.NIK));
    }

    @Test
    void testEmptyIdentifier() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier("");
        assertEquals(1, categories.size());
        assertTrue(categories.contains(JdkCategory.JDK));
    }

    @Test
    void testNullIdentifier() {
        Set<JdkCategory> categories = JdkCategory.fromIdentifier(null);
        assertEquals(1, categories.size());
        assertTrue(categories.contains(JdkCategory.JDK));
    }

    @Test
    void testCaseInsensitive() {
        // 测试大小写不敏感
        Set<JdkCategory> categories1 = JdkCategory.fromIdentifier("25.0.1-GRAAL");
        Set<JdkCategory> categories2 = JdkCategory.fromIdentifier("25.0.1-graal");
        assertEquals(categories1, categories2,
                "Case should not matter in identifier matching");
    }
}
