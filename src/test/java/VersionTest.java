import io.sdkman.service.VersionUpdateService;

/**
 * Simple test to check version detection from JAR
 */
public class VersionTest {
    public static void main(String[] args) {
        var service = VersionUpdateService.getInstance();
        var currentVersion = service.getCurrentVersion();

        System.out.println("=== Version Detection Test ===");
        System.out.println("Current version: " + currentVersion);

        // Check Package info
        var pkg = VersionUpdateService.class.getPackage();
        System.out.println("Package: " + pkg);
        if (pkg != null) {
            System.out.println("Implementation Title: " + pkg.getImplementationTitle());
            System.out.println("Implementation Version: " + pkg.getImplementationVersion());
            System.out.println("Implementation Vendor: " + pkg.getImplementationVendor());
        }

        System.out.println("=== Test Complete ===");
    }
}