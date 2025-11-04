package io.sdkman;

import io.sdkman.service.VersionUpdateService;
import io.sdkman.util.ConfigManager;
import io.sdkman.util.I18nManager;
import io.sdkman.util.ThemeManager;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Toolkit;
import java.io.IOException;

/**
 * SDKMAN主应用类
 * 基于JavaFX 25和AtlantaFX主题
 */
public class SdkmanApplication extends Application {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanApplication.class);

    private static final int WINDOW_WIDTH = 1200;
    private static final int WINDOW_HEIGHT = 800;

    public static HostServices hostServices;

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 字体渲染优化 - 启用更好的字体渲染
        setupFontRendering();
        logger.info("Starting SDKMAN Application");

        // 应用主题
        ThemeManager.applyTheme(ConfigManager.getTheme());

        // 加载主界面
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/fxml/main-view.fxml")
        );
        Parent root = loader.load();

        // 创建场景
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);

        // 设置窗口
        primaryStage.setTitle(I18nManager.get("app.title"));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(WINDOW_WIDTH);
        primaryStage.setMinHeight(WINDOW_HEIGHT);

        // 居中显示
        primaryStage.centerOnScreen();

        // 显示窗口
        primaryStage.show();

        logger.info("Application started successfully");

        hostServices = getHostServices();

        // 启动后台版本检测（异步）
        checkForUpdatesInBackground();
    }

    ///
    /// Checks for application updates in background
    /// 后台检测应用更新
    ///
    private void checkForUpdatesInBackground() {
        Task<VersionUpdateService.UpdateInfo> task = new Task<>() {
            @Override
            protected VersionUpdateService.UpdateInfo call() {
                logger.info("Checking for application updates in background");
                return VersionUpdateService.getInstance().checkForUpdates();
            }
        };

        task.setOnSucceeded(_ -> {
            var updateInfo = task.getValue();
            if (updateInfo.isSuccess() && updateInfo.hasUpdate()) {
                logger.info("Update available: {} -> {}",
                        updateInfo.currentVersion(), updateInfo.latestVersion());
                // 可以在这里显示通知或更新提示
                // 暂时只记录日志，用户可以在设置页面查看
            } else if (updateInfo.isSuccess()) {
                logger.info("Application is up to date: {}", updateInfo.currentVersion());
            } else {
                logger.debug("Update check failed: {}", updateInfo.errorMessage());
            }
        });

        task.setOnFailed(_ -> {
            logger.debug("Failed to check for updates in background", task.getException());
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    @Override
    public void stop() {
        logger.info("Application shutting down");

        // 关闭线程池
        io.sdkman.util.ThreadManager.getInstance().shutdown();
    }

    /**
     * 设置字体渲染优化
     * Configures font rendering optimization
     */
    private void setupFontRendering() {
        try {
            // 启用字体渲染优化（Java 8+）
            System.setProperty("prism.text", "t2k");  // 使用T2K文本引擎
            System.setProperty("prism.lcdtext", "true");  // 启用LCD文本渲染
            System.setProperty("prism.order", "sw");  // 软件渲染模式

            // 高DPI优化
            if (isHighDPI()) {
                System.setProperty("javafx.font.family", "System");
                logger.info("High DPI display detected, using system font rendering");
            }

            logger.debug("Font rendering optimization applied");

        } catch (Exception e) {
            logger.warn("Failed to setup font rendering optimization: {}", e.getMessage());
        }
    }

    /**
     * 检测是否为高DPI显示器
     */
    private boolean isHighDPI() {
        try {
            // 检测DPI缩放
            return Toolkit.getDefaultToolkit().getScreenResolution() > 96;
        } catch (Exception e) {
            logger.debug("Could not detect DPI: {}", e.getMessage());
            return false;
        }
    }

}
