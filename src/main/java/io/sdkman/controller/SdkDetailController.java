package io.sdkman.controller;

import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.service.SdkmanService;
import io.sdkman.util.I18nManager;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * SDK详情页面控制器
 */
public class SdkDetailController {
    private static final Logger logger = LoggerFactory.getLogger(SdkDetailController.class);

    @FXML
    private Label titleLabel;

    @FXML
    private Label descriptionLabel;

    @FXML
    private VBox websiteBox;
    @FXML
    private VBox archBox;

    @FXML
    private Hyperlink websiteLink;
    @FXML
    private Label architectures;
    @FXML
    private Label websiteLabel;
    @FXML
    private Label architectureLabel;
    @FXML
    private Button refreshVersionsButton;
    @FXML
    private Label versionLabel;
    @FXML
    private VBox versionsLoadingPane;
    @FXML
    private Label loadingLabel;

    @FXML
    private ListView<SdkVersion> versionsListView;

    private final SdkmanService sdkmanService;
    private final VersionListManager<SdkVersion> versionListManager;
    private Sdk currentSdk;
    private Consumer<Void> onBackCallback;
    private HostServices hostServices;

    public SdkDetailController() {
        this.sdkmanService = SdkmanService.getInstance();
        this.versionListManager = new VersionListManager<>();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing SdkDetailController");

        setupI18n();

        // 设置版本列表的Cell工厂
        versionsListView.setCellFactory(_ -> new SdkVersionCell());

        // 性能优化：启用缓存
        versionsListView.setCache(true);
        versionsListView.setCacheHint(javafx.scene.CacheHint.SPEED);

        // 性能优化：设置固定Cell高度（简化版本列表只显示版本号，高度固定为60px）
        versionsListView.setFixedCellSize(60);

        // 禁用选择模式和焦点，减少不必要的渲染
        versionsListView.setSelectionModel(null);
        versionsListView.setFocusTraversable(false);

        // 使用CSS优化滚动性能
        versionsListView.setStyle("-fx-background-insets: 0; -fx-padding: 0;");

        logger.info("SdkDetailController initialized successfully");
    }

    /**
     * 设置要显示的SDK
     */
    public void setSdk(Sdk sdk) {
        this.currentSdk = sdk;
        loadSdkDetails();
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        websiteLabel.setText(I18nManager.get("sdk.detail.website.label"));
        architectureLabel.setText(I18nManager.get("sdk.detail.architecture.label"));
        versionLabel.setText(I18nManager.get("sdk.detail.version.label"));
        refreshVersionsButton.setText(I18nManager.get("sdk.detail.refresh.label"));
        loadingLabel.setText(I18nManager.get("list.message.loading"));
    }

    /**
     * 设置返回回调
     */
    public void setOnBackCallback(Consumer<Void> callback) {
        this.onBackCallback = callback;
    }

    /**
     * 设置HostServices用于打开网页
     */
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /**
     * 加载SDK详情
     */
    private void loadSdkDetails() {
        if (currentSdk == null) return;

        titleLabel.setText(currentSdk.getName());

        // 描述（如果有）
        if (currentSdk.getDescription() != null && !currentSdk.getDescription().isEmpty()) {
            descriptionLabel.setText(currentSdk.getDescription());
        } else {
            descriptionLabel.setText(I18nManager.get("sdk.detail.description.empty"));
            descriptionLabel.setStyle("-fx-font-style: italic;");
        }

        // 官方网址（如果有）
        if (currentSdk.getWebsite() != null && !currentSdk.getWebsite().isEmpty()) {
            websiteLink.setText(currentSdk.getWebsite());
            websiteLink.setOnAction(e -> {
                if (hostServices != null) {
                    hostServices.showDocument(currentSdk.getWebsite());
                }
            });
            websiteBox.setVisible(true);
            websiteBox.setManaged(true);
        } else {
            websiteBox.setVisible(false);
            websiteBox.setManaged(false);
        }
        if (currentSdk.getArchitectures() == null || currentSdk.getArchitectures().isEmpty()) {
            archBox.setVisible(false);
            archBox.setManaged(false);
        } else {
            archBox.setVisible(true);
            archBox.setManaged(true);
            architectures.setText(String.join("\n", currentSdk.getArchitectures()));
        }


        // 加载版本列表
        loadVersions();
    }

