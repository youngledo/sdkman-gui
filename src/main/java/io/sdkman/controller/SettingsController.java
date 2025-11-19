package io.sdkman.controller;

import io.sdkman.SdkmanApplication;
import io.sdkman.service.VersionUpdateService;
import io.sdkman.util.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 设置页面控制器
 */
public class SettingsController {
    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private Label themeLabel;

    @FXML
    private ToggleGroup themeGroup;

    @FXML
    private RadioButton themeLightRadio;

    @FXML
    private RadioButton themeDarkRadio;

    @FXML
    private RadioButton themeAutoRadio;

    @FXML
    private Label languageLabel;

    @FXML
    private ToggleGroup languageGroup;

    @FXML
    private RadioButton languageEnRadio;

    @FXML
    private RadioButton languageZhRadio;

    @FXML
    private Label proxyLabel;

    @FXML
    private ToggleGroup proxyTypeGroup;

    @FXML
    private RadioButton proxyNoneRadio;

    @FXML
    private RadioButton proxyAutoRadio;

    @FXML
    private RadioButton proxyManualRadio;

    @FXML
    private VBox proxyManualBox;

    @FXML
    private Label proxyHostLabel;

    @FXML
    private TextField proxyHostField;

    @FXML
    private Label proxyPortLabel;

    @FXML
    private TextField proxyPortField;

    @FXML
    private Label sdkmanPathLabel;

    @FXML
    private TextField sdkmanPathField;

    @FXML
    private Button browseButton;

    // 版本更新相关字段
    @FXML
    private Label appVersionLabel;

    @FXML
    private Label currentVersionLabel;

    @FXML
    private Label currentVersionValue;

    @FXML
    private Button checkUpdateButton;

    @FXML
    private Label updateStatusLabel;

    @FXML
    private VBox updateAvailableBox;

    @FXML
    private Label newVersionLabel;

    @FXML
    private Label latestVersionLabel;

    @FXML
    private Label latestVersionValue;

    @FXML
    private Hyperlink downloadManualLink;

    @FXML
    private Button downloadButton;

    @FXML
    private Label downloadStatusLabel;

    @FXML
    private ProgressBar downloadProgressBar;

    @FXML
    private VBox downloadProgressBox;

    @FXML
    private Label downloadProgressLabel;

    @FXML
    private HBox installBox;

    @FXML
    private Button installButton;

    @FXML
    private Label installStatusLabel;

    private final VersionUpdateService versionUpdateService;
    private java.io.File downloadedInstaller;

    public SettingsController() {
        this.versionUpdateService = VersionUpdateService.getInstance();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing SettingsController");

        // 设置国际化文本
        setupI18n();

        // 加载当前配置
        loadSettings();

        // 创建 ToggleGroup（在加载配置后创建，避免重置选择）
        setupToggleGroups();

        // 设置代理类型切换监听
        setupProxyTypeListener();
    }

