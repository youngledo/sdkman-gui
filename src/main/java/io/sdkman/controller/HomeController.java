package io.sdkman.controller;

import io.sdkman.service.SdkmanService;
import io.sdkman.util.I18nManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 首页控制器
 */
public class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label jdkCountLabel;

    @FXML
    private Label jdkStatLabel;

    @FXML
    private Label sdkCountLabel;

    @FXML
    private Label sdkStatLabel;

    @FXML
    private Label updateCountLabel;

    @FXML
    private Label updateStatLabel;

    @FXML
    private Label quickActionsLabel;

    @FXML
    private Label hintLabel;

    @FXML
    private Button browseJdkButton;

    @FXML
    private Button browseSdkButton;

    @FXML
    private Button checkUpdateButton;

    private final SdkmanService sdkManagerService;
    private java.util.function.Consumer<String> navigationCallback;

    public HomeController() {
        this.sdkManagerService = SdkmanService.getInstance();
    }

    /**
     * 设置导航回调函数
     *
     * @param callback 回调函数
     */
    public void setNavigationCallback(java.util.function.Consumer<String> callback) {
        this.navigationCallback = callback;
    }

    @FXML
    public void initialize() {
        logger.info("Initializing HomeController");

        // 设置国际化文本
        setupI18n();

        // 异步检查SDKMAN是否安装
        checkSdkmanInstallation();

        logger.info("HomeController initialized successfully");
    }

    /**
     * 异步检查SDKMAN安装状态
     */
    private void checkSdkmanInstallation() {
        javafx.concurrent.Task<Boolean> task = new javafx.concurrent.Task<>() {
            @Override
            protected Boolean call() {
                logger.info("Checking SDKMAN installation...");
                return sdkManagerService.isSdkmanInstalled();
            }
        };

        task.setOnSucceeded(event -> {
            boolean installed = task.getValue();
            if (!installed) {
                logger.error("SDKMAN is not installed!");
                showSdkmanNotInstalledError();
            } else {
                // 加载统计数据
                loadStatistics();
            }
        });

        task.setOnFailed(event -> {
            logger.error("Failed to check SDKMAN installation", task.getException());
            showSdkmanNotInstalledError();
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        if (welcomeLabel != null) welcomeLabel.setText(I18nManager.get("home.welcome"));
        if (subtitleLabel != null) subtitleLabel.setText(I18nManager.get("home.subtitle"));
        if (jdkStatLabel != null) jdkStatLabel.setText(I18nManager.get("home.stat.jdk"));
        if (sdkStatLabel != null) sdkStatLabel.setText(I18nManager.get("home.stat.sdk"));
        if (updateStatLabel != null) updateStatLabel.setText(I18nManager.get("home.stat.updates"));
        if (quickActionsLabel != null) quickActionsLabel.setText(I18nManager.get("home.quick_actions"));
        if (hintLabel != null) hintLabel.setText(I18nManager.get("home.hint"));
        if (browseJdkButton != null) browseJdkButton.setText(I18nManager.get("home.action.browse_jdk"));
        if (browseSdkButton != null) browseSdkButton.setText(I18nManager.get("home.action.browse_sdk"));
        if (checkUpdateButton != null) checkUpdateButton.setText(I18nManager.get("home.action.check_update"));
    }

    /**
     * 加载统计数据
     * 性能优化：使用缓存和本地目录扫描，快速加载统计数据
     */
    private void loadStatistics() {
        logger.info("Loading statistics...");

        // 显示加载状态
        jdkCountLabel.setText("...");
        sdkCountLabel.setText("...");
        updateCountLabel.setText("0"); // 更新功能未实现，显示0

        // 异步加载JDK数量
        loadJdkCount();

        // 异步加载SDK数量（现在使用缓存，速度很快）
        loadSdkCount();

        // 更新数量功能未实现，暂时不加载
    }

    /**
     * 加载JDK数量
     */
    private void loadJdkCount() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return sdkManagerService.getInstalledJdkCount();
            }
        };

        task.setOnSucceeded(event -> {
            Integer count = task.getValue();
            jdkCountLabel.setText(String.valueOf(count));
            logger.info("Loaded JDK count: {}", count);
        });

        task.setOnFailed(event -> {
            jdkCountLabel.setText("?");
            logger.error("Failed to load JDK count", task.getException());
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 加载SDK数量
     */
    private void loadSdkCount() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return sdkManagerService.getInstalledSdkCount();
            }
        };

        task.setOnSucceeded(event -> {
            Integer count = task.getValue();
            sdkCountLabel.setText(String.valueOf(count));
            logger.info("Loaded SDK count: {}", count);
        });

        task.setOnFailed(event -> {
            sdkCountLabel.setText("?");
            logger.error("Failed to load SDK count", task.getException());
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 加载更新数量
     */
    private void loadUpdateCount() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return sdkManagerService.getUpdatableCount();
            }
        };

        task.setOnSucceeded(event -> {
            Integer count = task.getValue();
            updateCountLabel.setText(String.valueOf(count));
            logger.info("Loaded update count: {}", count);
        });

        task.setOnFailed(event -> {
            updateCountLabel.setText("?");
            logger.error("Failed to load update count", task.getException());
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 显示SDKMAN未安装错误
     */
    private void showSdkmanNotInstalledError() {
        Platform.runLater(() -> {
            jdkCountLabel.setText("N/A");
            sdkCountLabel.setText("N/A");
            updateCountLabel.setText("N/A");

            // 显示错误对话框
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18nManager.get("error.sdkman_not_installed.title"));
            alert.setHeaderText(null);
            alert.setContentText(I18nManager.get("error.sdkman_not_installed.content"));

            // 添加自定义按钮：打开SDKMAN官网
            javafx.scene.control.ButtonType openWebsiteButton = new javafx.scene.control.ButtonType(
                I18nManager.get("error.sdkman_not_installed.button")
            );
            alert.getButtonTypes().setAll(openWebsiteButton, javafx.scene.control.ButtonType.CANCEL);

            alert.showAndWait().ifPresent(response -> {
                if (response == openWebsiteButton) {
                    // 打开SDKMAN官网
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI("https://sdkman.io/install"));
                    } catch (Exception e) {
                        logger.error("Failed to open website", e);
                    }
                }
            });

            logger.error("Please install SDKMAN first: https://sdkman.io/install");
        });
    }

    /**
     * 浏览JDK按钮点击事件
     */
    @FXML
    private void onBrowseJdkClicked() {
        logger.info("Browse JDK button clicked");
        // 触发主控制器切换到JDK页面
        navigateToJdkPage();
    }

    /**
     * 浏览SDK按钮点击事件
     */
    @FXML
    private void onBrowseSdkClicked() {
        logger.info("Browse SDK button clicked");
        // 触发主控制器切换到SDK页面
        navigateToSdkPage();
    }

    /**
     * 检查更新按钮点击事件
     * 手动刷新所有统计数据，包括SDK数量
     */
    @FXML
    private void onCheckUpdateClicked() {
        logger.info("Check update button clicked - refreshing all statistics");

        // 显示加载状态
        jdkCountLabel.setText("...");
        sdkCountLabel.setText("...");
        updateCountLabel.setText("...");

        // 重新加载JDK数量
        loadJdkCount();

        // 手动加载SDK数量（平时不自动加载）
        loadSdkCount();

        // 加载更新数量
        loadUpdateCount();
    }

    /**
     * 导航到JDK页面
     */
    private void navigateToJdkPage() {
        if (navigationCallback != null) {
            navigationCallback.accept("jdk");
        } else {
            logger.warn("Navigation callback not set");
        }
    }

    /**
     * 导航到SDK页面
     */
    private void navigateToSdkPage() {
        if (navigationCallback != null) {
            navigationCallback.accept("sdk");
        } else {
            logger.warn("Navigation callback not set");
        }
    }
}
