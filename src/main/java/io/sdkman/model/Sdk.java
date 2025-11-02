package io.sdkman.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.*;
import javafx.collections.FXCollections;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SDK实体类
 * 使用JavaFX属性以支持数据绑定
 */
public class Sdk implements Installable {
    private final StringProperty candidate;
    private final StringProperty name;
    private final StringProperty description;
    private final StringProperty website;  // SDK官网链接
    private final SimpleSetProperty<String> architectures;
    private final StringProperty latestVersion;  // 最新可用版本（从SDKMAN获取）
    private final StringProperty installedVersion;  // 当前已安装并使用的版本号字符串
    private final BooleanProperty installed;
    private final BooleanProperty installing;  // 是否正在安装
    private final StringProperty installProgress;  // 安装进度文本
    private Category category;
    private List<SdkVersion> versions;

    public Sdk() {
        this.candidate = new SimpleStringProperty();
        this.name = new SimpleStringProperty();
        this.description = new SimpleStringProperty();
        this.website = new SimpleStringProperty();
        this.architectures = new SimpleSetProperty<>();
        this.latestVersion = new SimpleStringProperty();
        this.installedVersion = new SimpleStringProperty();
        this.installed = new SimpleBooleanProperty(false);
        this.installing = new SimpleBooleanProperty(false);
        this.installProgress = new SimpleStringProperty();
        this.versions = new ArrayList<>();
    }

    public Sdk(String candidate, String name) {
        this();
        setCandidate(candidate);
        setName(name);
        this.category = Category.fromName(candidate);
    }

    // ==================== Candidate ====================
    public String getCandidate() {
        return candidate.get();
    }

    public StringProperty candidateProperty() {
        return candidate;
    }

    public void setCandidate(String candidate) {
        this.candidate.set(candidate);
        // 自动设置分类
        this.category = Category.fromName(candidate);
    }

    // ==================== Name ====================
    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    // ==================== Description ====================
    public String getDescription() {
        return description.get();
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public void setDescription(String description) {
        this.description.set(description);
    }

    // ==================== Website ====================
    public String getWebsite() {
        return website.get();
    }

    public StringProperty websiteProperty() {
        return website;
    }

    public void setWebsite(String website) {
        this.website.set(website);
    }


    public Set<String> getArchitectures() {
        return architectures.get();
    }

    public SimpleSetProperty<String> architecturesProperty() {
        return architectures;
    }

    public void setArchitectures(Set<String> architectures) {
        if (architectures != null) {
            this.architectures.set(FXCollections.observableSet(architectures));
        }
    }

    // ==================== Latest Version ====================
    public String getLatestVersion() {
        return latestVersion.get();
    }

    public StringProperty latestVersionProperty() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion.set(latestVersion);
    }

    // ==================== Installed Version ====================
    public String getInstalledVersion() {
        return installedVersion.get();
    }

    public StringProperty installedVersionProperty() {
        return installedVersion;
    }

    public void setInstalledVersion(String installedVersion) {
        this.installedVersion.set(installedVersion);
    }

    // ==================== Installed ====================
    public boolean isInstalled() {
        return installed.get();
    }

    public BooleanProperty installedProperty() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed.set(installed);
    }

    // ==================== Installing ====================
    @JsonIgnore
    public boolean isInstalling() {
        return installing.get();
    }

    @JsonIgnore
    public BooleanProperty installingProperty() {
        return installing;
    }

    public void setInstalling(boolean installing) {
        this.installing.set(installing);
    }

    // ==================== Install Progress ====================
    @JsonIgnore
    public String getInstallProgress() {
        return installProgress.get();
    }

    @JsonIgnore
    public StringProperty installProgressProperty() {
        return installProgress;
    }

    public void setInstallProgress(String installProgress) {
        this.installProgress.set(installProgress);
    }

    // ==================== Category ====================
    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    // ==================== Versions ====================
    public List<SdkVersion> getVersions() {
        return versions;
    }

    public void setVersions(List<SdkVersion> versions) {
        this.versions = versions;
    }

    public void addVersion(SdkVersion version) {
        this.versions.add(version);
    }

    /**
     * 获取默认版本
     */
    @JsonIgnore
    public SdkVersion getDefaultVersion() {
        return versions.stream()
                .filter(SdkVersion::isDefault)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取当前使用的版本
     */
    @JsonIgnore
    public SdkVersion getCurrentVersion() {
        return versions.stream()
                .filter(SdkVersion::isInUse)
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查是否有更新可用
     */
    @JsonIgnore
    public boolean hasUpdateAvailable() {
        // 简单逻辑：如果最新版本未安装，则有更新
        String latest = getLatestVersion();
        if (latest == null) {
            return false;
        }

        return versions.stream()
                .filter(v -> v.getVersion().equals(latest))
                .noneMatch(SdkVersion::isInstalled);
    }

    @Override
    public String toString() {
        return "Sdk{" +
                "candidate='" + getCandidate() + '\'' +
                ", name='" + getName() + '\'' +
                ", latestVersion='" + getLatestVersion() + '\'' +
                ", installed=" + isInstalled() +
                ", category=" + category +
                '}';
    }

    // ==================== Installable接口实现 ====================

    @Override
    @JsonIgnore
    public String getVersionIdentifier() {
        // 返回已安装的版本号（用于卸载操作）
        // 如果没有已安装版本，返回空字符串（表示安装最新版本）
        String installed = getInstalledVersion();
        return installed != null ? installed : "";
    }

    @Override
    @JsonIgnore
    public String getDisplayName() {
        return getName();
    }
}
