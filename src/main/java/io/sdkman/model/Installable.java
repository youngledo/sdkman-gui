package io.sdkman.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;

/**
 * 可安装项接口
 * 为JDK和SDK提供统一的安装状态管理
 */
public interface Installable {

    /**
     * 获取候选名称（如 "java", "maven", "gradle"）
     */
    String getCandidate();

    /**
     * 获取版本标识符（对于JDK是identifier，对于SDK是空字符串表示最新版）
     */
    String getVersionIdentifier();

    /**
     * 获取显示名称
     */
    String getDisplayName();

    /**
     * 是否已安装
     */
    boolean isInstalled();

    /**
     * 设置已安装状态
     */
    void setInstalled(boolean installed);

    /**
     * 已安装属性
     */
    BooleanProperty installedProperty();

    /**
     * 是否正在安装
     */
    boolean isInstalling();

    /**
     * 设置正在安装状态
     */
    void setInstalling(boolean installing);

    /**
     * 正在安装属性
     */
    BooleanProperty installingProperty();

    /**
     * 获取安装进度文本
     */
    String getInstallProgress();

    /**
     * 设置安装进度文本
     */
    void setInstallProgress(String progress);

    /**
     * 安装进度属性
     */
    StringProperty installProgressProperty();
}
