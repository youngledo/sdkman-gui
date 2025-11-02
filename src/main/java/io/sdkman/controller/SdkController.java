package io.sdkman.controller;

import atlantafx.base.theme.Styles;
import io.sdkman.SdkmanApplication;
import io.sdkman.model.Category;
import io.sdkman.model.Sdk;
import io.sdkman.service.SdkmanService;
import io.sdkman.util.I18nManager;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;

/**
 * SDK管理页面控制器
 */
public class SdkController {
    private static final Logger logger = LoggerFactory.getLogger(SdkController.class);

    @FXML
    private StackPane rootContainer;

    @FXML
    private VBox listView;

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private TextField searchField;

    @FXML
    private Button refreshButton;

    @FXML
    private Button allCategoryButton;

    @FXML
    private Button buildToolsCategoryButton;

    @FXML
    private Button languagesCategoryButton;

    @FXML
    private Button frameworksCategoryButton;

    @FXML
    private Button serversCategoryButton;

    @FXML
    private Button toolsCategoryButton;

    @FXML
    private Button otherCategoryButton;

    @FXML
    private FlowPane sdkGridPane;

    @FXML
    private VBox loadingPane;

    @FXML
    private Label loadingLabel;

    @FXML
    private VBox emptyPane;

    @FXML
    private Label emptyTitleLabel;

    @FXML
    private Label emptySubtitleLabel;

    private final SdkmanService sdkmanService;
    private List<Sdk> allSdks;
    private Category currentCategory = Category.ALL;

    public SdkController() {
        this.sdkmanService = SdkmanService.getInstance();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing SdkController");

        // 设置国际化文本
        setupI18n();

        // 设置搜索监听
        setupSearchListener();

        // 加载SDK列表
        loadSdkList();

        logger.info("SdkController initialized successfully");
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        titleLabel.setText(I18nManager.get("sdk.title"));
        subtitleLabel.setText(I18nManager.get("sdk.subtitle"));
        searchField.setPromptText(I18nManager.get("sdk.search.placeholder"));
        refreshButton.setText(I18nManager.get("sdk.action.refresh"));

        allCategoryButton.setText(I18nManager.get("sdk.category.all"));
        languagesCategoryButton.setText(I18nManager.get("sdk.category.languages"));

        buildToolsCategoryButton.setText(I18nManager.get("sdk.category.build_tools"));

        frameworksCategoryButton.setText(I18nManager.get("sdk.category.frameworks"));
        serversCategoryButton.setText(I18nManager.get("sdk.category.servers"));
        toolsCategoryButton.setText(I18nManager.get("sdk.category.tools"));
        otherCategoryButton.setText(I18nManager.get("sdk.category.other"));

        loadingLabel.setText(I18nManager.get("list.message.loading"));
        if (emptyTitleLabel != null) emptyTitleLabel.setText(I18nManager.get("sdk.empty.title"));
        if (emptySubtitleLabel != null) emptySubtitleLabel.setText(I18nManager.get("sdk.empty.subtitle"));
    }

