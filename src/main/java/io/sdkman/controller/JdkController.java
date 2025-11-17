package io.sdkman.controller;

import io.sdkman.SdkmanApplication;
import io.sdkman.model.JdkListItem;
import io.sdkman.model.JdkCategory;
import io.sdkman.model.SdkVersion;
import io.sdkman.service.SdkmanService;
import io.sdkman.util.AlertUtils;
import io.sdkman.util.I18nManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDK管理页面控制器
 */
public class JdkController {
    private static final Logger logger = LoggerFactory.getLogger(JdkController.class);

    @FXML
    private StackPane rootContainer;
    @FXML
    private VBox listViewContainer;
    @FXML
    private Label titleLabel;
    @FXML
    private Label subtitleLabel;
    @FXML
    private TextField searchField;
    @FXML
    private Button refreshButton;
    @FXML
    private Label filterLabel;
    @FXML
    private ComboBox<String> statusFilterCombo;
    @FXML
    private Label vendorLabel;
    @FXML
    private ComboBox<String> vendorFilterCombo;
    @FXML
    private Label categoryLabel;
    @FXML
    private ComboBox<String> categoryFilterCombo;
    @FXML
    private ListView<JdkListItem> jdkListView;
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

    private final SdkmanService sdkManagerService;
    private final ObservableList<SdkVersion> allJdkVersions; // 原始数据
    private final ObservableList<JdkListItem> displayListItems; // 显示数据（带分组）
    private final FilteredList<JdkListItem> filteredListItems;
    private final InstallationHandler installationHandler;
    private final VersionListManager<SdkVersion> versionListManager;

    public JdkController() {
        this.sdkManagerService = SdkmanService.getInstance();
        this.allJdkVersions = FXCollections.observableArrayList();
        this.displayListItems = FXCollections.observableArrayList();
        this.filteredListItems = new FilteredList<>(displayListItems, p -> true);
        this.installationHandler = new InstallationHandler(sdkManagerService);
        this.versionListManager = new VersionListManager<>();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing JdkController");

        // 加载自定义样式表
        try {
            String cssPath = getClass().getResource("/css/jdk-management.css").toExternalForm();
            jdkListView.getStylesheets().add(cssPath);
            logger.debug("Loaded custom stylesheet: {}", cssPath);
        } catch (Exception e) {
            logger.warn("Failed to load custom stylesheet", e);
        }

        // 设置国际化文本
        setupI18n();

        // 配置ListView
        setupListView();

        // 配置筛选器
        setupFilters();

        // 配置搜索
        setupSearch();

        // 加载JDK列表
        loadJdkVersions(false);

        logger.info("JdkController initialized successfully");
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        titleLabel.setText(I18nManager.get("jdk.title"));
        subtitleLabel.setText(I18nManager.get("home.subtitle"));
        searchField.setPromptText(I18nManager.get("jdk.search.placeholder"));
        refreshButton.setText(I18nManager.get("jdk.action.refresh"));
        filterLabel.setText(I18nManager.get("jdk.filter.label") + ":");
        vendorLabel.setText(I18nManager.get("jdk.vendor.label") + ":");
        categoryLabel.setText(I18nManager.get("jdk.category.label") + ":");
        loadingLabel.setText(I18nManager.get("list.message.loading"));
        emptyTitleLabel.setText(I18nManager.get("jdk.message.no_jdk_found"));
        emptySubtitleLabel.setText(I18nManager.get("jdk.action.refresh"));
    }

    /**
     * 配置ListView
     */
    private void setupListView() {
        jdkListView.setItems(filteredListItems);
        jdkListView.setCellFactory(_ -> new JdkListCell());

        // 禁用选择模式，避免点击时闪烁
        jdkListView.setSelectionModel(null);
        jdkListView.setFocusTraversable(false);

        // 不设置固定Cell高度，因为标题行和版本行高度不同

        // 启用缓存提升性能
        jdkListView.setCache(true);
        jdkListView.setCacheHint(javafx.scene.CacheHint.SPEED);

        // 使用CSS实现更流畅的滚动
        jdkListView.setStyle("-fx-background-insets: 0; -fx-padding: 0;");
    }

