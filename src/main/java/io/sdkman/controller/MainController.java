package io.sdkman.controller;

import io.sdkman.util.I18nManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * 主窗口控制器
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private ImageView logoImageView;

    @FXML
    private StackPane contentPane;

    @FXML
    private Button homeButton;

    @FXML
    private Button jdkButton;

    @FXML
    private Button sdkButton;

    @FXML
    private Button settingsButton;

    // 当前激活的导航按钮
    private Button activeButton;

    @FXML
    public void initialize() {
        logger.info("Initializing MainController");

        // 设置国际化文本
        setupI18n();

        // 监听语言变化
        setupLanguageChangeListener();

        // 默认显示首页
        loadHomePage();

        logger.info("MainController initialized successfully");
    }

    /**
     * 设置国际化文本
     */
    private void setupI18n() {
        updateUIStrings();
    }

    /**
     * 设置语言变化监听
     */
    private void setupLanguageChangeListener() {
        I18nManager.addLocaleChangeListener(locale -> {
            // 在JavaFX应用线程中更新UI
            javafx.application.Platform.runLater(this::updateUIStrings);
        });
    }

    /**
     * 更新UI字符串
     */
    private void updateUIStrings() {
        // 设置logo图标
        setupLogo();

        // 设置导航按钮文本
        homeButton.setText(I18nManager.get("nav.home"));
        jdkButton.setText(I18nManager.get("nav.jdk"));
        sdkButton.setText(I18nManager.get("nav.sdk"));
        settingsButton.setText(I18nManager.get("nav.settings"));
    }

    /**
     * 设置logo图标
     */
    private void setupLogo() {
        // 加载PNG图标，保持原始大小比例
        var resource = getClass().getResource("/icons/sdkman-gui.png");
        if (resource != null) {
            Image logoImage = null;
            try {
                logoImage = new Image(resource.openStream());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            logoImageView.setImage(logoImage);
        }
    }

    /**
     * 首页按钮点击事件
     */
    @FXML
    private void onHomeClicked() {
        loadHomePage();
    }

    /**
     * JDK按钮点击事件
     */
    @FXML
    private void onJdkClicked() {
        loadPage("/fxml/jdk-view.fxml", jdkButton);
    }

    /**
     * SDK按钮点击事件
     */
    @FXML
    private void onSdkClicked() {
        loadPage("/fxml/sdk-view.fxml", sdkButton);
    }

    /**
     * 设置按钮点击事件
     */
    @FXML
    private void onSettingsClicked() {
        loadPage("/fxml/settings-view.fxml", settingsButton);
    }

    /**
     * 加载首页
     */
    private void loadHomePage() {
        loadPage("/fxml/home-view.fxml", homeButton);
    }

    /**
     * 加载指定页面
     *
     * @param fxmlPath   FXML文件路径
     * @param navButton  对应的导航按钮
     */
    private void loadPage(String fxmlPath, Button navButton) {
        try {
            // 加载FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node page = loader.load();

            // 如果是首页，设置导航回调
            if (fxmlPath.contains("home-view")) {
                HomeController homeController = loader.getController();
                if (homeController != null) {
                    homeController.setNavigationCallback(this::navigateFromHome);
                }
            }

            // 清空并添加新页面
            contentPane.getChildren().clear();
            contentPane.getChildren().add(page);

            // 更新导航按钮状态
            updateNavButtonState(navButton);

            logger.info("Loaded page: {}", fxmlPath);

        } catch (IOException e) {
            logger.error("Failed to load page: {}", fxmlPath, e);
        }
    }

    /**
     * 从首页导航到其他页面
     *
     * @param target 目标页面
     */
    private void navigateFromHome(String target) {
        switch (target) {
            case "jdk" -> onJdkClicked();
            case "sdk" -> onSdkClicked();
            case "settings" -> onSettingsClicked();
            default -> logger.warn("Unknown navigation target: {}", target);
        }
    }

    /**
     * 更新导航按钮状态
     *
     * @param newActiveButton 新激活的按钮
     */
    private void updateNavButtonState(Button newActiveButton) {
        // 移除之前按钮的激活状态
        if (activeButton != null) {
            activeButton.getStyleClass().remove("nav-button-active");
        }

        // 设置新按钮为激活状态
        if (newActiveButton != null && !newActiveButton.getStyleClass().contains("nav-button-active")) {
            newActiveButton.getStyleClass().add("nav-button-active");
        }

        activeButton = newActiveButton;
    }
}
