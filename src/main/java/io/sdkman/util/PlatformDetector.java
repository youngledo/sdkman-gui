package io.sdkman.util;

///
/// # PlatformDetector
///
/// Platform detection utility for SDKMAN format
/// 平台检测工具类，用于SDKMAN格式
///
/// ## Features
/// - Detects OS and architecture
/// - Returns SDKMAN-compatible platform identifiers
/// - Provides convenient platform check methods
///
/// ## Example Usage
/// ```java
/// String platform = PlatformDetector.detectPlatform();
/// // Returns: "darwinarm64", "linuxx64", "windowsx64", etc.
///
/// if (PlatformDetector.isMac()) {
///     // macOS-specific code
/// }
/// ```
///
/// @since 1.0
///
public class PlatformDetector {

    private static final String OS_NAME = "os.name";

    ///
    /// Detects current platform in SDKMAN format
    /// 检测当前平台（SDKMAN格式）
    ///
    /// **Supported platforms:**
    /// - `darwinarm64` - macOS on Apple Silicon (M1/M2/M3)
    /// - `darwinx64` - macOS on Intel
    /// - `linuxarm64` - Linux ARM 64-bit
    /// - `linuxarm32hf` - Linux ARM 32-bit hard float
    /// - `linuxx64` - Linux x86_64
    /// - `linuxx32` - Linux x86 32-bit
    /// - `windowsx64` - Windows x86_64
    /// - `windowsx32` - Windows x86 32-bit
    /// - `universalx64` - Other Unix-like systems
    ///
    /// @return Platform identifier (e.g., "darwinarm64")
    ///
    public static String detectPlatform() {
        var os = System.getProperty(OS_NAME).toLowerCase();
        var arch = System.getProperty("os.arch").toLowerCase();

        var osPrefix = detectOS(os);
        var archSuffix = detectArch(arch);

        return osPrefix + archSuffix;
    }

    ///
    /// Detects operating system from OS name
    /// 检测操作系统
    ///
    /// @param os Operating system name (lowercase)
    /// @return OS prefix: `windows`, `darwin`, `linux`, or `universal`
    ///
    private static String detectOS(String os) {
        if (os.contains("win")) return "windows";
        if (os.contains("mac")) return "darwin";
        if (os.contains("nux")) return "linux";
        return "universal";
    }

    ///
    /// Detects CPU architecture from arch name
    /// 检测CPU架构
    ///
    /// @param arch Architecture name (lowercase)
    /// @return Architecture suffix: `arm64`, `arm32hf`, `x64`, or `x32`
    ///
    private static String detectArch(String arch) {
        if (arch.contains("aarch64") || arch.contains("arm64")) return "arm64";
        if (arch.contains("arm")) return "arm32hf";
        if (arch.contains("x86_64") || arch.contains("amd64")) return "x64";
        if (arch.contains("i386") || arch.contains("i686")) return "x32";
        return "x64"; // 默认
    }

    ///
    /// Checks if current OS is Windows
    /// 判断是否为Windows系统
    ///
    /// @return `true` if Windows, `false` otherwise
    ///
    public static boolean isWindows() {
        var os = System.getProperty(OS_NAME).toLowerCase();
        return os.contains("win");
    }

    ///
    /// Checks if current OS is macOS
    /// 判断是否为macOS系统
    ///
    /// @return `true` if macOS, `false` otherwise
    ///
    public static boolean isMac() {
        var os = System.getProperty(OS_NAME).toLowerCase();
        return os.contains("mac");
    }

    ///
    /// Checks if current OS is Linux
    /// 判断是否为Linux系统
    ///
    /// @return `true` if Linux, `false` otherwise
    ///
    public static boolean isLinux() {
        var os = System.getProperty(OS_NAME).toLowerCase();
        return os.contains("nux");
    }

    ///
    /// Gets the operating system name
    /// 获取操作系统名称
    ///
    /// @return OS name (e.g., "Mac OS X", "Windows 11", "Linux")
    ///
    public static String getOSName() {
        return System.getProperty(OS_NAME);
    }

    ///
    /// Gets the CPU architecture name
    /// 获取CPU架构名称
    ///
    /// @return Architecture name (e.g., "aarch64", "x86_64", "amd64")
    ///
    public static String getArchName() {
        return System.getProperty("os.arch");
    }

    public static String userHome() {
        return System.getProperty("user.home");
    }
}
