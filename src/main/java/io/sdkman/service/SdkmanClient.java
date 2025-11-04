package io.sdkman.service;

import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;

import java.util.List;

/**
 * SDKMAN客户端接口
 * 定义SDK管理的统一操作接口，支持CLI和HTTP API两种实现方式
 */
public interface SdkmanClient {

    /**
     * 获取所有可用的SDK候选列表
     *
     * @return SDK候选列表（包含名称、版本、网站等详细信息）
     */
    List<Sdk> listCandidates();

    /**
     * 获取指定SDK的所有版本
     *
     * @param candidate SDK候选名称（如java、maven）
     * @return 版本列表
     */
    List<SdkVersion> listVersions(String candidate);

    /**
     * 获取SDK版本列表
     *
     * @param candidate SDK候选名称
     * @param useProxy 是否使用代理（从网络获取时需要）
     * @return 版本列表
     */
    List<SdkVersion> listVersions(String candidate, boolean useProxy);

    /**
     * 安装指定版本的SDK（带进度回调）
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @param progressCallback 进度回调接口，接收实时输出
     * @return 是否安装成功
     */
    boolean install(String candidate, String version, ProgressCallback progressCallback);

    /**
     * 卸载指定版本的SDK
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否卸载成功
     */
    boolean uninstall(String candidate, String version);

    /**
     * 设置默认版本
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否设置成功
     */
    boolean setDefault(String candidate, String version);

    /**
     * 获取当前使用的版本
     *
     * @param candidate SDK候选名称
     * @return 当前版本号，如果未安装则返回null
     */
    String getCurrentVersion(String candidate);

    /**
     * 检查候选是否已安装（通过检查candidates目录）
     *
     * @param candidate SDK候选名称
     * @return 是否已安装
     */
    boolean isCandidateInstalled(String candidate);

    /**
     * 检查SDKMAN是否已安装
     *
     * @return 是否已安装
     */
    boolean isSdkmanInstalled();

    /**
     * 进度回调接口
     */
    interface ProgressCallback {
        void onProgress(String line);
    }

    /**
     * 获取客户端类型
     *
     * @return 客户端类型描述
     */
    String getClientType();

    /**
     * 检查客户端是否可用
     *
     * @return 是否可用
     */
    boolean isAvailable();
}
