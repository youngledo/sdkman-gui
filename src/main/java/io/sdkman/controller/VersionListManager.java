package io.sdkman.controller;

import io.sdkman.model.Installable;
import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.service.SdkmanService;
import io.sdkman.util.AlertUtils;
import io.sdkman.util.I18nManager;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 通用的版本列表管理器，处理安装、卸载、设置默认等操作
 *
 * @param <T> 实现了Installable接口的类型
 */
public class VersionListManager<T extends Installable> {
    private static final Logger logger = LoggerFactory.getLogger(VersionListManager.class);

    private final InstallationHandler installationHandler;

    public VersionListManager() {
        SdkmanService sdkmanService = SdkmanService.getInstance();
        this.installationHandler = new InstallationHandler(sdkmanService);
    }

    /**
     * 处理安装操作
     *
     * @param item      要安装的项
     * @param items     所有项的列表（用于查找和更新状态）
     * @param itemName  项的显示名称（用于提示消息）
     * @param onSuccess 成功后的额外回调（可选）
     * @param refreshUI 刷新UI的回调
     */
    public void handleInstall(T item, ObservableList<T> items, String itemName,
                              Consumer<T> onSuccess, Runnable refreshUI) {
        logger.info("Installing: {} (candidate={})", item.getDisplayName(), item.getCandidate());

        installationHandler.install(
                item,
                I18nManager.get("version.installing"),
                // 成功回调
                installedItem -> {
                    logger.info("Installation succeeded for: {}", installedItem.getDisplayName());

                    // 在列表中找到对应的项并更新状态
                    T targetItem = findItem(items, item);

                    if (targetItem != null) {
                        updateItemAfterInstall(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }

                    if (onSuccess != null) {
                        onSuccess.accept(targetItem);
                    }
                },
                // 失败回调
                failedItem -> {
                    logger.info("Installation failed for: {}", failedItem.getDisplayName());

                    // 在列表中找到对应的项并清除安装中状态
                    T targetItem = findItem(items, item);
                    if (targetItem != null) {
                        clearInstallingState(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }
                    AlertUtils.showErrorAlert(
                            I18nManager.get("alert.error"),
                            I18nManager.get("version.installing.failed", itemName)
                    );
                }
        );
    }

    /**
     * 处理卸载操作
     *
     * @param item      要卸载的项
     * @param items     所有项的列表
     * @param itemName  项的显示名称
     * @param onSuccess 成功后的额外回调（可选）
     * @param refreshUI 刷新UI的回调
     */
    public void handleUninstall(T item, ObservableList<T> items, String itemName,
                                Consumer<T> onSuccess, Runnable refreshUI) {
        installationHandler.uninstall(
                item,
                I18nManager.get("version.uninstall"),
                // 成功回调
                uninstalledItem -> {
                    logger.info("Uninstallation succeeded for: {}", uninstalledItem.getDisplayName());

                    // 在列表中找到对应的项并更新状态
                    T targetItem = findItem(items, item);
                    if (targetItem != null) {
                        updateItemAfterUninstall(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }

                    if (onSuccess != null) {
                        onSuccess.accept(targetItem);
                    }
                },
                // 失败回调
                _ -> {
                    T targetItem = findItem(items, item);
                    if (targetItem != null) {
                        clearInstallingState(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }
                    AlertUtils.showErrorAlert(
                            I18nManager.get("alert.error"),
                            I18nManager.get("version.uninstall.failed", itemName)
                    );
                }
        );
    }

    /**
     * 处理设置默认版本
     *
     * @param item      要设置为默认的项
     * @param items     所有项的列表
     * @param itemName  项的显示名称
     * @param onSuccess 成功后的额外回调（可选）
     * @param refreshUI 刷新UI的回调
     */
    public void handleSetDefault(T item, ObservableList<T> items, String itemName,
                                 Consumer<T> onSuccess, Runnable refreshUI) {
        logger.info("Setting default: {}", item.getDisplayName());

        installationHandler.setDefault(
                item,
                I18nManager.get("list.item.set_default"),
                // 成功回调
                _ -> {
                    // 清除所有项的默认状态，然后设置当前项为默认
                    for (T listItem : items) {
                        updateItemDefaultState(listItem, false);
                    }
                    T targetItem = findItem(items, item);
                    if (targetItem != null) {
                        updateItemDefaultState(targetItem, true);
                        clearInstallingState(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }

                    if (onSuccess != null) {
                        onSuccess.accept(targetItem);
                    }

                    // 移除成功提示对话框，让用户体验更流畅
                    // showInfo("成功设置 " + itemName + " 为默认版本");
                },
                // 失败回调
                _ -> {
                    T targetItem = findItem(items, item);
                    if (targetItem != null) {
                        clearInstallingState(targetItem);
                        if (refreshUI != null) {
                            Platform.runLater(refreshUI);
                        }
                    }
                    AlertUtils.showErrorAlert(
                            I18nManager.get("alert.error"),
                            I18nManager.get("list.item.set_default.failed", itemName)
                    );
                }
        );
    }

    /**
     * 在列表中查找对应的项
     */
    private T findItem(ObservableList<T> items, T targetItem) {
        return items.stream()
                .filter(item -> matchesItem(item, targetItem))
                .findFirst()
                .orElse(null);
    }

    /**
     * 判断两个项是否匹配（基于candidate和version）
     */
    private boolean matchesItem(T item1, T item2) {
        return item1.getCandidate().equals(item2.getCandidate()) &&
                item1.getVersionIdentifier().equals(item2.getVersionIdentifier());
    }

    /**
     * 安装成功后更新项的状态
     */
    private void updateItemAfterInstall(T item) {
        if (item instanceof SdkVersion version) {
            version.setInstalling(false);
            version.setInstallProgress(null);
            version.setInstalled(true);
        } else if (item instanceof Sdk sdk) {
            sdk.setInstalling(false);
            sdk.setInstallProgress(null);
            sdk.setInstalled(true);
        }
    }

    /**
     * 卸载成功后更新项的状态
     */
    private void updateItemAfterUninstall(T item) {
        if (item instanceof SdkVersion version) {
            version.setInstalling(false);
            version.setInstallProgress(null);
            version.setInstalled(false);
            version.setDefault(false);
            version.setInUse(false);
        } else if (item instanceof Sdk sdk) {
            sdk.setInstalling(false);
            sdk.setInstallProgress(null);
            sdk.setInstalled(false);
        }
    }

    /**
     * 更新项的默认状态
     */
    private void updateItemDefaultState(T item, boolean isDefault) {
        if (item instanceof SdkVersion version) {
            logger.debug("Updating default state: {} {} -> isDefault={}, inUse={}",
                    version.getCandidate(), version.getVersion(), isDefault, isDefault);
            version.setDefault(isDefault);
            version.setInUse(isDefault);
            logger.debug("After update: isDefault={}, inUse={}, isDefaultProperty={}",
                    version.isDefault(), version.isInUse(),
                    version.isDefaultProperty() != null ? version.isDefaultProperty().get() : "null");
        }
    }

    /**
     * 清除安装中状态
     */
    private void clearInstallingState(T item) {
        if (item instanceof SdkVersion version) {
            version.setInstalling(false);
            version.setInstallProgress(null);
        } else if (item instanceof Sdk sdk) {
            sdk.setInstalling(false);
            sdk.setInstallProgress(null);
        }
    }

}
