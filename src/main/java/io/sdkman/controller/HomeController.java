package io.sdkman.controller;

import io.sdkman.service.SdkmanService;
import io.sdkman.util.AlertUtils;
import io.sdkman.util.I18nManager;
import io.sdkman.util.UrlUtils;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 首页控制器
 */
public class HomeController {
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    @FXML
    private Label welcomePrefixLabel;

    @FXML
    private Label welcomeMark;

    @FXML
    private Hyperlink appNameLink;

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
    private HostServices hostServices;

    public HomeController() {
        this.sdkManagerService = SdkmanService.getInstance();
    }

    ///
    /// Sets the HostServices for opening URLs in the browser
    /// 设置HostServices用于在浏览器中打开URL
    ///
    /// @param hostServices JavaFX HostServices instance
    ///
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
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
        // 设置国际化文本
        setupI18n();

        // 异步检查SDKMAN是否安装
        checkSdkmanInstallation();
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

        task.setOnSucceeded(_ -> {
            boolean installed = task.getValue();
            if (!installed) {
                logger.error("SDKMAN is not installed!");
                showSdkmanNotInstalledError();
            } else {
                // 加载统计数据
                loadStatistics();
            }
        });

        task.setOnFailed(_ -> {
            logger.error("Failed to check SDKMAN installation", task.getException());
            showSdkmanNotInstalledError();
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        // 设置欢迎文本: "欢迎使用 " + "SDKMAN GUI"(可点击链接)
        welcomePrefixLabel.setText(I18nManager.get("home.welcome_prefix"));
        welcomeMark.setText(I18nManager.get("home.welcome_mark"));
        appNameLink.setText(I18nManager.get("app.title"));
        subtitleLabel.setText(I18nManager.get("home.subtitle"));
        jdkStatLabel.setText(I18nManager.get("home.stat.jdk"));
        sdkStatLabel.setText(I18nManager.get("home.stat.sdk"));
        updateStatLabel.setText(I18nManager.get("home.stat.updates"));
        quickActionsLabel.setText(I18nManager.get("home.quick_actions"));
        hintLabel.setText(I18nManager.get("home.hint"));
        browseJdkButton.setText(I18nManager.get("home.action.browse_jdk"));
        browseSdkButton.setText(I18nManager.get("home.action.browse_sdk"));
        checkUpdateButton.setText(I18nManager.get("home.action.check_update"));
    }

    /**
     * 加载统计数据
     * 性能优化：JDK和SDK数量使用本地目录扫描，更新检测异步进行
     */
    private void loadStatistics() {
        logger.info("Loading statistics...");

        // 显示加载状态
        jdkCountLabel.setText("...");
        sdkCountLabel.setText("...");
        updateCountLabel.setText("..."); // 异步检测中

        // 异步加载JDK数量
        loadJdkCount();

        // 异步加载SDK数量
        loadSdkCount();

        // 异步检测更新数量
        loadUpdateCount();
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

        task.setOnSucceeded(_ -> {
            Integer count = task.getValue();
            jdkCountLabel.setText(String.valueOf(count));
            logger.info("Loaded JDK count: {}", count);
        });

        task.setOnFailed(_ -> {
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

        task.setOnSucceeded(_ -> {
            Integer count = task.getValue();
            sdkCountLabel.setText(String.valueOf(count));
            logger.info("Loaded SDK count: {}", count);
        });

        task.setOnFailed(_ -> {
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

        task.setOnSucceeded(_ -> {
            Integer count = task.getValue();
            updateCountLabel.setText(String.valueOf(count));
            logger.info("Loaded update count: {}", count);
        });

        task.setOnFailed(_ -> {
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

            // 添加自定义按钮：打开SDKMAN官网
            ButtonType openWebsiteButton = new ButtonType(
                    I18nManager.get("error.sdkman_not_installed.button")
            );
            AlertUtils.showErrorAlert(
                    I18nManager.get("error.sdkman_not_installed.title"),
                    I18nManager.get("error.sdkman_not_installed.content"),
                    openWebsiteButton,
                    buttonType -> {
                        if (buttonType == openWebsiteButton) {
                            // 打开SDKMAN官网
                            try {
                                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://sdkman.io/install"));
                            } catch (Exception e) {
                                logger.error("Failed to open website", e);
                            }
                        }
                    }
            );
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

    ///
    /// Handles the app name link click event to open GitHub repository
    /// 处理应用名称链接点击事件，打开GitHub仓库
    ///
    @FXML
    private void onAppNameLinkClicked() {
        UrlUtils.openGitHubRepository(hostServices);
    }

    @FXML
    private void onCheckUpdateClicked() {
        logger.info("Check update button clicked - refreshing all statistics");

        // 显示加载状态
        jdkCountLabel.setText("...");
        sdkCountLabel.setText("...");
        updateCountLabel.setText("..."); // 检查中

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
