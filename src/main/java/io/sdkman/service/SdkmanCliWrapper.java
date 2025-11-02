package io.sdkman.service;

import io.sdkman.model.JdkCategory;
import io.sdkman.model.Sdk;
import io.sdkman.model.SdkVersion;
import io.sdkman.util.ConfigManager;
import org.apache.commons.exec.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SDKMAN命令行封装类
 * 负责执行SDKMAN命令并解析输出
 */
public class SdkmanCliWrapper {
    private static final Logger logger = LoggerFactory.getLogger(SdkmanCliWrapper.class);

    private static final String BASH_PATH = "/bin/bash";
    private String sdkmanInitScript;

    // 代理检测结果缓存（避免频繁检测，特别是启动时）
    private String cachedProxySettings = null;
    private long proxySettingsCacheTime = 0;
    private static final long PROXY_CACHE_DURATION_MS = 5 * 60 * 1000; // 5分钟缓存

    public SdkmanCliWrapper() {
        String sdkmanPath = ConfigManager.getSdkmanPath();
        this.sdkmanInitScript = sdkmanPath + "/bin/sdkman-init.sh";
        logger.info("Initialized SdkmanCliWrapper with path: {}", sdkmanPath);
    }

    /**
     * 获取所有可用的SDK候选列表
     * Gets all available SDK candidates with detailed information
     *
     * @return SDK候选列表（包含名称、版本、网站等详细信息）
     */
    public List<Sdk> listCandidates() {
        try {
            String command = String.format("source %s && sdk list", sdkmanInitScript);
            String output = executeCommand(command);

            List<Sdk> candidates = parseCandidatesList(output);
            logger.info("Found {} SDK candidates", candidates.size());

            return candidates;

        } catch (Exception e) {
            logger.warn("Failed to list SDK candidates: {}", e.getMessage());
            logger.debug("Full stack trace:", e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定SDK的所有版本
     *
     * @param candidate SDK候选名称（如java、maven）
     * @return 版本列表
     */
    /**
     * 获取SDK版本列表
     *
     * @param candidate SDK候选名称
     * @return 版本列表
     */
    public List<SdkVersion> listVersions(String candidate) {
        return listVersions(candidate, true); // 默认使用代理（可能需要从网络获取）
    }

    /**
     * 获取SDK版本列表
     *
     * @param candidate SDK候选名称
     * @param useProxy 是否使用代理（从网络获取时需要）
     * @return 版本列表
     */
    public List<SdkVersion> listVersions(String candidate, boolean useProxy) {
        logger.debug("Fetching versions for candidate: {} (useProxy={})", candidate, useProxy);

        try {
            String command = String.format("source %s && sdk list %s", sdkmanInitScript, candidate);
            String output = executeCommand(command, useProxy);

            List<SdkVersion> versions = parseVersionsList(output, candidate);
            logger.debug("Found {} versions for {}", versions.size(), candidate);

            return versions;

        } catch (Exception e) {
            logger.debug("Failed to list versions for candidate {}: {}", candidate, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 安装指定版本的SDK（带进度回调）
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @param progressCallback 进度回调接口，接收实时输出
     * @return 是否安装成功
     */
    public boolean install(String candidate, String version, ProgressCallback progressCallback) {
        logger.info("Installing {} version {} without setting as default", candidate, version);

        try {
            // 先获取当前的默认版本（如果有的话）
            String currentDefault = getCurrentVersion(candidate);
            logger.debug("Current default version for {}: {}", candidate, currentDefault);

            // 构建安装命令，使用非交互模式
            String command = String.format("""
                source %s
                # 设置环境变量以避免交互式询问
                export SDKMAN_YES=true
                export AUTO_INSTALL=true

                # 安装指定版本，不自动设置为默认
                sdk install %s %s

                # 如果之前有默认版本，且新安装的版本不是原来的默认版本，则恢复默认版本
                %s
                """,
                sdkmanInitScript, candidate, version,
                currentDefault != null ?
                    String.format("if sdk current %s | grep -q '%s'; then echo 'Original default already re-established'; else sdk default %s %s; fi", candidate, currentDefault, candidate, currentDefault) :
                    "echo 'No previous default version to restore'");

            // 使用10分钟超时，并传递进度回调
            String output = executeCommandWithProgress(command, 600, progressCallback);

            boolean success = output.contains("Done installing") ||
                            output.contains("successfully installed");

            if (success) {
                logger.info("Successfully installed {} {}", candidate, version);
            } else {
                logger.warn("Installation may have failed: {}", output);
            }

            return success;

        } catch (Exception e) {
            logger.warn("Failed to install {} {}: {}", candidate, version, e.getMessage());
            logger.debug("Full stack trace:", e);
            return false;
        }
    }

    /**
     * 进度回调接口
     */
    public interface ProgressCallback {
        void onProgress(String line);
    }

    /**
     * 卸载指定版本的SDK
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否卸载成功
     */
    public boolean uninstall(String candidate, String version) {
        logger.info("Uninstalling {} version {}", candidate, version);

        try {
            String command = String.format("source %s && sdk uninstall %s %s --force",
                    sdkmanInitScript, candidate, version);
            String output = executeCommand(command);

            boolean success = output.contains("uninstalled") ||
                            output.contains("removed");

            if (success) {
                logger.info("Successfully uninstalled {} {}", candidate, version);
            } else {
                logger.warn("Uninstallation may have failed: {}", output);
            }

            return success;

        } catch (Exception e) {
            logger.warn("Failed to uninstall {} {}: {}", candidate, version, e.getMessage());
            logger.debug("Full stack trace:", e);
            return false;
        }
    }

    /**
     * 设置默认版本
     *
     * @param candidate SDK候选名称
     * @param version   版本号
     * @return 是否设置成功
     */
    public boolean setDefault(String candidate, String version) {
        try {
            String command = String.format("source %s && sdk default %s %s",
                    sdkmanInitScript, candidate, version);
            String output = executeCommand(command);
            return output.contains("Default") ||
                            output.contains("set") ||
                            output.contains("now in use");
        } catch (Exception e) {
            logger.warn("Failed to set default for {} {}: {}", candidate, version, e.getMessage());
            logger.debug("Full stack trace:", e);
            return false;
        }
    }

    /**
     * 获取当前使用的版本
     *
     * @param candidate SDK候选名称
     * @return 当前版本号，如果未安装则返回null
     */
    public String getCurrentVersion(String candidate) {
        logger.debug("Getting current version for: {}", candidate);

        try {
            String command = String.format("source %s && sdk current %s",
                    sdkmanInitScript, candidate);
            String output = executeCommand(command);

            String version = parseCurrentVersion(output);
            logger.debug("Current version of {}: {}", candidate, version);

            return version;

        } catch (Exception e) {
            logger.debug("Failed to get current version for {}: {}", candidate, e.getMessage());
            return null;
        }
    }

    /**
     * 检查候选是否已安装（通过检查candidates目录）
     *
     * @param candidate SDK候选名称
     * @return 是否已安装
     */
    public boolean isCandidateInstalled(String candidate) {
        logger.debug("Checking if candidate is installed: {}", candidate);

        try {
            String candidatesDir = ConfigManager.getSdkmanPath() + "/candidates/" + candidate;
            java.io.File dir = new java.io.File(candidatesDir);

            if (!dir.exists() || !dir.isDirectory()) {
                logger.debug("Candidate directory does not exist: {}", candidatesDir);
                return false;
            }

            // 检查是否有版本安装（排除current软链接）
            java.io.File[] files = dir.listFiles();
            if (files == null) {
                return false;
            }

            for (java.io.File file : files) {
                if (file.isDirectory() && !"current".equals(file.getName())) {
                    logger.debug("Found installed version for {}: {}", candidate, file.getName());
                    return true;
                }
            }

            logger.debug("No installed versions found for {}", candidate);
            return false;

        } catch (Exception e) {
            logger.debug("Failed to check if candidate {} is installed: {}", candidate, e.getMessage());
            return false;
        }
    }

    /**
     * 执行Shell命令并实时捕获进度
     *
     * @param command 要执行的命令
     * @param timeoutSeconds 超时秒数
     * @param progressCallback 进度回调
     * @return 命令输出
     * @throws IOException 如果执行失败或超时
     */
    private String executeCommandWithProgress(String command, int timeoutSeconds, ProgressCallback progressCallback) throws IOException {
        logger.debug("Executing command with progress tracking and {} seconds timeout: {}", timeoutSeconds, command);

        // 添加代理设置到命令中
        String fullCommand = buildCommandWithProxy(command);

        CommandLine cmdLine = new CommandLine(BASH_PATH);
        cmdLine.addArgument("-c");
        cmdLine.addArgument(fullCommand, false);

        DefaultExecutor executor = DefaultExecutor.builder().get();
        executor.setExitValues(new int[]{0, 1});

        ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                .setTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .get();
        executor.setWatchdog(watchdog);

        // 创建捕获输出并实时回调的流处理器
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        // 创建包装输出流，实时读取并回调
        OutputStream progressOutputStream = new OutputStream() {
            private final StringBuilder lineBuffer = new StringBuilder();

            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
                char c = (char) b;

                if (c == '\n' || c == '\r') {
                    if (lineBuffer.length() > 0) {
                        String line = lineBuffer.toString().trim();
                        if (!line.isEmpty() && progressCallback != null) {
                            progressCallback.onProgress(line);
                        }
                        lineBuffer.setLength(0);
                    }
                } else {
                    lineBuffer.append(c);
                }
            }

            @Override
            public void flush() throws IOException {
                outputStream.flush();
                // 刷新时也发送当前缓冲的内容
                if (lineBuffer.length() > 0) {
                    String line = lineBuffer.toString().trim();
                    if (!line.isEmpty() && progressCallback != null) {
                        progressCallback.onProgress(line);
                    }
                }
            }
        };

        PumpStreamHandler streamHandler = new PumpStreamHandler(progressOutputStream, errorStream);
        executor.setStreamHandler(streamHandler);

        try {
            int exitCode = executor.execute(cmdLine);
            logger.debug("Command exit code: {}", exitCode);

            String output = outputStream.toString();
            String error = errorStream.toString();

            return output + error;

        } catch (ExecuteException e) {
            logger.error("Command execution failed with unexpected exit code: {}", e.getExitValue(), e);
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        } catch (IOException e) {
            if (watchdog.killedProcess()) {
                logger.error("Command execution timed out after {} seconds", timeoutSeconds);
                throw new IOException("Command execution timed out", e);
            }
            logger.error("Command execution error", e);
            throw e;
        }
    }

    /**
     * 执行Shell命令（使用默认60秒超时，不使用代理）
     * 用于本地操作，不需要网络的命令
     *
     * @param command 要执行的命令
     * @return 命令输出
     * @throws IOException 如果执行失败或超时
     */
    private String executeCommand(String command) throws IOException {
        return executeCommand(command, 60, false);
    }

    /**
     * 执行Shell命令（使用默认60秒超时）
     * 使用Apache Commons Exec执行，提供超时保护
     *
     * @param command 要执行的命令
     * @param useProxy 是否使用代理（仅网络操作需要）
     * @return 命令输出
     * @throws IOException 如果执行失败或超时
     */
    private String executeCommand(String command, boolean useProxy) throws IOException {
        return executeCommand(command, 60, useProxy);
    }

    /**
     * 执行Shell命令（自定义超时和代理设置）
     * 使用Apache Commons Exec执行，提供超时保护
     *
     * @param command 要执行的命令
     * @param timeoutSeconds 超时秒数
     * @param useProxy 是否使用代理（仅网络操作需要）
     * @return 命令输出
     * @throws IOException 如果执行失败或超时
     */
    private String executeCommand(String command, int timeoutSeconds, boolean useProxy) throws IOException {
        logger.debug("Executing command with {} seconds timeout (useProxy={}): {}", timeoutSeconds, useProxy, command);

        // 只有需要网络的命令才添加代理设置
        String fullCommand = useProxy ? buildCommandWithProxy(command) : command;

        // 创建命令行
        CommandLine cmdLine = new CommandLine(BASH_PATH);
        cmdLine.addArgument("-c");
        cmdLine.addArgument(fullCommand, false);

        // 配置执行器
        DefaultExecutor executor = DefaultExecutor.builder().get();

        // 接受退出码0和1（SDKMAN某些命令失败时返回1，这是正常的）
        executor.setExitValues(new int[]{0, 1});

        // 设置自定义超时
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                .setTimeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .get();
        executor.setWatchdog(watchdog);

        // 捕获输出
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
        PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, errorStream);
        executor.setStreamHandler(streamHandler);

        try {
            int exitCode = executor.execute(cmdLine);
            logger.debug("Command exit code: {}", exitCode);

            String output = outputStream.toString();
            String error = errorStream.toString();

            // 合并标准输出和错误输出
            String result = output + error;
            logger.debug("Command output length: {} chars", result.length());

            return result;

        } catch (ExecuteException e) {
            // 只有在非预期退出码时才会到这里
            logger.error("Command execution failed with unexpected exit code: {}", e.getExitValue(), e);
            throw new IOException("Command execution failed: " + e.getMessage(), e);
        } catch (IOException e) {
            if (watchdog.killedProcess()) {
                logger.error("Command execution timed out after 60 seconds");
                throw new IOException("Command execution timed out", e);
            }
            logger.error("Command execution error", e);
            throw e;
        }
    }

    /**
     * 解析候选列表输出
     * Parses the output of 'sdk list' command to extract SDK information
     *
     * @param output sdk list命令的输出
     * @return SDK候选列表（包含名称、版本、网站等详细信息）
     */
    private List<Sdk> parseCandidatesList(String output) {
        List<Sdk> candidates = new ArrayList<>();

        // SDKMAN输出格式示例：
        // Available Candidates
        // ================================================================================
        // q-quit                                  /-search down
        //
        // --------------------------------------------------------------------------------
        // Apache ActiveMQ (Classic) (5.17.1)                  https://activemq.apache.org/
        //
        // Apache ActiveMQ® is a popular open source, multi-protocol, Java-based message
        // broker. ... (多行描述)
        //
        //                                                   $ sdk install activemq
        // --------------------------------------------------------------------------------

        String[] lines = output.split("\n");

        // 正则表达式模式
        // 标题行：名称 (版本)                            网站
        Pattern titlePattern = Pattern.compile("^([^(]+?)\\s*\\(([^)]+)\\)\\s+(https?://\\S+)\\s*$");
        // 安装命令行：$ sdk install <candidate>
        Pattern installPattern = Pattern.compile("^\\s*\\$\\s+sdk\\s+install\\s+([a-z][a-z0-9-]*)\\s*$", Pattern.CASE_INSENSITIVE);

        Sdk currentSdk = null;
        StringBuilder descriptionBuilder = new StringBuilder();
        boolean parsingDescription = false;

        for (String line : lines) {
            String trimmedLine = line.trim();

            // 跳过分隔线和空行（在不解析描述时）
            if (trimmedLine.contains("===") ||
                trimmedLine.contains("q-quit") ||
                trimmedLine.contains("Available Candidates")) {
                continue;
            }

            // 匹配标题行（名称、版本、网站）
            Matcher titleMatcher = titlePattern.matcher(trimmedLine);
            if (titleMatcher.matches()) {
                // 保存上一个SDK（如果有）
                if (currentSdk != null) {
                    currentSdk.setDescription(descriptionBuilder.toString().trim());
                    candidates.add(currentSdk);
                    logger.debug("Parsed SDK: {} - {}", currentSdk.getCandidate(), currentSdk.getName());
                }

                // 创建新的SDK对象
                String name = titleMatcher.group(1).trim();
                String version = titleMatcher.group(2).trim();
                String website = titleMatcher.group(3).trim();

                currentSdk = new Sdk();
                currentSdk.setName(name);
                currentSdk.setLatestVersion(version);
                currentSdk.setWebsite(website);

                descriptionBuilder = new StringBuilder();
                parsingDescription = false;
                continue;
            }

            // 匹配安装命令行（提取candidate ID）
            Matcher installMatcher = installPattern.matcher(trimmedLine);
            if (installMatcher.matches() && currentSdk != null) {
                String candidate = installMatcher.group(1).toLowerCase();
                currentSdk.setCandidate(candidate);
                parsingDescription = false;
                continue;
            }

            // 跳过分隔线
            if (trimmedLine.contains("---")) {
                parsingDescription = false;
                continue;
            }

            // 解析描述文本（在标题和安装命令之间的内容）
            if (currentSdk != null && !trimmedLine.isEmpty() && currentSdk.getCandidate() == null) {
                if (descriptionBuilder.length() > 0) {
                    descriptionBuilder.append(" ");
                }
                descriptionBuilder.append(trimmedLine);
                parsingDescription = true;
            }
        }

        // 保存最后一个SDK
        if (currentSdk != null && currentSdk.getCandidate() != null) {
            currentSdk.setDescription(descriptionBuilder.toString().trim());
            candidates.add(currentSdk);
            logger.debug("Parsed SDK: {} - {}", currentSdk.getCandidate(), currentSdk.getName());
        }

        logger.info("Parsed {} SDK candidates with detailed information", candidates.size());
        return candidates;
    }

    /**
     * 解析版本列表输出
     *
     * @param output    sdk list <candidate>命令的输出
     * @param candidate SDK候选名称
     * @return 版本列表
     */
    private List<SdkVersion> parseVersionsList(String output, String candidate) {
        List<SdkVersion> versions = new ArrayList<>();

        logger.debug("Parsing version list for {}, output length: {}", candidate, output.length());

        String[] lines = output.split("\n");
        logger.debug("Total lines: {}", lines.length);

        // 检测输出格式：Java格式（有表格）还是简单格式（只有版本号）
        boolean isJavaFormat = output.contains("Vendor") || output.contains("|");
        logger.debug("Detected format for {}: {}", candidate, isJavaFormat ? "Java (table)" : "Simple (version list)");

        if (isJavaFormat) {
            // Java格式：带Vendor表格的格式
            String currentVendor = "";
            for (String line : lines) {
                line = line.trim();

                // 跳过表头和分隔符
                if (line.isEmpty() || line.contains("===") || line.contains("---") ||
                    line.contains("Vendor") || line.contains("Available")) {
                    continue;
                }

                // 解析版本行
                SdkVersion version = parseVersionLine(line, currentVendor);
                if (version != null) {
                    // 更新当前vendor（如果解析到了新的vendor）
                    if (version.getVendor() != null && !version.getVendor().isEmpty()) {
                        currentVendor = version.getVendor();
                    }
                    versions.add(version);
                    logger.debug("Parsed Java version: {} from vendor: {}", version.getVersion(), version.getVendor());
                } else {
                    logger.debug("Failed to parse line: {}", line);
                }
            }
        } else {
            // 简单格式：只有版本号列表
            for (String line : lines) {
                line = line.trim();

                // 跳过表头、分隔符和说明行
                if (line.isEmpty() || line.contains("===") || line.contains("---") ||
                    line.contains("Available") || line.contains("local version") ||
                    line.contains("- installed") || line.contains("currently in use")) {
                    continue;
                }

                // 解析简单版本行（一行可能有多个版本号，用空格分隔）
                List<SdkVersion> lineVersions = parseSimpleVersionLine(line, candidate);
                if (lineVersions != null && !lineVersions.isEmpty()) {
                    versions.addAll(lineVersions);
                    logger.debug("Parsed {} versions from line", lineVersions.size());
                }
            }
        }

        logger.debug("Parsed {} versions for {}", versions.size(), candidate);
        return versions;
    }

    /**
     * 解析单行版本信息
     *
     * @param line 版本行
     * @param currentVendor 当前vendor（用于处理空vendor的行）
     * @return SdkVersion对象，如果解析失败则返回null
     */
    private SdkVersion parseVersionLine(String line, String currentVendor) {
        try {
            // 使用正则表达式解析
            // 格式：Vendor | Use | Version | Dist | Status | Identifier
            String[] parts = line.split("\\|");

            if (parts.length >= 6) {
                String vendor = parts[0].trim();
                String use = parts[1].trim();
                String version = parts[2].trim();
                String status = parts[4].trim();
                String identifier = parts[5].trim();

                // 如果vendor为空，使用currentVendor（同一vendor的后续行）
                if (vendor.isEmpty() && currentVendor != null && !currentVendor.isEmpty()) {
                    vendor = currentVendor;
                }

                if (!version.isEmpty() && !identifier.isEmpty()) {
                    SdkVersion sdkVersion = new SdkVersion(version);
                    sdkVersion.setIdentifier(identifier);  // 设置identifier
                    sdkVersion.setVendor(vendor);
                    sdkVersion.setInstalled(status.contains("installed"));
                    sdkVersion.setInUse(use.contains(">"));
                    sdkVersion.setDefault(use.contains(">"));

                    // 根据identifier识别JDK类别
                    sdkVersion.setCategory(JdkCategory.fromIdentifier(identifier));

                    return sdkVersion;
                }
            }

        } catch (Exception e) {
            logger.debug("Failed to parse version line: {}", line, e);
        }

        return null;
    }

    /**
     * 解析简单格式的版本行（非Java SDK）
     * 一行可能包含多个版本号，用空格分隔
     *
     * @param line 版本行（格式："> * 1.10.14             1.9.15"）
     * @param candidate SDK候选名称
     * @return SdkVersion列表，如果解析失败则返回空列表
     */
    private List<SdkVersion> parseSimpleVersionLine(String line, String candidate) {
        List<SdkVersion> versions = new ArrayList<>();

        try {
            // 按空格拆分所有token（包括标记符号和版本号）
            String[] tokens = line.trim().split("\\s+");

            boolean isCurrentInUse = false;  // 当前行是否有 > 标记
            boolean isCurrentInstalled = false;  // 当前版本是否已安装
            boolean firstVersion = true;      // 是否是第一个版本

            for (String token : tokens) {
                if (token.isEmpty()) {
                    continue;
                }

                // 检查标记符号
                if (token.equals(">")) {
                    isCurrentInUse = true;
                    continue;
                } else if (token.equals("*")) {
                    isCurrentInstalled = true;
                    continue;
                }

                // 如果不是标记符号，则是版本号
                SdkVersion version = new SdkVersion(token);
                version.setIdentifier(token);  // 简单SDK使用版本号作为identifier
                version.setVendor("");  // 简单SDK没有vendor概念

                // 设置安装状态（应用当前累积的状态）
                version.setInstalled(isCurrentInstalled);

                // 只有第一个版本可能是"当前使用"的版本
                if (firstVersion && isCurrentInUse) {
                    version.setInUse(true);
                    version.setDefault(true);
                    firstVersion = false;
                } else {
                    version.setInUse(false);
                    version.setDefault(false);
                }

                versions.add(version);

                // 重置状态，为下一个版本��准备
                // 注意：> 标记只对第一个版本有效，但 * 标记每个版本都独立
                isCurrentInstalled = false;
                isCurrentInUse = false;  // 重置 inUse 状态，确保只有第一个版本有 > 标记
            }

        } catch (Exception e) {
            logger.debug("Failed to parse simple version line: {}", line, e);
        }

        return versions;
    }

    /**
     * 解析当前版本输出
     *
     * @param output sdk current命令的输出
     * @return 当前版本号
     */
    private String parseCurrentVersion(String output) {
        // 输出格式：Using java version 17.0.13-amzn
        Pattern pattern = Pattern.compile("Using .+ version ([\\S]+)");
        Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * 检查SDKMAN是否已安装
     *
     * @return 是否已安装
     */
    public boolean isSdkmanInstalled() {
        try {
            // 性能优化：直接检查文件是否存在，而不是执行命令
            // 这样可以从2秒减少到几毫秒
            java.io.File initScript = new java.io.File(sdkmanInitScript);
            java.io.File sdkmanDir = new java.io.File(ConfigManager.getSdkmanPath());

            boolean installed = initScript.exists() && sdkmanDir.exists() && sdkmanDir.isDirectory();

            logger.info("SDKMAN installed: {} (checked by file existence)", installed);
            return installed;

        } catch (Exception e) {
            logger.warn("Failed to check SDKMAN installation: {}", e.getMessage());
            logger.debug("Full stack trace:", e);
            return false;
        }
    }

    /**
     * 构建包含代理设置的命令（使用缓存避免频繁检测）
     *
     * @param originalCommand 原始命令
     * @return 包含代理设置的完整命令
     */
    private String buildCommandWithProxy(String originalCommand) {
        String proxyType = ConfigManager.getProxyType();

        // 如果不使用代理，直接返回
        if ("none".equals(proxyType)) {
            return originalCommand;
        }

        // 检查缓存是否有效（5分钟内）
        long now = System.currentTimeMillis();
        boolean cacheValid = cachedProxySettings != null && (now - proxySettingsCacheTime) < PROXY_CACHE_DURATION_MS;

        if (cacheValid) {
            logger.debug("Using cached proxy settings (age: {}s)", (now - proxySettingsCacheTime) / 1000);
            // 如果缓存的是空字符串，表示没有检测到代理
            if (cachedProxySettings.isEmpty()) {
                return originalCommand;
            }
            return cachedProxySettings + "\n\n" + originalCommand;
        }

        // 缓存失效或不存在，重新检测
        logger.debug("Detecting proxy settings (cache expired or not available)...");
        String proxyPrefix = switch (proxyType) {
            case "auto" -> detectAutoProxy(); // 自动检测
            case "manual" -> buildManualProxy(); // 手动配置
            default -> "";
        };

        // 更新缓存
        cachedProxySettings = proxyPrefix;
        proxySettingsCacheTime = now;

        if (proxyPrefix.isEmpty()) {
            return originalCommand;
        }
        return proxyPrefix + "\n\n" + originalCommand;
    }

    /**
     * 检测系统代理设置并返回代理配置前缀（跨平台）
     * 使用Java标准ProxySelector API（支持Windows/Linux/macOS）
     */
    private String detectAutoProxy() {
        try {
            // 临时启用系统代理检测
            String previousValue = System.getProperty("java.net.useSystemProxies");
            System.setProperty("java.net.useSystemProxies", "true");

            try {
                // 使用Java标准API获取系统代理（跨平台）
                java.net.ProxySelector proxySelector = java.net.ProxySelector.getDefault();
                if (proxySelector != null) {
                    java.net.URI uri = new java.net.URI("https://api.sdkman.io");
                    java.util.List<java.net.Proxy> proxies = proxySelector.select(uri);

                    for (java.net.Proxy proxy : proxies) {
                        if (proxy.type() == java.net.Proxy.Type.HTTP) {
                            java.net.InetSocketAddress addr = (java.net.InetSocketAddress) proxy.address();
                            if (addr != null) {
                                String proxyHost = addr.getHostString();
                                int proxyPort = addr.getPort();
                                String proxyUrl = String.format("http://%s:%d", proxyHost, proxyPort);
                                logger.info("Detected system proxy: {}", proxyUrl);

                                return String.format("""
                                    # 使用系统代理
                                    export http_proxy="%s"
                                    export https_proxy="%s"
                                    """, proxyUrl, proxyUrl);
                            }
                        }
                    }
                }
            } finally {
                // 恢复之前的设置
                if (previousValue != null) {
                    System.setProperty("java.net.useSystemProxies", previousValue);
                } else {
                    System.clearProperty("java.net.useSystemProxies");
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to detect system proxy: {}", e.getMessage());
        }

        // 如果没有检测到系统代理，返回空字符串
        logger.debug("No system proxy detected, using direct connection");
        return "";
    }

    /**
     * 构建手动配置代理的命令前缀
     */
    private String buildManualProxy() {
        String proxyHost = ConfigManager.getProxyHost();
        String proxyPort = ConfigManager.getProxyPort();

        if (proxyHost == null || proxyHost.trim().isEmpty() || proxyPort == null || proxyPort.trim().isEmpty()) {
            logger.warn("Manual proxy enabled but host or port is missing");
            return "";
        }

        String proxyUrl = String.format("http://%s:%s", proxyHost.trim(), proxyPort.trim());
        logger.info("Using manual proxy: {}", proxyUrl);

        return String.format("""
            # 设置手动代理配置
            export http_proxy="%s"
            export https_proxy="%s"
            """, proxyUrl, proxyUrl);
    }
}