    /**
     * 设置搜索监听
     */
    private void setupSearchListener() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldText, newText) -> {
                applyFilters();
            });
        }
    }

    /**
     * 加载SDK列表
     */
    private void loadSdkList() {
        loadSdkList(false);
    }

    /**
     * 加载SDK列表
     *
     * @param forceRefresh 是否强制刷新（忽略缓存）
     */
    private void loadSdkList(boolean forceRefresh) {
        showLoading(true);

        Task<List<Sdk>> task = new Task<>() {
            @Override
            protected List<Sdk> call() {
                logger.info("Loading SDK list (forceRefresh: {})...", forceRefresh);
                return sdkmanService.getAllSdks(forceRefresh);
            }
        };

        task.setOnSucceeded(event -> {
            allSdks = task.getValue();
            logger.info("Loaded {} SDKs", allSdks.size());
            showLoading(false);
            applyFilters();
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load SDK list", task.getException());
            showLoading(false);
            InstallationHandler.showErrorAlert(
                    I18nManager.get("alert.error"),
                    "Failed to load SDK list"
            );
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 应用过滤器
     */
    private void applyFilters() {
        if (allSdks == null) return;

        var searchTerm = searchField != null ? searchField.getText().toLowerCase().trim() : "";

        Predicate<Sdk> categoryFilter = sdk -> currentCategory == Category.ALL ||
                sdk.getCategory() == currentCategory;

        Predicate<Sdk> searchFilter = sdk -> searchTerm.isEmpty() ||
                sdk.getCandidate().toLowerCase().contains(searchTerm) ||
                sdk.getName().toLowerCase().contains(searchTerm) ||
                (sdk.getDescription() != null && sdk.getDescription().toLowerCase().contains(searchTerm));

        var filteredSdks = allSdks.stream()
                .filter(categoryFilter.and(searchFilter))
                .toList();

        displaySdks(filteredSdks);
    }

    /**
     * 显示SDK列表
     */
    private void displaySdks(List<Sdk> sdks) {
        if (sdkGridPane == null) return;

        logger.info("displaySdks called with {} SDKs", sdks.size());

        sdkGridPane.getChildren().clear();

        if (sdks.isEmpty()) {
            showEmpty(true);
            return;
        }

        showEmpty(false);

        for (var sdk : sdks) {
            var sdkItem = createSdkItem(sdk);
            sdkGridPane.getChildren().add(sdkItem);
        }

        logger.info("displaySdks completed, sdkGridPane now has {} children", sdkGridPane.getChildren().size());
    }

    /**
     * 创建SDK项（与JDK页面样式一致）
     */
    private VBox createSdkItem(Sdk sdk) {
        logger.info("Creating SDK item for: {} ({})", sdk.getName(), sdk.getCandidate());
        var item = new VBox(8);
        item.setPrefWidth(280);
        item.setMaxWidth(280);
        item.setPadding(new javafx.geometry.Insets(12));

        // 设置鼠标悬停样式和点击事件
        item.setOnMouseEntered(e -> item.setStyle("-fx-cursor: hand;"));
        item.setOnMouseExited(e -> item.setStyle("-fx-cursor: default;"));

        // 点击事件 - 导航到详情页
        item.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                showSdkDetail(sdk);
            }
        });

        // SDK名称行
        var nameBox = new HBox(8);
        nameBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        var nameLabel = new Label(sdk.getName());
        nameLabel.getStyleClass().addAll(Styles.TITLE_4);
        nameLabel.setWrapText(true);
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

        // 已安装标记（使用异步实时检查）
        var statusLabel = new Label();
        statusLabel.getStyleClass().addAll(Styles.SUCCESS);
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        // 异步检查安装状态（使用与详情页一致的检查方法）
        logger.info("Starting installation status check for SDK: {}", sdk.getCandidate());
        Task<Boolean> checkTask = new Task<>() {
            @Override
            protected Boolean call() {
                logger.debug("Executing installation status check for SDK: {}", sdk.getCandidate());
                return sdkmanService.isSdkInstalledViaListCommand(sdk.getCandidate());
            }
        };

        checkTask.setOnSucceeded(event -> {
            boolean isInstalled = checkTask.getValue();
            logger.info("Installation status check completed for {}: {}", sdk.getCandidate(), isInstalled ? "installed" : "not installed");
            if (isInstalled) {
                statusLabel.setText("✓");
                statusLabel.setVisible(true);
                statusLabel.setManaged(true);
                logger.debug("Showing installed status for {}", sdk.getCandidate());
            } else {
                statusLabel.setVisible(false);
                statusLabel.setManaged(false);
                logger.debug("Hiding installed status for {}", sdk.getCandidate());
            }
        });

        checkTask.setOnFailed(event -> {
            // 检查失败时隐藏标记
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
            logger.error("Failed to check installation status for {}", sdk.getCandidate(), checkTask.getException());
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(checkTask);

        // 初始隐藏标记，等待检查结果
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        nameBox.getChildren().addAll(nameLabel, statusLabel);

        // 分类标签和版本信息
        var infoBox = new HBox(12);
        infoBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        var categoryLabel = new Label(I18nManager.get(sdk.getCategory().getDisplayNameKey()));
        categoryLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        if (sdk.getLatestVersion() != null && !sdk.getLatestVersion().isEmpty()) {
            var versionLabel = new Label("v" + sdk.getLatestVersion());
            versionLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);
            infoBox.getChildren().addAll(categoryLabel, new Label("•"), versionLabel);
        } else {
            infoBox.getChildren().add(categoryLabel);
        }

        // 组装项目
        item.getChildren().addAll(nameBox, infoBox);

        return item;
    }

    
    /**
     * 显示/隐藏加载状态
     */
    private void showLoading(boolean show) {
        if (loadingPane != null) {
            loadingPane.setVisible(show);
            loadingPane.setManaged(show);
        }
        if (sdkGridPane != null) {
            sdkGridPane.setVisible(!show);
            sdkGridPane.setManaged(!show);
        }
    }

    /**
     * 显示/隐藏空状态
     */
    private void showEmpty(boolean show) {
        if (emptyPane != null) {
            emptyPane.setVisible(show);
            emptyPane.setManaged(show);
        }
        if (sdkGridPane != null) {
            sdkGridPane.setVisible(!show);
            sdkGridPane.setManaged(!show);
        }
    }

    /**
     * 显示错误信息
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * 刷新按钮点击事件（强制从网络刷新）
     */
    @FXML
    private void onRefreshClicked() {
        logger.info("Refresh clicked, force refresh from network");
        loadSdkList(true);
    }

    /**
     * 分类按钮点击事件
     */
    @FXML
    private void onAllCategoryClicked() {
        setActiveCategory(Category.ALL, allCategoryButton);
    }

    @FXML
    private void onBuildToolsCategoryClicked() {
        setActiveCategory(Category.BUILD_TOOLS, buildToolsCategoryButton);
    }

    @FXML
    private void onLanguagesCategoryClicked() {
        setActiveCategory(Category.LANGUAGES, languagesCategoryButton);
    }

    @FXML
    private void onFrameworksCategoryClicked() {
        setActiveCategory(Category.FRAMEWORKS, frameworksCategoryButton);
    }

    @FXML
    private void onServersCategoryClicked() {
        setActiveCategory(Category.SERVERS, serversCategoryButton);
    }

    @FXML
    private void onToolsCategoryClicked() {
        setActiveCategory(Category.TOOLS, toolsCategoryButton);
    }

    @FXML
    private void onOtherCategoryClicked() {
        setActiveCategory(Category.OTHER, otherCategoryButton);
    }

    /**
     * 设置激活的分类
     */
    private void setActiveCategory(Category category, Button activeButton) {
        currentCategory = category;

        // 更新按钮样式 - 移除所有按钮的激活状态
        allCategoryButton.getStyleClass().remove("filter-chip-active");
        languagesCategoryButton.getStyleClass().remove("filter-chip-active");
        buildToolsCategoryButton.getStyleClass().remove("filter-chip-active");
        frameworksCategoryButton.getStyleClass().remove("filter-chip-active");
        serversCategoryButton.getStyleClass().remove("filter-chip-active");
        toolsCategoryButton.getStyleClass().remove("filter-chip-active");
        otherCategoryButton.getStyleClass().remove("filter-chip-active");

        // 添加激活状态到当前按钮
        activeButton.getStyleClass().add("filter-chip-active");

        // 应用过滤
        applyFilters();
    }

    /**
     * 显示SDK详情页
     */
    private void showSdkDetail(Sdk sdk) {
        try {
            logger.info("Navigating to SDK detail page for: {}", sdk.getCandidate());

            // 加载详情页FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sdk-detail-view.fxml"));
            Parent detailView = loader.load();

            // 获取详情页控制器
            SdkDetailController detailController = loader.getController();
            // 设置SDK数据
            detailController.setSdk(sdk);
            // 设置返回回调
            detailController.setOnBackCallback(_ -> {
                showListView();
            });
            detailController.setHostServices(SdkmanApplication.hostServices);

            // 添加详情页到根容器并显示
            if (!rootContainer.getChildren().contains(detailView)) {
                rootContainer.getChildren().add(detailView);
            }

            // 隐藏列表视���，显示详情视图
            listView.setVisible(false);
            listView.setManaged(false);
            detailView.setVisible(true);
            detailView.setManaged(true);

            logger.info("Navigated to SDK detail page successfully");

        } catch (IOException e) {
            logger.error("Failed to load SDK detail view", e);
            showError("无法加载SDK详情页面");
        }
    }

    /**
     * 显示列表视图
     */
    private void showListView() {
        logger.info("Returning to SDK list view");

        // 隐藏所有详情视图
        rootContainer.getChildren().stream()
                .filter(node -> node != listView)
                .forEach(node -> {
                    node.setVisible(false);
                    node.setManaged(false);
                });

        // 显示列表视图
        listView.setVisible(true);
        listView.setManaged(true);

        // 重新应用过滤器以更新UI状态
        applyFilters();

        logger.info("Returned to SDK list view");
    }
}