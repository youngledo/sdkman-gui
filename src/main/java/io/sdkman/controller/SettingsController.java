package io.sdkman.controller;

import atlantafx.base.theme.Styles;
import io.sdkman.util.ConfigManager;
import io.sdkman.util.I18nManager;
import io.sdkman.util.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.*;
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

    @FXML
    private Button saveButton;

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

        // 设置保存按钮样式
        saveButton.getStyleClass().add(Styles.SUCCESS);
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

        // 创建语��� ToggleGroup
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
        saveButton.setText(I18nManager.get("settings.save"));
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
        if (savedLocale != null && savedLocale.equals(Locale.SIMPLIFIED_CHINESE)) {
            languageZhRadio.setSelected(true);
        } else {
            languageEnRadio.setSelected(true);
        }

        // 加载代理设置
        String proxyType = ConfigManager.getProxyType();
        switch (proxyType) {
            case "none" -> proxyNoneRadio.setSelected(true);
            case "auto" -> proxyAutoRadio.setSelected(true);
            case "manual" -> {
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
        proxyTypeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
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
        proxyHostField.textProperty().addListener((obs, oldVal, newVal) -> saveProxySettings());
        proxyPortField.textProperty().addListener((obs, oldVal, newVal) -> saveProxySettings());

        // 监听语言变化，立即保存并切换语言
        languageGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            saveLanguage();
        });

        // 监听主题变化，立即保存并切换主题
        themeGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            saveTheme();
        });
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
                currentPath = System.getProperty("user.home");
            }

            java.io.File initialDir = new java.io.File(currentPath);
            if (!initialDir.exists()) {
                initialDir = new java.io.File(System.getProperty("user.home"));
            }

            // 创建目录选择器
            javafx.stage.DirectoryChooser directoryChooser = new javafx.stage.DirectoryChooser();
            directoryChooser.setTitle(I18nManager.get("settings.sdkman_browse_title"));
            directoryChooser.setInitialDirectory(initialDir);

            // 显示���话框
            java.io.File selectedDirectory = directoryChooser.showDialog(browseButton.getScene().getWindow());

            if (selectedDirectory != null) {
                String selectedPath = selectedDirectory.getAbsolutePath();
                sdkmanPathField.setText(selectedPath);
                logger.info("Selected SDKMAN path: {}", selectedPath);
            }

        } catch (Exception e) {
            logger.error("Failed to open directory chooser", e);
            // 显示错误消息
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
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

    /**
     * 保存设置
     */
    @FXML
    private void onSaveClicked() {
        try {
            // 保存主题设置
            if (themeLightRadio.isSelected()) {
                ConfigManager.saveTheme("light");
            } else if (themeDarkRadio.isSelected()) {
                ConfigManager.saveTheme("dark");
            } else {
                ConfigManager.saveTheme("auto");
            }

            // 保存语言设置
            if (languageZhRadio.isSelected()) {
                ConfigManager.saveLocale(Locale.SIMPLIFIED_CHINESE);
            } else {
                ConfigManager.saveLocale(Locale.US);
            }

            // 保存代理设置
            if (proxyNoneRadio.isSelected()) {
                ConfigManager.setProxyType("none");
            } else if (proxyAutoRadio.isSelected()) {
                ConfigManager.setProxyType("auto");
            } else if (proxyManualRadio.isSelected()) {
                ConfigManager.setProxyType("manual");
                ConfigManager.setProxyHost(proxyHostField.getText().trim());
                ConfigManager.setProxyPort(proxyPortField.getText().trim());
            }

            // 保存其他设置
            ConfigManager.setSdkmanPath(sdkmanPathField.getText().trim());
        } catch (Exception e) {
            logger.error("Failed to save settings", e);
            InstallationHandler.showErrorAlert(
                    I18nManager.get("settings.save.failed"),
                    I18nManager.get("settings.save.failed")
            );
        }
    }

}