    /**
     * 创建 ToggleGroup
     */
    private void setupToggleGroups() {
        // 创建主题 ToggleGroup
        themeGroup = new ToggleGroup();
        themeLightRadio.setToggleGroup(themeGroup);
        themeDarkRadio.setToggleGroup(themeGroup);
        themeAutoRadio.setToggleGroup(themeGroup);

        languageGroup = new ToggleGroup();
        languageEnRadio.setToggleGroup(languageGroup);
        languageZhRadio.setToggleGroup(languageGroup);

        // 创建代理类型 ToggleGroup
        proxyTypeGroup = new ToggleGroup();
        proxyNoneRadio.setToggleGroup(proxyTypeGroup);
        proxyAutoRadio.setToggleGroup(proxyTypeGroup);
        proxyManualRadio.setToggleGroup(proxyTypeGroup);
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        titleLabel.setText(I18nManager.get("settings.title"));
        subtitleLabel.setText(I18nManager.get("settings.subtitle"));
        themeLabel.setText(I18nManager.get("settings.theme"));
        themeLightRadio.setText(I18nManager.get("settings.theme.light"));
        themeDarkRadio.setText(I18nManager.get("settings.theme.dark"));
        themeAutoRadio.setText(I18nManager.get("settings.theme.auto"));
        languageLabel.setText(I18nManager.get("settings.language"));
        languageEnRadio.setText(I18nManager.get("settings.language.english"));
        languageZhRadio.setText(I18nManager.get("settings.language.chinese"));
        proxyLabel.setText(I18nManager.get("settings.proxy"));
        proxyNoneRadio.setText(I18nManager.get("settings.proxy.none"));
        proxyAutoRadio.setText(I18nManager.get("settings.proxy.auto"));
        proxyManualRadio.setText(I18nManager.get("settings.proxy.manual"));
        proxyHostLabel.setText(I18nManager.get("settings.proxy.host"));
        proxyPortLabel.setText(I18nManager.get("settings.proxy.port"));
        proxyHostField.setPromptText(I18nManager.get("settings.proxy.host.placeholder"));
        proxyPortField.setPromptText(I18nManager.get("settings.proxy.port.placeholder"));
        sdkmanPathLabel.setText(I18nManager.get("settings.sdkman_path"));
        browseButton.setText(I18nManager.get("settings.sdkman_browse"));

        // 版本更新相关文本
        if (appVersionLabel != null) {
            appVersionLabel.setText(I18nManager.get("settings.app_version"));
        }
        if (currentVersionLabel != null) {
            currentVersionLabel.setText(I18nManager.get("settings.current_version"));
        }
        if (currentVersionValue != null) {
            currentVersionValue.setText(versionUpdateService.getCurrentVersion());
        }
        if (checkUpdateButton != null) {
            checkUpdateButton.setText(I18nManager.get("settings.check_update"));
        }
        if (newVersionLabel != null) {
            newVersionLabel.setText(I18nManager.get("settings.new_version"));
        }
        if (latestVersionLabel != null) {
            latestVersionLabel.setText(I18nManager.get("settings.latest_version"));
        }
        if (downloadManualLink != null) {
            downloadManualLink.setText(I18nManager.get("settings.download_manual"));
        }
        if (downloadButton != null) {
            downloadButton.setText(I18nManager.get("settings.download_update"));
        }
        if (installButton != null) {
            installButton.setText(I18nManager.get("settings.install_update"));
        }
    }

    /**
     * 加载当前设置
     */
    private void loadSettings() {
        // 加载主题设置
        String theme = ConfigManager.getTheme();
        switch (theme) {
            case "light" -> themeLightRadio.setSelected(true);
            case "dark" -> themeDarkRadio.setSelected(true);
            default -> themeAutoRadio.setSelected(true);
        }

        // 加载语言设置
        Locale savedLocale = ConfigManager.getSavedLocale();
        if (savedLocale == null || savedLocale.equals(Locale.SIMPLIFIED_CHINESE)) {
            languageZhRadio.setSelected(true);
        } else {
            languageEnRadio.setSelected(true);
        }

        // 加载代理设置
        String proxyType = ConfigManager.getProxyType();
        switch (proxyType) {
            case "none" -> proxyNoneRadio.setSelected(true);
            case "auto" -> proxyAutoRadio.setSelected(true);
            default -> {
                proxyManualRadio.setSelected(true);
                proxyManualBox.setVisible(true);
                proxyManualBox.setManaged(true);
            }
        }

        proxyHostField.setText(ConfigManager.getProxyHost());
        proxyPortField.setText(ConfigManager.getProxyPort());

        // 加载其他设置
        sdkmanPathField.setText(ConfigManager.getSdkmanPath());
    }

    /**
     * 设置代理类型切换监听
     */
    private void setupProxyTypeListener() {
        proxyTypeGroup.selectedToggleProperty().addListener((_, _, newToggle) -> {
            if (newToggle == proxyManualRadio) {
                proxyManualBox.setVisible(true);
                proxyManualBox.setManaged(true);
            } else {
                proxyManualBox.setVisible(false);
                proxyManualBox.setManaged(false);
            }

            // 立即保存代理类型设置
            saveProxyType();
        });

        // 监听代理主机和端口变化，立即保存
        proxyHostField.textProperty().addListener((_, _, _) -> saveProxySettings());
        proxyPortField.textProperty().addListener((_, _, _) -> saveProxySettings());

        // 监听语言变化，立即保存并切换语言
        languageGroup.selectedToggleProperty().addListener((_, _, _) -> saveLanguage());

        // 监听主题变化，立即保存并切换主题
        themeGroup.selectedToggleProperty().addListener((_, _, _) -> saveTheme());
    }