    /**
     * 加载版本列表
     *
     */
    private void loadVersions() {
        showVersionsLoading(true);

        Task<List<SdkVersion>> task = new Task<>() {
            @Override
            protected List<SdkVersion> call() {
                return sdkmanService.loadSdkVersions(currentSdk.getCandidate());
            }
        };

        task.setOnSucceeded(event -> {
            List<SdkVersion> versions = task.getValue();
            logger.info("Loaded {} versions for {}", versions.size(), currentSdk.getCandidate());

            // 为每个版本设置candidate，确保安装/卸载/设置默认操作使用正确的SDK类型
            for (SdkVersion version : versions) {
                version.setCandidate(currentSdk.getCandidate());
            }

            // 按版本号降序排序（最新版本在前）
            versions.sort((v1, v2) ->
                io.sdkman.util.VersionComparator.descending().compare(v1.getVersion(), v2.getVersion())
            );

            Platform.runLater(() -> {
                showVersionsLoading(false);
                if (versions.isEmpty()) {
                    showVersionsEmpty(true);
                } else {
                    showVersionsEmpty(false);
                    versionsListView.getItems().setAll(versions);
                }
            });
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load versions", task.getException());
            Platform.runLater(() -> {
                showVersionsLoading(false);
                showVersionsEmpty(true);
            });
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 显示/隐藏加载状态
     */
    private void showVersionsLoading(boolean show) {
        versionsLoadingPane.setVisible(show);
        // ListView始终显示，加载状态覆盖在上面
        versionsListView.setVisible(true);

        // 如果正在加载，清空ListView内容避免显示旧数据
        if (show) {
            versionsListView.getItems().clear();
        }
    }

    /**
     * 显示/隐藏空状态
     */
    private void showVersionsEmpty(boolean show) {
        versionsListView.setVisible(!show);
        versionsListView.setManaged(!show);
    }

    /**
     * 返回按钮点击事件
     */
    @FXML
    private void onBackClicked() {
        if (onBackCallback != null) {
            onBackCallback.accept(null);
        }
    }

    /**
     * 刷新版本列表（强制从网络刷新）
     */
    @FXML
    private void onRefreshVersions() {
        logger.info("Refresh clicked, force refresh from network");
        loadVersions();  // 强制刷新
    }

    /**
     * SDK版本列表Cell
     * 使用组件重用机制，避免频繁创建UI
     */
    private class SdkVersionCell extends ListCell<SdkVersion> {
        // 缓存当前Cell绑定的版本对象，避免重复创建UI
        private SdkVersion currentVersion = null;

        @Override
        protected void updateItem(SdkVersion version, boolean empty) {
            super.updateItem(version, empty);

            if (empty || version == null) {
                setGraphic(null);
                currentVersion = null;
                return;
            }

            // 只有当绑定的版本对象变化时才重新创建UI
            // 这样可以避免同一个版本的状态变化（安装中）触发UI重建
            if (currentVersion != version) {
                logger.debug("Creating new cell for version: {} (cache miss)", version.getVersion());
                // 使用通用的VersionListCellFactory创建cell，SDK只显示版本号，不显示identifier
                setGraphic(VersionListCellFactory.createVersionCell(
                        version,
                        SdkDetailController.this::handleInstall,
                        SdkDetailController.this::handleUninstall,
                        SdkDetailController.this::handleSetDefault,
                        null,   // 不需要特殊CSS样式
                        false   // SDK只显示版本号，不显示identifier
                ));
                currentVersion = version;
            }
            // 如果是同一个版本对象，不做任何操作，让Property绑定自动更新UI
        }
    }

    /**
     * 处理安装
     */
    private void handleInstall(SdkVersion version) {
        versionListManager.handleInstall(
                version,
                versionsListView.getItems(),
                currentSdk.getName() + " " + version.getVersion(),
                // 成功回调
                installedVersion -> {
                    logger.info("Successfully installed {} {}",
                               currentSdk.getCandidate(), installedVersion.getVersion());

                    // 检查是否是唯一版本，如果是则自动设置为默认版本
                    if (sdkmanService.isOnlyInstalledVersion(currentSdk.getCandidate(), installedVersion.getVersion())) {
                        logger.info("Detected that {} {} is the only installed version, setting as default",
                                   currentSdk.getCandidate(), installedVersion.getVersion());

                        if (sdkmanService.setDefaultForOnlyVersion(currentSdk.getCandidate(), installedVersion.getVersion())) {
                            logger.info("Successfully set {} {} as default version",
                                       currentSdk.getCandidate(), installedVersion.getVersion());

                            // 延迟一下再强制刷新，确保SDKMAN已经完全更新默认版本状态
                            Platform.runLater(() -> {
                                // 再等待一小段时间确保CLI状态完全更新
                                new Thread(() -> {
                                    try {
                                        Thread.sleep(500); // 等待500ms确保CLI状态更新完成
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                    }
                                    // 强制刷新以获取最新的默认状态
                                    Platform.runLater(this::loadVersions);
                                }).start();
                            });
                        } else {
                            logger.warn("Failed to set {} {} as default version",
                                        currentSdk.getCandidate(), installedVersion.getVersion());
                        }
                    }
                },
                () -> versionsListView.refresh()
        );
    }

    /**
     * 处理卸载
     */
    private void handleUninstall(SdkVersion version) {
        versionListManager.handleUninstall(
                version,
                versionsListView.getItems(),
                currentSdk.getName() + " " + version.getVersion(),
                // 成功回调
                uninstalledVersion -> {
                    logger.info("Successfully uninstalled {} {}",
                               currentSdk.getCandidate(), uninstalledVersion.getVersion());
                },
                () -> versionsListView.refresh()
        );
    }

    /**
     * 处理设置默认版本
     */
    private void handleSetDefault(SdkVersion version) {
        logger.info("Setting default version: {} {}", currentSdk.getCandidate(), version.getVersion());

        versionListManager.handleSetDefault(
                version,
                versionsListView.getItems(),
                currentSdk.getName() + " " + version.getVersion(),
                // 成功回调：记录日志
                _ -> {
                    logger.info("Successfully set {} {} as default", currentSdk.getCandidate(), version.getVersion());
                },
                // UI刷新回调
                () -> {
                    // 强制刷新ListView，确保UI更新
                    versionsListView.refresh();
                    logger.debug("Refreshed versions list after setting default");
                }
        );
    }
}