    /**
     * 配置筛选器
     */
    private void setupFilters() {
        // 状态筛选
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                I18nManager.get("jdk.filter.all"),
                I18nManager.get("jdk.filter.installed"),
                I18nManager.get("jdk.filter.not_installed")
        ));
        statusFilterCombo.setValue(I18nManager.get("jdk.filter.all"));
        statusFilterCombo.setOnAction(e -> applyFilters());

        // 供应商筛选 - 初始只有"全部"选项，加载JDK后动态填充
        vendorFilterCombo.setItems(FXCollections.observableArrayList(I18nManager.get("jdk.vendor.all")));
        vendorFilterCombo.setValue(I18nManager.get("jdk.vendor.all"));
        vendorFilterCombo.setOnAction(e -> applyFilters());

        // 分类筛选
        categoryFilterCombo.setItems(FXCollections.observableArrayList(
                I18nManager.get("jdk.category.all"),
                I18nManager.get("jdk.category.jdk"),
                I18nManager.get("jdk.category.javafx"),
                I18nManager.get("jdk.category.nik")
        ));
        categoryFilterCombo.setValue(I18nManager.get("jdk.category.all"));
        categoryFilterCombo.setOnAction(e -> applyFilters());
    }

    /**
     * 配置搜索
     */
    private void setupSearch() {
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
    }

    /**
     * 应用筛选条件并重新生成分组显示
     *
     */
    private void applyFilters() {
        String searchText = searchField.getText() != null ? searchField.getText().toLowerCase().trim() : "";
        String statusFilter = statusFilterCombo.getValue();
        String vendorFilter = vendorFilterCombo.getValue();
        String categoryFilter = categoryFilterCombo.getValue();

        // 防止初始化时的NPE
        if (statusFilter == null || vendorFilter == null || categoryFilter == null) {
            return;
        }

        // 先筛选出符合条件的JDK版本
        List<SdkVersion> filtered = allJdkVersions.stream()
                .filter(jdk -> {
                    // 搜索过滤
                    if (!searchText.isEmpty() && !jdk.getVersion().toLowerCase().contains(searchText)) {
                        return false;
                    }

                    // 状态过滤
                    if (filterStatus(statusFilter, jdk)) return false;

                    // 供应商过滤
                    if (!vendorFilter.equals(I18nManager.get("jdk.vendor.all"))) {
                        String vendor = jdk.getVendor();
                        if (vendor == null || vendor.isEmpty()) {
                            return false;
                        }
                        if (!vendor.equals(vendorFilter)) {
                            return false;
                        }
                    }

                    // 分类过滤
                    return filterCategory(categoryFilter, jdk);
                })
                .toList();

        // 动态更新筛选器选项（联动）
        updateDynamicFilterOptions(statusFilter, vendorFilter, categoryFilter);

        // 智能更新显示列表
        smartUpdateGroupedList(filtered);
    }

    /**
     * 智能更新分组列表：尽量保持ListView控件不变，只更新数据
     */
    private void smartUpdateGroupedList(List<SdkVersion> versions) {
        // 构建新的列表项结构
        List<JdkListItem> newItems = buildGroupedListItems(versions);

        // 如果当前列表为空，直接设置新列表
        if (displayListItems.isEmpty()) {
            displayListItems.setAll(newItems);
            return;
        }

        // 检查���表结构是否发生变化
        if (listStructureChanged(newItems)) {
            // 结构变化了（筛选条件变化），需要重建列表
            displayListItems.setAll(newItems);
        } else {
            // 结构未变化，只更新数据内容，保持ListView控件不变
            updateListItemsData(newItems);
            // 关键：使用refresh()而不是setAll()，避免ListView控件重建
            jdkListView.refresh();
        }
    }

    /**
     * 检查列表结构是否发生变化
     */
    private boolean listStructureChanged(List<JdkListItem> newItems) {
        // 大小不同，结构肯定变化了
        if (displayListItems.size() != newItems.size()) {
            return true;
        }

        // 检查每一项的类型和供应商是否相同
        for (int i = 0; i < newItems.size(); i++) {
            JdkListItem existingItem = displayListItems.get(i);
            JdkListItem newItem = newItems.get(i);

            // 类型不同，结构变化
            if (existingItem.getType() != newItem.getType()) {
                return true;
            }

            // 供应商标题不同，结构变化
            if (existingItem.getType() == JdkListItem.ItemType.VENDOR_HEADER) {
                if (!Objects.equals(existingItem.getVendorName(), newItem.getVendorName())) {
                    return true;
                }
            }
        }

        return false; // 结构未变化
    }

    /**
     * 更新现有列表项的数据，不改变列表结构
     */
    private void updateListItemsData(List<JdkListItem> newItems) {
        for (int i = 0; i < displayListItems.size() && i < newItems.size(); i++) {
            JdkListItem existingItem = displayListItems.get(i);
            JdkListItem newItem = newItems.get(i);

            // 只更新JDK版本项的数据
            if (existingItem.getType() == JdkListItem.ItemType.JDK_VERSION &&
                    newItem.getType() == JdkListItem.ItemType.JDK_VERSION) {

                // 直接替换现有的JdkListItem对象
                displayListItems.set(i, newItem);
            }
        }
    }

    private boolean filterCategory(String categoryFilter, SdkVersion jdk) {
        if (!categoryFilter.equals(I18nManager.get("jdk.category.all"))) {
            var category = jdk.getCategory();
            if (category == null) {
                return false;
            }

            if (categoryFilter.equals(I18nManager.get("jdk.category.jdk")) && category != JdkCategory.JDK) {
                return false;
            }
            if (categoryFilter.equals(I18nManager.get("jdk.category.javafx")) && category != JdkCategory.JAVAFX) {
                return false;
            }
            return !categoryFilter.equals(I18nManager.get("jdk.category.nik")) || category == JdkCategory.NIK;
        }

        return true;
    }

    private boolean filterStatus(String statusFilter, SdkVersion jdk) {
        if (!statusFilter.equals(I18nManager.get("jdk.filter.all"))) {
            if (statusFilter.equals(I18nManager.get("jdk.filter.installed")) && !jdk.isInstalled()) {
                return true;
            }
            return statusFilter.equals(I18nManager.get("jdk.filter.not_installed")) && jdk.isInstalled();
        }
        return false;
    }

    /**
     * 根据当前筛选条件动态更新其他筛选器的可用选项
     */
    private void updateDynamicFilterOptions(String currentStatusFilter, String currentVendorFilter, String currentCategoryFilter) {
        // 基于当前筛选条件，计算哪些JDK可见
        List<SdkVersion> baseFiltered = allJdkVersions.stream()
                .filter(jdk -> {
                    // 应用除了供应商和分类之外的筛选
                    return !filterStatus(currentStatusFilter, jdk);
                })
                .toList();

        // 更新供应商筛选器（基于分类筛选）
        List<SdkVersion> forVendorFilter = baseFiltered.stream()
                .filter(jdk -> {
                    return filterCategory(currentCategoryFilter, jdk);
                })
                .toList();

        updateVendorFilterOptions(forVendorFilter, currentVendorFilter);

        // 更新分类筛选器（基于供应商筛选）
        List<SdkVersion> forCategoryFilter = baseFiltered.stream()
                .filter(jdk -> {
                    if (!currentVendorFilter.equals(I18nManager.get("jdk.vendor.all"))) {
                        String vendor = jdk.getVendor();
                        if (vendor == null || vendor.isEmpty()) return false;
                        return vendor.equals(currentVendorFilter);
                    }
                    return true;
                })
                .toList();

        updateCategoryFilterOptions(forCategoryFilter, currentCategoryFilter);
    }

    /**
     * 更新供应商筛选器选项
     */
    private void updateVendorFilterOptions(List<SdkVersion> versions, String currentValue) {
        List<String> vendors = versions.stream()
                .map(SdkVersion::getVendor)
                .filter(v -> v != null && !v.isEmpty())
                .distinct()
                .sorted()
                .toList();

        ObservableList<String> vendorOptions = FXCollections.observableArrayList();
        vendorOptions.add(I18nManager.get("jdk.vendor.all"));
        vendorOptions.addAll(vendors);

        // 暂时移除监听器以避免触发循环更新
        vendorFilterCombo.setOnAction(null);
        vendorFilterCombo.setItems(vendorOptions);

        // 保留当前选择（如果还存在）
        if (currentValue != null && vendorOptions.contains(currentValue)) {
            vendorFilterCombo.setValue(currentValue);
        } else {
            vendorFilterCombo.setValue(I18nManager.get("jdk.vendor.all"));
        }

        // 重新添加监听器
        vendorFilterCombo.setOnAction(e -> applyFilters());
    }

    /**
     * 更新分类筛选器选项
     */
    private void updateCategoryFilterOptions(List<SdkVersion> versions, String currentValue) {
        // 获取当前结果中存在的分类
        Set<JdkCategory> availableCategories = versions.stream()
                .map(SdkVersion::getCategory)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        ObservableList<String> categoryOptions = FXCollections.observableArrayList();
        categoryOptions.add(I18nManager.get("jdk.category.all"));

        if (availableCategories.contains(JdkCategory.JDK)) {
            categoryOptions.add(I18nManager.get("jdk.category.jdk"));
        }
        if (availableCategories.contains(JdkCategory.JAVAFX)) {
            categoryOptions.add(I18nManager.get("jdk.category.javafx"));
        }
        if (availableCategories.contains(JdkCategory.NIK)) {
            categoryOptions.add(I18nManager.get("jdk.category.nik"));
        }

        // 暂时移除监听器以避免触发循环更新
        categoryFilterCombo.setOnAction(null);
        categoryFilterCombo.setItems(categoryOptions);

        // 保留当前选择（如果还存在）
        if (currentValue != null && categoryOptions.contains(currentValue)) {
            categoryFilterCombo.setValue(currentValue);
        } else {
            categoryFilterCombo.setValue(I18nManager.get("jdk.category.all"));
        }

        // 重新添加监听器
        categoryFilterCombo.setOnAction(e -> applyFilters());
    }

    /**
     * 构建带供应商分组的显示列表
     *
     * @param versions 要显示的JDK版本列表
     */
    private void buildGroupedList(List<SdkVersion> versions) {
        List<JdkListItem> items = buildGroupedListItems(versions);
        displayListItems.setAll(items);
    }

    /**
     * 构建带分组的显示列表项
     */
    private List<JdkListItem> buildGroupedListItems(List<SdkVersion> versions) {
        List<JdkListItem> items = new ArrayList<>();

        // 按供应商分组
        Map<String, List<SdkVersion>> groupedByVendor = versions.stream()
                .collect(Collectors.groupingBy(
                        v -> v.getVendor() != null ? v.getVendor() : "Unknown",
                        LinkedHashMap::new,  // 保持顺序
                        Collectors.toList()
                ));

        // 按优先级排序：Eclipse Temurin 优先，其他按字母顺序
        List<String> sortedVendors = groupedByVendor.keySet().stream()
                .sorted((v1, v2) -> {
                    // Eclipse Temurin 置顶
                    boolean v1IsTemurin = v1.toLowerCase().contains("temurin") || v1.toLowerCase().contains("eclipse");
                    boolean v2IsTemurin = v2.toLowerCase().contains("temurin") || v2.toLowerCase().contains("eclipse");

                    if (v1IsTemurin && !v2IsTemurin) return -1;
                    if (!v1IsTemurin && v2IsTemurin) return 1;

                    return v1.compareToIgnoreCase(v2);  // 其他按字母顺序
                })
                .toList();

        // 为每个供应商创建标题和版本项
        for (String vendor : sortedVendors) {
            List<SdkVersion> vendorVersions = groupedByVendor.get(vendor);

            // 按版本号降序排序（最新版本在前）
            vendorVersions.sort((v1, v2) ->
                    io.sdkman.util.VersionComparator.descending().compare(v1.getVersion(), v2.getVersion())
            );

            // 为Eclipse Temurin版本的推荐标签做准备
            boolean isTemurin = vendor.toLowerCase().contains("temurin") || vendor.toLowerCase().contains("eclipse");

            // 添加供应商标题（带推荐标识）
            if (isTemurin) {
                items.add(JdkListItem.createVendorHeader(vendor, true));
            } else {
                items.add(JdkListItem.createVendorHeader(vendor, false));
            }

            // 添加该供应商下的所有版本
            for (SdkVersion version : vendorVersions) {
                items.add(JdkListItem.createJdkVersion(version));
            }
        }

        return items;
    }

    /**
     * 加载JDK版本列表
     *
     * @param preserveFilters 是否保留当前的筛选状态
     */
    private void loadJdkVersions(boolean preserveFilters) {
        loadJdkVersions(preserveFilters, false);
    }

    /**
     * 加载JDK版本列表
     *
     * @param preserveFilters 是否保留当前的筛选状态
     * @param forceRefresh    是否强制刷新（忽略缓存）
     */
    private void loadJdkVersions(boolean preserveFilters, boolean forceRefresh) {
        logger.info("Loading JDK versions (preserve filters: {}, force refresh: {})", preserveFilters, forceRefresh);
        logger.info("Current allJdkVersions size: {}", allJdkVersions.size());

        // 保存当前筛选状态
        String savedSearchText = preserveFilters ? searchField.getText() : "";
        String savedStatusFilter = preserveFilters ? statusFilterCombo.getValue() : null;
        String savedVendorFilter = preserveFilters ? vendorFilterCombo.getValue() : null;

        // 显示加载状态
        showLoadingState();

        Task<List<SdkVersion>> task = sdkManagerService.loadJdkVersionsTask();

        task.setOnSucceeded(event -> {
            List<SdkVersion> versions = task.getValue();
            long installedCount = versions.stream().filter(SdkVersion::isInstalled).count();
            logger.info("Loaded {} JDK versions ({} installed)", versions.size(), installedCount);

            Platform.runLater(() -> {
                allJdkVersions.setAll(versions);

                // 再次确认设置后的状态
                long installedCountAfter = allJdkVersions.stream().filter(SdkVersion::isInstalled).count();
                logger.info("After setAll, allJdkVersions has {} installed JDKs", installedCountAfter);

                // 恢复筛选状态
                if (preserveFilters) {
                    if (savedSearchText != null) {
                        searchField.setText(savedSearchText);
                    }
                    if (savedStatusFilter != null) {
                        statusFilterCombo.setValue(savedStatusFilter);
                    }
                }

                // 应用筛选并生成分组显示（会自动更新供应商和分类筛选器的选项）
                applyFilters();

                if (versions.isEmpty()) {
                    showEmptyState();
                } else {
                    showListState();
                }
            });
        });

        task.setOnFailed(event -> {
            logger.error("Failed to load JDK versions", task.getException());
            Platform.runLater(this::showEmptyState);
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 刷新按钮点击事件
     */
    @FXML
    private void onRefreshClicked() {
        logger.info("Refresh button clicked");
        loadJdkVersions(false, true); // 不保留筛选状态，强制从网络刷新
    }

    /**
     * 刷新JDK列表（保留当前筛选状态）
     */
    private void refreshJdkList() {
        loadJdkVersions(true); // 保留筛选状态
    }

    /**
     * 显示加载状态
     */
    private void showLoadingState() {
        jdkListView.setVisible(false);
        jdkListView.setManaged(false);
        emptyPane.setVisible(false);
        emptyPane.setManaged(false);
        loadingPane.setVisible(true);
        loadingPane.setManaged(true);
    }

    /**
     * 显示列表状态
     */
    private void showListState() {
        loadingPane.setVisible(false);
        loadingPane.setManaged(false);
        emptyPane.setVisible(false);
        emptyPane.setManaged(false);
        jdkListView.setVisible(true);
        jdkListView.setManaged(true);
    }

    /**
     * 显示空状态
     */
    private void showEmptyState() {
        loadingPane.setVisible(false);
        loadingPane.setManaged(false);
        jdkListView.setVisible(false);
        jdkListView.setManaged(false);
        emptyPane.setVisible(true);
        emptyPane.setManaged(true);
    }

    /**
     * 安装JDK
     */
    private void installJdk(SdkVersion jdk) {
        installationHandler.install(
                jdk,
                I18nManager.get("version.installing"),
                // 成功回调
                item -> {
                    // 在allJdkVersions中找到对应的JDK并更新状态（确保引用一致）
                    SdkVersion targetJdk = allJdkVersions.stream()
                            .filter(v -> v.getIdentifier().equals(jdk.getIdentifier()))
                            .findFirst()
                            .orElse(null);

                    if (targetJdk != null) {
                        logger.info("Before setInstalled: {} isInstalled={}, same object={}",
                                targetJdk.getIdentifier(), targetJdk.isInstalled(), targetJdk == jdk);

                        targetJdk.setInstalled(true);
                        // 检查是否成为默认版本（简单实现：如果只有一个安装的版本，设为默认）
                        updateDefaultStatusAfterInstall(targetJdk);

                        logger.info("After setInstalled: {} isInstalled={}, installedProperty={}",
                                targetJdk.getIdentifier(), targetJdk.isInstalled(),
                                targetJdk.installedProperty().get());

                        // 统计已安装的JDK数量
                        long installedCount = allJdkVersions.stream().filter(SdkVersion::isInstalled).count();
                        logger.info("Total installed JDKs in allJdkVersions: {}", installedCount);
                    }

                    // 重新应用筛选以更新状态标签（状态标签是静态的，需要重建Cell）
                    applyFilters();
                },
                // 失败回调
                _ -> {
                    AlertUtils.showErrorAlert(I18nManager.get("jdk.message.install_failed"),
                            MessageFormat.format(I18nManager.get("jdk.message.install_failed"), jdk.getVersion()));
                }
        );
    }

    /**
     * 卸载JDK
     */
    private void uninstallJdk(SdkVersion jdk) {
        logger.info("Uninstalling JDK: {} (identifier: {})", jdk.getVersion(), jdk.getIdentifier());

        // 使用VersionListManager处理卸载以显示进度指示
        // 传递allJdkVersions而不是displayListItems，因为VersionListManager期望SdkVersion列表
        versionListManager.handleUninstall(
                jdk,
                allJdkVersions,
                "Java " + jdk.getVersion(),
                // 成功后的额外回调
                uninstalledJdk -> {
                    // 找到卸载的JDK并更新其状态
                    if (uninstalledJdk != null) {
                        logger.info("Successfully uninstalled JDK {} (isInstalled: {})", uninstalledJdk.getVersion(), uninstalledJdk.isInstalled());

                        // 统计已安装的JDK数量
                        long installedCount = allJdkVersions.stream().filter(SdkVersion::isInstalled).count();
                        logger.info("Total installed JDKs after uninstall: {}", installedCount);

                        // 清除JDK缓存，确保下次重新获取最新状态
                        logger.info("JDK uninstalled successfully");
                    }
                },
                // 刷新UI的回调
                () -> jdkListView.refresh()
        );
    }

    /**
     * 设置为默认JDK
     */
    private void setDefaultJdk(SdkVersion jdk) {
        logger.info("Setting default JDK: {} (identifier: {})", jdk.getVersion(), jdk.getIdentifier());

        // 使用VersionListManager处理设为默认操作，自动包含进度指示
        versionListManager.handleSetDefault(
                jdk,
                allJdkVersions,
                jdk.getDisplayName(),
                null,  // 不需要额外的成功回调
                () -> {
                    // 设置默认版本后需要刷新UI，因为：
                    // 1. ListView的Cell可能被缓存，需要重新渲染
                    // 2. 分组列表需要更新（默认状态影响排序）
                    applyFilters();
                    logger.debug("Applied filters after setting default JDK");
                }
        );
    }

    /**
     * 安装后更新默认状态（本地更新，避免重新加载列表）
     * SDKMAN会在安装第一个JDK时自动设为默认，需要检测并更新
     */
    private void updateDefaultStatusAfterInstall(SdkVersion installedJdk) {
        // 检查当前是否有其他已安装的JDK
        boolean hasOtherInstalled = allJdkVersions.stream()
                .anyMatch(v -> v.isInstalled() && !v.getIdentifier().equals(installedJdk.getIdentifier()));

        // 如果这是第一个安装的JDK，SDKMAN会自动设为默认
        if (!hasOtherInstalled) {
            installedJdk.setDefault(true);
            installedJdk.setInUse(true);
        }
        // 如果已经有其他JDK，SDKMAN不会改变默认版本

        // 注意：调用者需要调用applyFilters()来更新状态标签（标签是静态的，需要重建Cell）
    }

    /**
     * 显示供应商详情页
     */
    private void showVendorDetail(String vendorName) throws IOException {
        logger.info("Showing vendor detail for: {}", vendorName);

        // 获取该供应商的所有版本
        var vendorVersions = allJdkVersions.stream()
                .filter(v -> vendorName.equals(v.getVendor()))
                .toList();

        if (vendorVersions.isEmpty()) {
            logger.warn("No versions found for vendor: {}", vendorName);
            return;
        }

        // 加载详情页FXML
        var loader = new FXMLLoader(getClass().getResource("/fxml/jdk-detail-view.fxml"));
        Parent detailView = loader.load();

        // 获取控制器并设置数据
        JdkDetailController detailController = loader.getController();
        detailController.setVendor(vendorName, vendorVersions);
        detailController.setOnBackCallback(_ -> showListView());
        detailController.setHostServices(SdkmanApplication.hostServices);

        // 显示详情页
        rootContainer.getChildren().add(detailView);
        listViewContainer.setVisible(false);
        detailView.setVisible(true);
    }

    /**
     * 返回列表视图
     */
    private void showListView() {
        logger.info("Returning to list view");

        // 移除详情页
        if (rootContainer.getChildren().size() > 1) {
            rootContainer.getChildren().remove(1);
        }

        // 显示列表视图
        listViewContainer.setVisible(true);

        // 刷新JDK列表以更新安装状态
        refreshJdkList();
    }

    /**
     * 自定义JDK列表Cell
     * 修复了Cell重用时按钮状态残留的问题
     */
    private class JdkListCell extends ListCell<JdkListItem> {
        // 缓存当前Cell绑定的JDK对象，避免重复绑定
        private SdkVersion currentJdk = null;

        @Override
        protected void updateItem(JdkListItem item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                currentJdk = null;
                return;
            }

            // 根据类型渲染不同的Cell
            if (item.getType() == JdkListItem.ItemType.VENDOR_HEADER) {
                setGraphic(createVendorHeaderCell(item.getVendorName(), item.isRecommended()));
                currentJdk = null;
            } else {
                SdkVersion jdk = item.getSdkVersion();
                // 修复：总是重新创建UI以确保状态正确，或者改进缓存逻辑
                // 使用更精确的比较：版本号+标识符
                boolean isSameJdk = currentJdk != null &&
                        currentJdk.getVersion().equals(jdk.getVersion()) &&
                        Objects.equals(currentJdk.getIdentifier(), jdk.getIdentifier());

                if (!isSameJdk) {
                    setGraphic(createJdkVersionCell(jdk));
                    currentJdk = jdk;
                }
                // Property绑定会自动更新UI状态（安装进度、按钮状态等）
            }
        }

        /**
         * 创建供应商标题Cell
         */
        private HBox createVendorHeaderCell(String vendorName, boolean recommended) {
            HBox headerBox = new HBox(10);
            headerBox.getStyleClass().add("vendor-header-cell");
            headerBox.setAlignment(Pos.CENTER_LEFT);
            headerBox.setPadding(new Insets(12, 20, 12, 20));
            headerBox.setMaxHeight(45);
            headerBox.setMinHeight(45);
            headerBox.setPrefHeight(45);

            // 供应商名称
            Label vendorLabel = new Label("▼ " + vendorName);
            vendorLabel.getStyleClass().add("vendor-label");

            // 推荐标签
            if (recommended) {
                Label recommendedLabel = new Label(I18nManager.get("jdk.status.recommended"));
                recommendedLabel.getStyleClass().add("recommended-label");
                headerBox.getChildren().addAll(vendorLabel, recommendedLabel);
            } else {
                headerBox.getChildren().add(vendorLabel);
            }

            // 添加点击事件，显示供应商详情页
            headerBox.setOnMouseClicked(event -> {
                try {
                    showVendorDetail(vendorName);
                } catch (IOException e) {
                    logger.error("Failed to show vendor detail", e);
                    AlertUtils.showErrorAlert(
                            I18nManager.get("error.title"),
                            I18nManager.get("error.load_detail_failed")
                    );
                }
            });

            // 添加鼠标悬停效果
            headerBox.setOnMouseEntered(_ -> headerBox.setStyle("-fx-cursor: hand;"));
            headerBox.setOnMouseExited(_ -> headerBox.setStyle("-fx-cursor: default;"));

            return headerBox;
        }

        /**
         * 创建JDK版本Cell
         */
        private HBox createJdkVersionCell(SdkVersion jdk) {
            // 使用通用的VersionListCellFactory创建cell，添加jdk-version-cell样式类来应用特殊样式
            return VersionListCellFactory.createVersionCell(
                    jdk,
                    JdkController.this::installJdk,
                    JdkController.this::uninstallJdk,
                    JdkController.this::setDefaultJdk,
                    "jdk-version-cell",  // 使用JDK特有的CSS样式类
                    true                  // 显示identifier
            );
        }
    }
}
