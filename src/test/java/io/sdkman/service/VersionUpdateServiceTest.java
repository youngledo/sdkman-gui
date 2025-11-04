package io.sdkman.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test version reading functionality
 */
public class VersionUpdateServiceTest {

    @Test
    public void testGetCurrentVersion() {
        var service = VersionUpdateService.getInstance();
        var currentVersion = service.getCurrentVersion();

        System.out.println("Current version detected: " + currentVersion);

        // Version should not be null or empty
        assertNotNull(currentVersion, "Current version should not be null");
        assertFalse(currentVersion.trim().isEmpty(), "Current version should not be empty");

        // Version should follow semantic versioning pattern (x.y.z)
        assertTrue(currentVersion.matches("\\d+\\.\\d+\\.\\d+.*"),
                  "Version should follow semantic versioning pattern: " + currentVersion);
    }

    @Test
    public void testPackageInfo() {
        var pkg = VersionUpdateService.class.getPackage();

        System.out.println("Package: " + pkg);
        if (pkg != null) {
            System.out.println("Implementation Title: " + pkg.getImplementationTitle());
            System.out.println("Implementation Version: " + pkg.getImplementationVersion());
            System.out.println("Implementation Vendor: " + pkg.getImplementationVendor());
        }
    }
}