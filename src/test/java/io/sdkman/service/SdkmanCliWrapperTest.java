package io.sdkman.service;

import io.sdkman.model.SdkVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * SdkmanCliWrapper测试类
 */
public class SdkmanCliWrapperTest {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanCliWrapperTest.class);

    private SdkmanCliWrapper wrapper;

    @BeforeEach
    public void setUp() {
        wrapper = new SdkmanCliWrapper();
    }

    @Test
    public void testIsSdkmanInstalled() {
        boolean installed = wrapper.isSdkmanInstalled();
        logger.info("SDKMAN installed: {}", installed);

        if (!installed) {
            logger.warn("SDKMAN is not installed! Please install it first.");
            logger.info("Visit: https://sdkman.io/install");
        }
    }

    @Test
    public void testListJavaVersions() {
        List<SdkVersion> versions = wrapper.listVersions("java");
        logger.info("Found {} Java versions", versions.size());

        // 显示前10个版本
        versions.stream()
                .limit(10)
                .forEach(v -> logger.info("  - {} ({}), installed: {}, inUse: {}",
                        v.getVersion(), v.getVendor(), v.isInstalled(), v.isInUse()));
    }

    @Test
    public void testGetCurrentJavaVersion() {
        String version = wrapper.getCurrentVersion("java");
        logger.info("Current Java version: {}", version);
    }

    // 注意：以下测试会实际安装/卸载SDK，请谨慎运行
    // @Test
    // public void testInstallAndUninstall() {
    //     String candidate = "maven";
    //     String version = "3.9.9";
    //
    //     // 安装
    //     boolean installed = wrapper.install(candidate, version);
    //     logger.info("Install result: {}", installed);
    //
    //     // 卸载
    //     boolean uninstalled = wrapper.uninstall(candidate, version);
    //     logger.info("Uninstall result: {}", uninstalled);
    // }
}
