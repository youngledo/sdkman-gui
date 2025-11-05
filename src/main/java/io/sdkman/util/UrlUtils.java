package io.sdkman.util;

import javafx.application.HostServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

///
/// # UrlUtils
///
/// Utility class for opening URLs in the system browser
/// URL工具类，用于在系统浏览器中打开URL
///
/// ## Usage
/// ```java
/// UrlUtils.openUrl(hostServices, "https://github.com/youngledo/sdkman-gui");
/// ```
///
/// @since 1.0
///
public class UrlUtils {
    private static final Logger logger = LoggerFactory.getLogger(UrlUtils.class);

    ///
    /// Opens a URL in the system's default browser
    /// 在系统默认浏览器中打开URL
    ///
    /// **Error handling:**
    /// - Shows user-friendly error dialog if opening fails
    /// - Logs warning if HostServices is not available
    ///
    /// @param hostServices JavaFX HostServices instance (can be null)
    /// @param url URL to open (e.g., "https://github.com/youngledo/sdkman-gui")
    ///
    public static void openUrl(HostServices hostServices, String url) {
        logger.info("Opening URL: {}", url);

        if (hostServices != null) {
            try {
                hostServices.showDocument(url);
            } catch (Exception e) {
                logger.error("Failed to open URL: {}", url, e);
                AlertUtils.showErrorAlert(
                    I18nManager.get("error.open_url_title"),
                    I18nManager.get("error.open_url_message")
                );
            }
        } else {
            logger.warn("HostServices not available, cannot open URL: {}", url);
        }
    }

    ///
    /// Opens the SDKMAN GUI GitHub repository in the browser
    /// 在浏览器中打开SDKMANGUI的GitHub仓库
    ///
    /// @param hostServices JavaFX HostServices instance (can be null)
    ///
    public static void openGitHubRepository(HostServices hostServices) {
        openUrl(hostServices, "https://github.com/youngledo/sdkman-gui");
    }

    private UrlUtils() {
        // Utility class, prevent instantiation
    }
}
