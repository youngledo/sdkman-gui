package io.sdkman.controller;

import atlantafx.base.theme.Styles;
import io.sdkman.model.SdkVersion;
import io.sdkman.util.I18nManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * 通用的版本列表Cell工厂
 * 用于JDK列表和SDK详情列表，统一UI和交互逻辑
 */
public class VersionListCellFactory {

    /**
     * 创建版本信息Cell（完整版本，支持定制）
     *
     * @param version          版本对象
     * @param onInstall        安装回调
     * @param onUninstall      卸载回调
     * @param onSetDefault     设为默认回调
     * @param customStyleClass 自定义CSS样式类（可选）
     * @param showIdentifier   是否显示identifier（默认true）
     * @return Cell的HBox布局
     */
    public static HBox createVersionCell(
            SdkVersion version,
            Consumer<SdkVersion> onInstall,
            Consumer<SdkVersion> onUninstall,
            Consumer<SdkVersion> onSetDefault,
            String customStyleClass,
            boolean showIdentifier) {

        if (version == null) {
            return new HBox();
        }

        // 主容器
        HBox cellBox = new HBox(15);
        cellBox.setAlignment(Pos.CENTER_LEFT);
        cellBox.setPadding(new Insets(12, 15, 12, 15));

        // 如果提供了自定义样式类，则添加
        if (customStyleClass != null && !customStyleClass.isEmpty()) {
            cellBox.getStyleClass().add(customStyleClass);
        }

        // 左侧：版本信息
        VBox infoBox = createInfoBox(version, showIdentifier);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        // 右侧：操作区域（包含进度指示、安装按钮、已安装按钮）
        HBox actionBox = createActionBox(version, onInstall, onUninstall, onSetDefault);

        cellBox.getChildren().addAll(infoBox, actionBox);
        return cellBox;
    }

    /**
     * 创建版本信息区域
     *
     * @param version        版本对象
     * @param showIdentifier 是否显示identifier
     */
    private static VBox createInfoBox(SdkVersion version, boolean showIdentifier) {
        VBox infoBox = new VBox(5);

        // 版本号
        Label versionLabel = new Label(version.getVersion());
        versionLabel.getStyleClass().add("version-label");
        infoBox.getChildren().add(versionLabel);

        // Identifier（可选）
        if (showIdentifier) {
            Label identifierLabel = new Label("   " + version.getIdentifier());
            identifierLabel.getStyleClass().add("identifier-label");
            infoBox.getChildren().add(identifierLabel);
        }

        return infoBox;
    }

    /**
     * 创建操作区域
     */
    private static HBox createActionBox(
            SdkVersion version,
            Consumer<SdkVersion> onInstall,
            Consumer<SdkVersion> onUninstall,
            Consumer<SdkVersion> onSetDefault) {

        HBox actionBox = new HBox(10);
        actionBox.setAlignment(Pos.CENTER_RIGHT);

        // === 进度区域 ===
        HBox progressBox = createProgressBox(version);

        // === 未安装按钮区域 ===
        HBox notInstalledBox = createNotInstalledBox(version, onInstall);

        // === 已安装按钮区域 ===
        HBox installedBox = createInstalledBox(version, onUninstall, onSetDefault);

        // 将三个区域都添加到actionBox（同时只有一个可见）
        actionBox.getChildren().addAll(progressBox, notInstalledBox, installedBox);

        return actionBox;
    }