    /**
     * 保存代理类型
     */
    private void saveProxyType() {
        String proxyType = "";
        if (proxyNoneRadio.isSelected()) {
            proxyType = "none";
        } else if (proxyAutoRadio.isSelected()) {
            proxyType = "auto";
        } else if (proxyManualRadio.isSelected()) {
            proxyType = "manual";
        }

        if (!proxyType.isEmpty()) {
            ConfigManager.setProxyType(proxyType);
            logger.info("Saved proxy type: {}", proxyType);
        }
    }

    /**
     * 保存代理设置（主机和端口）
     */
    private void saveProxySettings() {
        String proxyHost = proxyHostField.getText().trim();
        String proxyPort = proxyPortField.getText().trim();

        ConfigManager.setProxyHost(proxyHost);
        ConfigManager.setProxyPort(proxyPort);
        logger.info("Saved proxy settings: {}:{}", proxyHost, proxyPort);
    }

    /**
     * 浏览选择SDKMAN路径
     */
    @FXML
    private void onBrowseClicked() {
        try {
            logger.info("Opening SDKMAN path browser");

            // 获取当前路径作为初始目录
            String currentPath = sdkmanPathField.getText().trim();
            if (currentPath.isEmpty()) {
                currentPath = PlatformDetector.userHome();
            }

            java.io.File initialDir = new java.io.File(currentPath);
            if (!initialDir.exists()) {
                initialDir = new java.io.File(PlatformDetector.userHome());
            }

            // 创建目录选择器
            javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
            directoryChooser.setTitle(I18nManager.get("settings.sdkman_browse_title"));
            directoryChooser.setInitialDirectory(initialDir);

            // 显示话框
            java.io.File selectedDirectory = directoryChooser.showDialog(browseButton.getScene().getWindow());

            if (selectedDirectory != null) {
                String selectedPath = selectedDirectory.getAbsolutePath();
                sdkmanPathField.setText(selectedPath);
                logger.info("Selected SDKMAN path: {}", selectedPath);
            }

        } catch (Exception e) {
            logger.error("Failed to open directory chooser", e);
            // 显示错误消息
            Alert alert = new Alert(
                    Alert.AlertType.ERROR);
            alert.setTitle(I18nManager.get("message.error"));
            alert.setHeaderText(I18nManager.get("settings.browse_error"));
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * 保存语言设置并切换语言
     */
    private void saveLanguage() {
        Locale newLocale = null;
        if (languageZhRadio.isSelected()) {
            newLocale = Locale.SIMPLIFIED_CHINESE;
        } else if (languageEnRadio.isSelected()) {
            newLocale = Locale.US;
        }

        if (newLocale != null && !newLocale.equals(I18nManager.getCurrentLocale())) {
            ConfigManager.saveLocale(newLocale);
            I18nManager.setLocale(newLocale);
            logger.info("Language changed to: {}", newLocale);
            // 设置国际化文本
            setupI18n();
        }
    }

    /**
     * 保存主题设置并切换主题
     */
    private void saveTheme() {
        String theme = "";
        if (themeLightRadio.isSelected()) {
            theme = "light";
        } else if (themeDarkRadio.isSelected()) {
            theme = "dark";
        } else if (themeAutoRadio.isSelected()) {
            theme = "auto";
        }

        if (!theme.isEmpty() && !theme.equals(ConfigManager.getTheme())) {
            ConfigManager.saveTheme(theme);
            ThemeManager.applyTheme(theme);
        }
    }

    ///
    /// Handles check update button click
    /// 处理检查更新按钮点击事件
    ///
    @FXML
    private void onCheckUpdateClicked() {
        // 禁用按钮，显示检查中状态
        checkUpdateButton.setDisable(true);
        updateStatusLabel.setText(I18nManager.get("settings.checking_update"));
        updateStatusLabel.getStyleClass().removeAll("success", "danger");

        // 隐藏更新提示框
        if (updateAvailableBox != null) {
            updateAvailableBox.setVisible(false);
            updateAvailableBox.setManaged(false);
        }

        // 异步检查更新
        Task<VersionUpdateService.UpdateInfo> task = getUpdateInfoTask();

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    private Task<VersionUpdateService.UpdateInfo> getUpdateInfoTask() {
        Task<VersionUpdateService.UpdateInfo> task = new Task<>() {
            @Override
            protected VersionUpdateService.UpdateInfo call() {
                return versionUpdateService.checkForUpdates();
            }
        };

        task.setOnSucceeded(_ -> {
            var updateInfo = task.getValue();
            Platform.runLater(() -> {
                checkUpdateButton.setDisable(false);
                handleUpdateInfo(updateInfo);
            });
        });

        task.setOnFailed(_ -> Platform.runLater(() -> {
            checkUpdateButton.setDisable(false);
            updateStatusLabel.setText(I18nManager.get("settings.check_update_failed"));
            updateStatusLabel.getStyleClass().add("danger");
            logger.error("Failed to check for updates", task.getException());
        }));
        return task;
    }

    ///
    /// Handles update information display
    /// 处理更新信息显示
    ///
    private void handleUpdateInfo(VersionUpdateService.UpdateInfo updateInfo) {
        if (!updateInfo.isSuccess()) {
            updateStatusLabel.setText(I18nManager.get("settings.check_update_failed") + ": " + updateInfo.errorMessage());
            updateStatusLabel.getStyleClass().add("danger");
            return;
        }

        if (updateInfo.hasUpdate()) {
            // 有新版本可用
            updateStatusLabel.setText(I18nManager.get("settings.update_available"));
            updateStatusLabel.getStyleClass().add("success");

            // 显示新版本信息
            if (updateAvailableBox != null) {
                updateAvailableBox.setVisible(true);
                updateAvailableBox.setManaged(true);

                if (latestVersionValue != null) {
                    latestVersionValue.setText(updateInfo.latestVersion());
                }

                if (downloadManualLink != null && updateInfo.releaseUrl() != null) {
                    downloadManualLink.setOnAction(_ -> {
                        if (SdkmanApplication.hostServices != null) {
                            SdkmanApplication.hostServices.showDocument(updateInfo.releaseUrl());
                        }
                    });
                }

                // 设置下载按钮的可用性
                if (downloadButton != null) {
                    downloadButton.setDisable(updateInfo.downloadUrl() == null);
                    if (updateInfo.downloadUrl() == null) {
                        downloadButton.setText(I18nManager.get("settings.download_update") + " (不可用)");
                    }
                }

                // 重置下载状态
                if (downloadStatusLabel != null) {
                    downloadStatusLabel.setText("");
                    downloadStatusLabel.getStyleClass().removeAll("success", "danger");
                }
                if (downloadProgressBox != null) {
                    downloadProgressBox.setVisible(false);
                    downloadProgressBox.setManaged(false);
                }
                if (installBox != null) {
                    installBox.setVisible(false);
                    installBox.setManaged(false);
                }
                downloadedInstaller = null;
            }

            logger.info("Update available: {} -> {}", updateInfo.currentVersion(), updateInfo.latestVersion());
        } else {
            // 已是最新版本
            updateStatusLabel.setText(I18nManager.get("settings.up_to_date"));
            updateStatusLabel.getStyleClass().add("success");
            logger.info("Application is up to date: {}", updateInfo.currentVersion());
        }
    }

    ///
    /// Handles download button click
    /// 处理下载按钮点击事件
    ///
    @FXML
    private void onDownloadClicked() {
        logger.info("Download button clicked");

        // 重新检查更新以获取最新信息
        Task<VersionUpdateService.UpdateInfo> task = new Task<>() {
            @Override
            protected VersionUpdateService.UpdateInfo call() {
                return versionUpdateService.checkForUpdates();
            }
        };

        task.setOnSucceeded(_ -> {
            var updateInfo = task.getValue();
            if (updateInfo.isSuccess() && updateInfo.hasUpdate() && updateInfo.downloadUrl() != null) {
                startDownload(updateInfo.downloadUrl());
            } else {
                Platform.runLater(() -> {
                    downloadStatusLabel.setText(I18nManager.get("settings.download_failed"));
                    downloadStatusLabel.getStyleClass().add("danger");
                });
            }
        });

        task.setOnFailed(_ -> Platform.runLater(() -> {
            downloadStatusLabel.setText(I18nManager.get("settings.download_failed"));
            downloadStatusLabel.getStyleClass().add("danger");
        }));

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    ///
    /// Starts downloading the update installer
    /// 开始下载更新安装包
    ///
    private void startDownload(String downloadUrl) {
        Platform.runLater(() -> {
            downloadButton.setDisable(true);
            downloadStatusLabel.setText(I18nManager.get("settings.downloading"));

            // 显示进度框
            if (downloadProgressBox != null) {
                downloadProgressBox.setVisible(true);
                downloadProgressBox.setManaged(true);
            }

            if (downloadProgressBar != null) {
                downloadProgressBar.setProgress(0);
            }

            if (downloadProgressLabel != null) {
                downloadProgressLabel.setText("0%");
            }
        });

        versionUpdateService.downloadUpdate(downloadUrl, new VersionUpdateService.DownloadProgressCallback() {
            @Override
            public void onProgress(long bytesDownloaded, long totalBytes, int percentage) {
                Platform.runLater(() -> {
                    if (downloadProgressBar != null) {
                        downloadProgressBar.setProgress(percentage / 100.0);
                    }

                    if (downloadProgressLabel != null) {
                        // 格式化下载进度信息
                        var downloadedMB = bytesDownloaded / (1024.0 * 1024.0);
                        var totalMB = totalBytes > 0 ? totalBytes / (1024.0 * 1024.0) : 0;

                        if (totalBytes > 0) {
                            downloadProgressLabel.setText(
                                    String.format("%.1f MB / %.1f MB (%d%%)", downloadedMB, totalMB, percentage)
                            );
                        } else {
                            downloadProgressLabel.setText(
                                    String.format("%.1f MB (%d%%)", downloadedMB, percentage)
                            );
                        }
                    }
                });
            }

            @Override
            public void onCompleted(java.io.File downloadedFile) {
                Platform.runLater(() -> {
                    downloadedInstaller = downloadedFile;
                    downloadStatusLabel.setText(I18nManager.get("settings.download_completed"));
                    downloadStatusLabel.getStyleClass().removeAll("danger");
                    downloadStatusLabel.getStyleClass().add("success");

                    // 隐藏进度框
                    if (downloadProgressBox != null) {
                        downloadProgressBox.setVisible(false);
                        downloadProgressBox.setManaged(false);
                    }

                    // 显示安装按钮
                    if (installBox != null) {
                        installBox.setVisible(true);
                        installBox.setManaged(true);
                        if (installStatusLabel != null) {
                            installStatusLabel.setText(I18nManager.get("settings.restart_required"));
                        }
                    }

                    logger.info("Download completed: {}", downloadedFile.getAbsolutePath());
                });
            }

            @Override
            public void onFailed(String errorMessage) {
                Platform.runLater(() -> {
                    downloadStatusLabel.setText(I18nManager.get("settings.download_failed") + ": " + errorMessage);
                    downloadStatusLabel.getStyleClass().add("danger");

                    // 隐藏进度框
                    if (downloadProgressBox != null) {
                        downloadProgressBox.setVisible(false);
                        downloadProgressBox.setManaged(false);
                    }

                    downloadButton.setDisable(false);
                });
            }
        });
    }

    ///
    /// Handles install button click
    /// 处理安装按钮点击事件
    ///
    @FXML
    private void onInstallClicked() {
        if (downloadedInstaller == null || !downloadedInstaller.exists()) {
            logger.error("No installer file available for installation");
            return;
        }

        Platform.runLater(() -> {
            installButton.setDisable(true);
            installStatusLabel.setText(I18nManager.get("settings.installing"));
        });

        // 在后台线程启动安装程序
        new Thread(() -> {
            try {
                var success = versionUpdateService.installUpdate(downloadedInstaller);
                if (success) {
                    // 显示确认对话框，然后退出应用
                    Platform.runLater(this::showInstallConfirmation);
                } else {
                    Platform.runLater(() -> {
                        installStatusLabel.setText(I18nManager.get("settings.install_failed"));
                        installButton.setDisable(false);
                    });
                }
            } catch (Exception e) {
                logger.error("Failed to start installer", e);
                Platform.runLater(() -> {
                    installStatusLabel.setText(I18nManager.get("settings.install_failed"));
                    installButton.setDisable(false);
                });
            }
        }, "Install-Thread").start();
    }

    ///
    /// Shows installation confirmation dialog
    /// 显示安装确认对话框
    ///
    private void showInstallConfirmation() {
        Platform.runLater(() -> AlertUtils.showInfoAlert(
                I18nManager.get("alert.info"),
                I18nManager.get("settings.install_confirmation"), true
        ));
    }

}
