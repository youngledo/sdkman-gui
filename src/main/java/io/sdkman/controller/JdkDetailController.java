package io.sdkman.controller;

import io.sdkman.model.SdkVersion;
import io.sdkman.util.I18nManager;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * JDK详情页面控制器
 * 显示特定供应商的JDK详细信息和版本列表
 */
public class JdkDetailController {
    private static final Logger logger = LoggerFactory.getLogger(JdkDetailController.class);

    @FXML
    private Button backButton;
    @FXML
    private Label titleLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Hyperlink websiteLink;
    @FXML
    private Label architecturesLabel;
    @FXML
    private VBox architecturesBox;
    @FXML
    private Label versionsLabel;
    @FXML
    private Button refreshVersionsButton;
    @FXML
    private ListView<SdkVersion> versionsListView;
    @FXML
    private VBox versionsLoadingPane;

    private final ObservableList<SdkVersion> versions;
    private final VersionListManager<SdkVersion> versionListManager;

    private String vendorName;
    private List<SdkVersion> vendorVersions;
    private Consumer<Void> onBackCallback;
    private HostServices hostServices;

    public JdkDetailController() {
        this.versions = FXCollections.observableArrayList();
        this.versionListManager = new VersionListManager<>();
    }

    @FXML
    public void initialize() {
        logger.info("Initializing JdkDetailController");

        // 设置国际化文本
        backButton.setText(I18nManager.get("detail.back"));
        versionsLabel.setText(I18nManager.get("detail.versions"));
        refreshVersionsButton.setText(I18nManager.get("detail.refresh"));

        // 配置版本列表
        setupVersionsList();

        // 配置返回按钮
        backButton.setOnAction(_ -> handleBack());

        // 配置刷新按钮
        refreshVersionsButton.setOnAction(_ -> loadVersions());

        logger.info("JdkDetailController initialized");
    }

    /**
     * 设置要显示的JDK供应商信息
     */
    public void setVendor(String vendorName, List<SdkVersion> versions) {
        this.vendorName = vendorName;
        this.vendorVersions = versions;
        loadJdkDetails();
    }

    /**
     * 设置返回回调
     */
    public void setOnBackCallback(Consumer<Void> callback) {
        this.onBackCallback = callback;
    }

    /**
     * 设置HostServices用于打开外部链接
     */
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    /**
     * 加载JDK详情
     */
    private void loadJdkDetails() {
        if (vendorName == null || vendorVersions == null || vendorVersions.isEmpty()) {
            logger.warn("No vendor data to display");
            return;
        }

        // 设置标题
        titleLabel.setText(vendorName);

        // 设置描述（如果有的话，从第一个版本获取）
        String description = getVendorDescription(vendorName);
        if (description != null && !description.isEmpty()) {
            descriptionLabel.setText(description);
            descriptionLabel.setVisible(true);
            descriptionLabel.setManaged(true);
        } else {
            descriptionLabel.setVisible(false);
            descriptionLabel.setManaged(false);
        }

        // 设置官网链接
        String website = getVendorWebsite(vendorName);
        if (!website.isEmpty()) {
            websiteLink.setText(website);
            websiteLink.setOnAction(_ -> {
                if (hostServices != null) {
                    hostServices.showDocument(website);
                }
            });
            websiteLink.setVisible(true);
            websiteLink.setManaged(true);
        } else {
            websiteLink.setVisible(false);
            websiteLink.setManaged(false);
        }

        // 暂时隐藏架构信息（JDK版本可能没有此信息）
        architecturesBox.setVisible(false);
        architecturesBox.setManaged(false);

        // 加载版本列表
        loadVersions();
    }

    /**
     * 获取供应商描述
     */
    private String getVendorDescription(String vendor) {
        // 为常见供应商提供描述
        return switch (vendor.toLowerCase()) {
            case "oracle" ->
                    I18nManager.get("jdk.vendor.oracle.description", "Oracle JDK is the official Java Development Kit from Oracle Corporation.");
            case "temurin", "eclipse temurin" ->
                    I18nManager.get("jdk.vendor.temurin.description", "Eclipse Temurin is the open source Java SE build based upon OpenJDK, recommended for production use.");
            case "corretto" ->
                    I18nManager.get("jdk.vendor.corretto.description", "Amazon Corretto is a no-cost, multiplatform, production-ready distribution of OpenJDK.");
            case "zulu" ->
                    I18nManager.get("jdk.vendor.zulu.description", "Azul Zulu is a certified build of OpenJDK that is fully compliant with the Java SE standard.");
            case "liberica" ->
                    I18nManager.get("jdk.vendor.liberica.description", "BellSoft Liberica JDK is a 100% open-source Java implementation.");
            case "graalvm" ->
                    I18nManager.get("jdk.vendor.graalvm.description", "GraalVM is a high-performance JDK distribution with advanced features like polyglot programming.");
            case "sapmachine" ->
                    I18nManager.get("jdk.vendor.sapmachine.description", "SapMachine is an OpenJDK release maintained and supported by SAP.");
            case "microsoft" ->
                    I18nManager.get("jdk.vendor.microsoft.description", "Microsoft Build of OpenJDK is a no-cost distribution of OpenJDK that's open source.");
            default -> "";
        };
    }