    /**
     * 创建进度指示区域
     */
    private static HBox createProgressBox(SdkVersion version) {
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER_RIGHT);

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);

        Label progressLabel = new Label();
        progressLabel.getStyleClass().add(Styles.TEXT_SMALL);
        progressLabel.setWrapText(false);
        // 直接显示实际的进度文本
        progressLabel.textProperty().bind(version.installProgressProperty());

        progressBox.getChildren().addAll(progressIndicator, progressLabel);

        // 绑定可见性：只在安装中时显示
        progressBox.visibleProperty().bind(version.installingProperty());
        progressBox.managedProperty().bind(version.installingProperty());

        return progressBox;
    }

    /**
     * 创建未安装按钮区域
     */
    private static HBox createNotInstalledBox(SdkVersion version, Consumer<SdkVersion> onInstall) {
        HBox notInstalledBox = new HBox(8);
        notInstalledBox.setAlignment(Pos.CENTER_RIGHT);

        Button installBtn = new Button(I18nManager.get("list.item.install"));
        installBtn.getStyleClass().addAll(Styles.SUCCESS, Styles.SMALL);
        installBtn.setOnAction(_ -> {
            if (onInstall != null) {
                onInstall.accept(version);
            }
        });

        notInstalledBox.getChildren().add(installBtn);

        // 绑定可见性：未安装且非安装中时显示
        notInstalledBox.visibleProperty().bind(
                Bindings.createBooleanBinding(
                        () -> !version.isInstalled() && !version.isInstalling(),
                        version.installedProperty(),
                        version.installingProperty()
                )
        );
        notInstalledBox.managedProperty().bind(notInstalledBox.visibleProperty());

        return notInstalledBox;
    }

    /**
     * 创建已安装按钮区域
     */
    private static HBox createInstalledBox(
            SdkVersion version,
            Consumer<SdkVersion> onUninstall,
            Consumer<SdkVersion> onSetDefault) {

        HBox installedBox = new HBox(8);
        installedBox.setAlignment(Pos.CENTER_RIGHT);

        // 卸载按钮
        Button uninstallBtn = new Button(I18nManager.get("list.item.uninstall"));
        uninstallBtn.getStyleClass().addAll(Styles.BUTTON_OUTLINED, Styles.DANGER, Styles.SMALL);
        uninstallBtn.setOnAction(_ -> {
            if (onUninstall != null) {
                onUninstall.accept(version);
            }
        });

        // 设为默认按钮
        Button setDefaultBtn = createSetDefaultButton(version, onSetDefault);

        installedBox.getChildren().addAll(uninstallBtn, setDefaultBtn);

        // 绑定可见性：已安装且非安装中时显示
        installedBox.visibleProperty().bind(
                Bindings.createBooleanBinding(
                        () -> version.isInstalled() && !version.isInstalling(),
                        version.installedProperty(),
                        version.installingProperty()
                )
        );
        installedBox.managedProperty().bind(installedBox.visibleProperty());

        return installedBox;
    }

    /**
     * 创建设为默认按钮
     */
    private static Button createSetDefaultButton(SdkVersion version, Consumer<SdkVersion> onSetDefault) {
        Button setDefaultBtn = new Button();

        // 使用Property绑定动态更新按钮文本和状态
        setDefaultBtn.textProperty().bind(
                version.isDefaultProperty().map(isDefault ->
                        isDefault ? I18nManager.get("version.action.default") : I18nManager.get("version.action.set-default"))
        );
        setDefaultBtn.disableProperty().bind(version.isDefaultProperty());

        // 动态添加/移除样式类
        version.isDefaultProperty().addListener((obs, _, isDefault) -> {
            if (isDefault) {
                setDefaultBtn.getStyleClass().removeAll(Styles.ACCENT, Styles.SMALL);
                setDefaultBtn.getStyleClass().add(Styles.SMALL);
            } else {
                if (!setDefaultBtn.getStyleClass().contains(Styles.ACCENT)) {
                    setDefaultBtn.getStyleClass().addAll(Styles.ACCENT, Styles.SMALL);
                }
            }
        });

        // 初始样式设置
        if (version.isDefault()) {
            setDefaultBtn.getStyleClass().add(Styles.SMALL);
        } else {
            setDefaultBtn.getStyleClass().addAll(Styles.ACCENT, Styles.SMALL);
        }

        setDefaultBtn.setOnAction(_ -> {
            if (onSetDefault != null) {
                onSetDefault.accept(version);
            }
        });

        return setDefaultBtn;
    }
}
