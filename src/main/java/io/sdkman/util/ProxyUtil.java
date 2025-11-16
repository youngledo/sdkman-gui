package io.sdkman.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;

///
/// # ProxyUtil
///
/// Utility class for proxy configuration
/// 代理配置工具类
///
/// ## Features
/// - Creates HttpClient with proxy configuration
/// - Supports manual, auto, and no proxy modes
/// - Reads proxy settings from ConfigManager
///
public class ProxyUtil {
    private static final Logger logger = LoggerFactory.getLogger(ProxyUtil.class);

    ///
    /// Configures HttpClient.Builder with proxy settings
    /// 配置HttpClient.Builder的代理设置
    ///
    /// @param builder HttpClient.Builder to configure
    /// @param clientName Name of the client for logging purposes
    ///
    public static void configureProxy(HttpClient.Builder builder, String clientName) {
        // 检查代理配置
        String proxyType = ConfigManager.getProxyType();
        logger.debug("{} proxy type: {}", clientName, proxyType);

        if ("manual".equals(proxyType)) {
            // 手动配置代理
            String proxyHost = ConfigManager.getProxyHost();
            String proxyPort = ConfigManager.getProxyPort();
            if (!proxyHost.isEmpty() && !proxyPort.isEmpty()) {
                try {
                    InetSocketAddress proxyAddress = new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort));
                    ProxySelector proxySelector = ProxySelector.of(proxyAddress);
                    builder.proxy(proxySelector);
                    logger.info("{} using manual proxy: {}:{}", clientName, proxyHost, proxyPort);
                } catch (NumberFormatException e) {
                    logger.warn("{} has invalid proxy port: {}", clientName, proxyPort);
                }
            }
        } else if ("auto".equals(proxyType)) {
            // 自动检测代理
            ProxySelector defaultProxySelector = ProxySelector.getDefault();
            if (defaultProxySelector != null) {
                builder.proxy(defaultProxySelector);
                logger.info("{} using system default proxy selector", clientName);
            } else {
                logger.warn("No system proxy selector available for {}, using direct connection", clientName);
            }
        } else {
            // 无代理或默认情况
            logger.info("{} using default proxy configuration", clientName);
        }
    }

    ///
    /// Creates HttpClient with proxy configuration
    /// 创建带代理配置的HttpClient
    ///
    /// @param clientName Name of the client for logging purposes
    /// @return Configured HttpClient instance
    ///
    public static HttpClient createHttpClient(String clientName) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        configureProxy(builder, clientName);

        return builder.build();
    }
}