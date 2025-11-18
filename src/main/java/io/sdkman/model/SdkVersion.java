package io.sdkman.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Set;
import java.util.HashSet;

/**
 * SDK版本信息
 */
public class SdkVersion implements Installable {
    private String version;
    private String identifier;  // SDKMAN使用的唯一标识符（如21.0.9-amzn）
    private String vendor;
    private Set<JdkCategory> categories;  // JDK分类集合（可能同时属于多个分类）
    private String candidate;  // SDK候选名称（如java、gradle、maven等）

    // 用于JSON序列化的boolean字段（映射到JSON的"installed"字段）
    @JsonProperty("installed")
    private boolean installedValue;

    @JsonProperty("default")
    private boolean isDefaultValue;

    @JsonProperty("inUse")
    private boolean inUseValue;

    // 用于UI绑定的Property（不序列化）
    @JsonIgnore
    private BooleanProperty installed;

    @JsonIgnore
    private BooleanProperty isDefaultProperty;

    @JsonIgnore
    private BooleanProperty inUseProperty;

    // 安装状态（使用Property以支持UI绑定）
    @JsonIgnore
    private BooleanProperty installing;  // 是否正在安装

    @JsonIgnore  // Property不能被序列化，使用getter/setter来处理
    private StringProperty installProgress;  // 安装进度文本

    public SdkVersion() {
    }

    public SdkVersion(String version) {
        this.version = version;
    }

    public SdkVersion(String version, String vendor) {
        this.version = version;
        this.vendor = vendor;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public Set<JdkCategory> getCategories() {
        if (categories == null) {
            categories = new HashSet<>();
        }
        return categories;
    }

    public void setCategories(Set<JdkCategory> categories) {
        this.categories = categories;
    }

    /**
     * 检查是否属于指定分类
     */
    @JsonIgnore
    public boolean hasCategory(JdkCategory category) {
        return categories != null && categories.contains(category);
    }

    @JsonIgnore  // 不用于JSON序列化，仅用于业务逻辑
    public boolean isInstalled() {
        // 优先使用Property的值，如果Property未初始化则使用基本字段
        if (installed != null) {
            return installed.get();
        }
        return installedValue;
    }

    @JsonIgnore  // 不用于JSON序列化，使用installedValue字段
    public void setInstalled(boolean value) {
        // 同时更新基本字段和Property
        this.installedValue = value;
        if (this.installed == null) {
            this.installed = new SimpleBooleanProperty(value);
        } else {
            this.installed.set(value);
        }
    }

    @JsonIgnore
    public BooleanProperty installedProperty() {
        if (this.installed == null) {
            // 初始化Property时使用基本字段的值
            this.installed = new SimpleBooleanProperty(installedValue);
        }
        return this.installed;
    }

    @JsonIgnore
    public boolean isDefault() {
        // 优先使用Property的值，如果Property未初始化则使用基本字段
        if (isDefaultProperty != null) {
            return isDefaultProperty.get();
        }
        return isDefaultValue;
    }

    @JsonIgnore
    public void setDefault(boolean value) {
        // 同时更新基本字段和Property
        this.isDefaultValue = value;
        if (this.isDefaultProperty == null) {
            this.isDefaultProperty = new SimpleBooleanProperty(value);
        } else {
            this.isDefaultProperty.set(value);
        }
    }

    @JsonIgnore
    public BooleanProperty isDefaultProperty() {
        if (this.isDefaultProperty == null) {
            // 初始化Property时使用基本字段的值
            this.isDefaultProperty = new SimpleBooleanProperty(isDefaultValue);
        }
        return this.isDefaultProperty;
    }

    @JsonIgnore
    public boolean isInUse() {
        // 优先使用Property的值，如果Property未初始化则使用基本字段
        if (inUseProperty != null) {
            return inUseProperty.get();
        }
        return inUseValue;
    }

    @JsonIgnore
    public void setInUse(boolean value) {
        // 同时更新基本字段和Property
        this.inUseValue = value;
        if (this.inUseProperty == null) {
            this.inUseProperty = new SimpleBooleanProperty(value);
        } else {
            this.inUseProperty.set(value);
        }
    }

    @JsonIgnore
    public BooleanProperty inUseProperty() {
        if (this.inUseProperty == null) {
            // 初始化Property时使用基本字段的值
            this.inUseProperty = new SimpleBooleanProperty(inUseValue);
        }
        return this.inUseProperty;
    }

    public void setCandidate(String candidate) {
        this.candidate = candidate;
    }

    public boolean isInstalling() {
        return installing != null && installing.get();
    }

    public void setInstalling(boolean value) {
        if (this.installing == null) {
            this.installing = new SimpleBooleanProperty(value);
        } else {
            this.installing.set(value);
        }
    }

    @JsonIgnore
    public BooleanProperty installingProperty() {
        if (this.installing == null) {
            this.installing = new SimpleBooleanProperty(false);
        }
        return this.installing;
    }

    public String getInstallProgress() {
        return installProgress == null ? null : installProgress.get();
    }

    public void setInstallProgress(String progress) {
        if (this.installProgress == null) {
            this.installProgress = new SimpleStringProperty(progress);
        } else {
            this.installProgress.set(progress);
        }
    }

    @JsonIgnore  // Property访问器不需要序列化
    public StringProperty installProgressProperty() {
        if (this.installProgress == null) {
            this.installProgress = new SimpleStringProperty();
        }
        return this.installProgress;
    }

    @Override
    public String toString() {
        return "SdkVersion{" +
                "version='" + version + '\'' +
                ", vendor='" + vendor + '\'' +
                ", installed=" + isInstalled() +
                ", isDefault=" + isDefault() +
                ", inUse=" + isInUse() +
                '}';
    }

    // ==================== Installable接口实现 ====================

    @Override
    @JsonIgnore
    public String getCandidate() {
        // 返回设置的candidate字段，如果为null则默认返回"java"（向后兼容）
        return candidate != null ? candidate : "java";
    }

    @Override
    @JsonIgnore
    public String getVersionIdentifier() {
        return identifier;
    }

    @Override
    @JsonIgnore
    public String getDisplayName() {
        return version;
    }
}