    /**
     * 获取供应商官网
     */
    private String getVendorWebsite(String vendor) {
        return switch (vendor.toLowerCase()) {
            case "oracle" -> "https://www.oracle.com/java/";
            case "temurin", "eclipse temurin" -> "https://adoptium.net/";
            case "corretto" -> "https://aws.amazon.com/corretto/";
            case "zulu" -> "https://www.azul.com/downloads/zulu-community/";
            case "liberica" -> "https://bell-sw.com/";
            case "graalvm" -> "https://www.graalvm.org/";
            case "sapmachine" -> "https://sap.github.io/SapMachine/";
            case "microsoft" -> "https://www.microsoft.com/openjdk";
            default -> "";
        };
    }

    /**
     * 配置版本列表
     */
    private void setupVersionsList() {
        versionsListView.setItems(versions);
        versionsListView.setCellFactory(_ -> new JdkVersionCell());

        // 性能优化
        versionsListView.setCache(true);
        versionsListView.setCacheHint(javafx.scene.CacheHint.SPEED);
        versionsListView.setFixedCellSize(60);
        versionsListView.setSelectionModel(null);
        versionsListView.setFocusTraversable(false);
    }

    /**
     * 加载版本列表
     */
    private void loadVersions() {
        if (vendorVersions == null || vendorVersions.isEmpty()) {
            return;
        }

        logger.info("Loading versions for vendor: {}", vendorName);

        // 显示加载状态
        showLoadingState();

        // 在后台线程加载版本
        Task<List<SdkVersion>> task = new Task<>() {
            @Override
            protected List<SdkVersion> call() {
                // 按版本号降序排序（最新版本在前）
                return vendorVersions.stream()
                        .sorted(Comparator.comparing(SdkVersion::getVersion,
                                io.sdkman.util.VersionComparator.descending()))
                        .toList();
            }
        };

        task.setOnSucceeded(_ -> {
            List<SdkVersion> sortedVersions = task.getValue();
            Platform.runLater(() -> {
                versions.setAll(sortedVersions);
                hideLoadingState();
                logger.info("Loaded {} versions for {}", sortedVersions.size(), vendorName);
            });
        });

        task.setOnFailed(_ -> {
            logger.error("Failed to load versions", task.getException());
            Platform.runLater(this::hideLoadingState);
        });

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 处理安装
     */
    private void handleInstall(SdkVersion version) {
        logger.info("Installing JDK: {}", version.getIdentifier());

        versionListManager.handleInstall(
                version,
                versions,
                version.getDisplayName(),
                null,
                () -> versionsListView.refresh()
        );
    }

    /**
     * 处理卸载
     */
    private void handleUninstall(SdkVersion version) {
        logger.info("Uninstalling JDK: {}", version.getIdentifier());

        versionListManager.handleUninstall(
                version,
                versions,
                version.getDisplayName(),
                null,
                () -> versionsListView.refresh()
        );
    }

    /**
     * 处理设置为默认
     */
    private void handleSetDefault(SdkVersion version) {
        logger.info("Setting default JDK: {}", version.getIdentifier());

        versionListManager.handleSetDefault(
                version,
                versions,
                version.getDisplayName(),
                null,
                () -> versionsListView.refresh()
        );
    }

    /**
     * 显示加载状态
     */
    private void showLoadingState() {
        versionsLoadingPane.setVisible(true);
        versionsLoadingPane.setManaged(true);
    }

    /**
     * 隐藏加载状态
     */
    private void hideLoadingState() {
        versionsLoadingPane.setVisible(false);
        versionsLoadingPane.setManaged(false);
    }

    /**
     * 处理返回按钮
     */
    private void handleBack() {
        logger.info("Back button clicked");
        if (onBackCallback != null) {
            onBackCallback.accept(null);
        }
    }

    /**
     * JDK版本Cell
     */
    private class JdkVersionCell extends javafx.scene.control.ListCell<SdkVersion> {
        private SdkVersion currentVersion = null;

        @Override
        protected void updateItem(SdkVersion version, boolean empty) {
            super.updateItem(version, empty);

            if (empty || version == null) {
                setGraphic(null);
                currentVersion = null;
                return;
            }

            boolean isSameVersion = currentVersion != null &&
                    currentVersion.getVersion().equals(version.getVersion()) &&
                    java.util.Objects.equals(currentVersion.getIdentifier(), version.getIdentifier());

            if (!isSameVersion) {
                setGraphic(VersionListCellFactory.createVersionCell(
                        version,
                        JdkDetailController.this::handleInstall,
                        JdkDetailController.this::handleUninstall,
                        JdkDetailController.this::handleSetDefault,
                        "jdk-version-cell",
                        true
                ));
                currentVersion = version;
            }
        }
    }
}
