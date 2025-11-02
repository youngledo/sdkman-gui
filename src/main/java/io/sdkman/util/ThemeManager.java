package io.sdkman.util;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 主题管理器 - 负责应用AtlantaFX主题
 * 支持自动检测系统主题（macOS和Windows）
 */
public class ThemeManager {
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    /**
     * 应用主题
     *
     * @param themePreference 主题偏好: "light", "dark", "auto"
     */
    public static void applyTheme(String themePreference) {
        logger.info("Applying theme with preference: {}", themePreference);

        String actualTheme = switch (themePreference) {
            case "light" -> "light";
            case "dark" -> "dark";
            case "auto" -> detectSystemTheme();
            default -> "light";
        };

        logger.info("Actual theme to apply: {}", actualTheme);

        if ("dark".equals(actualTheme)) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }
    }

    /**
     * 检测系统主题
     *
     * @return "dark" 或 "light"
     */
    private static String detectSystemTheme() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            return detectMacOSTheme();
        } else if (os.contains("win")) {
            return detectWindowsTheme();
        } else {
            // Linux等其他系统默认使用亮色主题
            logger.info("Auto theme detection not supported on {}, using light theme", os);
            return "light";
        }
    }

    /**
     * 检测macOS系统主题
     *
     * @return "dark" 或 "light"
     */
    private static String detectMacOSTheme() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "defaults", "read", "-g", "AppleInterfaceStyle"
            });

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );
            String result = reader.readLine();
            process.waitFor();

            if (result != null && result.trim().equalsIgnoreCase("Dark")) {
                logger.info("Detected macOS dark mode");
                return "dark";
            } else {
                logger.info("Detected macOS light mode");
                return "light";
            }

        } catch (Exception e) {
            // 命令执行失败说明未设置暗色模式，使用亮色主题
            logger.debug("Failed to detect macOS theme, defaulting to light: {}", e.getMessage());
            return "light";
        }
    }

    /**
     * 检测Windows系统主题
     *
     * @return "dark" 或 "light"
     */
    private static String detectWindowsTheme() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "reg", "query",
                    "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "/v", "AppsUseLightTheme"
            });

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("AppsUseLightTheme")) {
                    // 格式: "AppsUseLightTheme    REG_DWORD    0x0"
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length >= 3) {
                        String value = parts[parts.length - 1];
                        if ("0x0".equals(value) || "0".equals(value)) {
                            logger.info("Detected Windows dark mode");
                            return "dark";
                        }
                    }
                }
            }

            process.waitFor();
            logger.info("Detected Windows light mode");
            return "light";

        } catch (Exception e) {
            logger.debug("Failed to detect Windows theme, defaulting to light: {}", e.getMessage());
            return "light";
        }
    }
}
