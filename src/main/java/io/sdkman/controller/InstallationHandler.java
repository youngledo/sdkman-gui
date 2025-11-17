package io.sdkman.controller;

import io.sdkman.model.Installable;
import io.sdkman.service.SdkmanService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * 通用安装处理器
 * 封装JDK和SDK的通用安装逻辑，避免代码重复
 */
public class InstallationHandler {
    private static final Logger logger = LoggerFactory.getLogger(InstallationHandler.class);

    private final SdkmanService sdkmanService;

    public InstallationHandler(SdkmanService sdkmanService) {
        this.sdkmanService = sdkmanService;
    }

    /**
     * 执行安装
     *
     * @param item              要安装的项（JDK或SDK）
     * @param installingMessage 安装中的提示消息
     * @param onSuccess         安装成功后的回调（在JavaFX线程中执行）
     * @param onFailure         安装失败后的回调（在JavaFX线程中执行）
     */
    public void install(
            Installable item,
            String installingMessage,
            Consumer<Installable> onSuccess,
            Consumer<Installable> onFailure) {

        logger.info("Installing {}: {} (identifier: {})",
                item.getCandidate(), item.getDisplayName(), item.getVersionIdentifier());

        // 设置安装状态（Property绑定会自动更新UI）
        item.setInstalling(true);
        item.setInstallProgress(installingMessage);

        // 同时更新Service缓存
        sdkmanService.updateInstallState(item, true, installingMessage);

        // 执行安装任务
        Task<Boolean> task = sdkmanService.installTask(item);

        // 监听Task的消息变化，实时更新进度文本
        task.messageProperty().addListener((_, _, newMsg) ->
                Platform.runLater(() -> {
                    if (newMsg != null && !newMsg.isEmpty()) {
                        // 更新进度信息（Property会自动通知UI更新）
                        item.setInstallProgress(newMsg);
                        // 同时更新Service的缓存，这样切换页面后也能保留安装状态
                        sdkmanService.updateInstallState(item, true, newMsg);
                    }
                }));

        task.setOnSucceeded(_ -> {
            boolean success = task.getValue();
            Platform.runLater(() -> {
                // 清除安装状态
                item.setInstalling(false);
                item.setInstallProgress(null);
                // 同时清除Service缓存中的安装状态
                sdkmanService.updateInstallState(item, false, null);

                if (success) {
                    // 执行成功回调（由Controller负责更新installed状态和缓存）
                    if (onSuccess != null) {
                        onSuccess.accept(item);
                    }
                } else {
                    // 执行失败回调
                    if (onFailure != null) {
                        onFailure.accept(item);
                    }

                    logger.error("Failed to install {}: {}",
                            item.getCandidate(), item.getDisplayName());
                }
            });
        });

        task.setOnFailed(_ ->
                Platform.runLater(() -> {
                    // 清除安装状态
                    item.setInstalling(false);
                    item.setInstallProgress(null);
                    // 同时清除Service缓存中的安装状态
                    sdkmanService.updateInstallState(item, false, null);

                    // 执行失败回调
                    if (onFailure != null) {
                        onFailure.accept(item);
                    }

                    logger.error("Installation task failed for {}: {}",
                            item.getCandidate(), item.getDisplayName(), task.getException());
                }));
        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 执行卸载
     */
    public void uninstall(
            Installable item,
            String uninstallingMessage,
            Consumer<Installable> onSuccess,
            Consumer<Installable> onFailure) {

        // 设置卸载状态
        item.setInstalling(true);
        item.setInstallProgress(uninstallingMessage);
        sdkmanService.updateInstallState(item, true, uninstallingMessage);

        // 执行卸载任务
        Task<Boolean> task = sdkmanService.uninstallSdkTask(item.getCandidate(), item.getVersionIdentifier());

        task.setOnSucceeded(_ -> {
            boolean success = task.getValue();
            Platform.runLater(() -> {
                item.setInstalling(false);
                item.setInstallProgress(null);
                sdkmanService.updateInstallState(item, false, null);

                if (success) {
                    if (onSuccess != null) {
                        onSuccess.accept(item);
                    }
                    logger.info("Successfully uninstalled {}: {}", item.getCandidate(), item.getDisplayName());
                } else {
                    if (onFailure != null) {
                        onFailure.accept(item);
                    }
                    logger.error("Failed to uninstall {}: {}", item.getCandidate(), item.getDisplayName());
                }
            });
        });

        task.setOnFailed(_ ->
                Platform.runLater(() -> {
                    item.setInstalling(false);
                    item.setInstallProgress(null);
                    sdkmanService.updateInstallState(item, false, null);

                    if (onFailure != null) {
                        onFailure.accept(item);
                    }
                    logger.error("Uninstall task failed for {}: {}", item.getCandidate(), item.getDisplayName(), task.getException());
                }));

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }

    /**
     * 设置默认版本
     */
    public void setDefault(
            Installable item,
            String settingMessage,
            Consumer<Installable> onSuccess,
            Consumer<Installable> onFailure) {

        logger.info("Setting default {}: {} (identifier: {})",
                item.getCandidate(), item.getDisplayName(), item.getVersionIdentifier());

        // 设置状态
        item.setInstalling(true);
        item.setInstallProgress(settingMessage);
        sdkmanService.updateInstallState(item, true, settingMessage);

        // 执行设置默认任务
        Task<Boolean> task = sdkmanService.setDefaultTask(item.getCandidate(), item.getVersionIdentifier());

        task.setOnSucceeded(_ -> {
            boolean success = task.getValue();
            Platform.runLater(() -> {
                item.setInstalling(false);
                item.setInstallProgress(null);
                sdkmanService.updateInstallState(item, false, null);

                if (success) {
                    if (onSuccess != null) {
                        onSuccess.accept(item);
                    }
                    logger.info("Successfully set default {}: {}", item.getCandidate(), item.getDisplayName());
                } else {
                    if (onFailure != null) {
                        onFailure.accept(item);
                    }
                    logger.error("Failed to set default {}: {}", item.getCandidate(), item.getDisplayName());
                }
            });
        });

        task.setOnFailed(_ ->
                Platform.runLater(() -> {
                    item.setInstalling(false);
                    item.setInstallProgress(null);
                    sdkmanService.updateInstallState(item, false, null);

                    if (onFailure != null) {
                        onFailure.accept(item);
                    }
                    logger.error("Set default task failed for {}: {}", item.getCandidate(), item.getDisplayName(), task.getException());
                }));

        io.sdkman.util.ThreadManager.getInstance().executeJavaFxTask(task);
    }


}
