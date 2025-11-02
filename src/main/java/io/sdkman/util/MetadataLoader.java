package io.sdkman.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * SDK元数据加载器
 * 从本地JSON文件加载SDK的描述和官网链接
 */
public class MetadataLoader {
    private static final Logger logger = LoggerFactory.getLogger(MetadataLoader.class);
    public static final String METADATA_FILE = "/metadata/sdk-metadata.json";

    private static Map<String, SdkMetadata> metadataSdkMap;

    /**
     * SDK元数据
     */
    public record SdkMetadata(
            String name,
            String description
    ) {
    }

    /**
     * 加载SDK元数据
     */
    private static synchronized void loadMetadata() {
        if (metadataSdkMap != null) {
            return;
        }

        try {
            InputStream inputStream = MetadataLoader.class.getResourceAsStream(METADATA_FILE);
            if (inputStream == null) {
                metadataSdkMap = Map.of();
                return;
            }

            ObjectMapper objectMapper = new ObjectMapper();
            metadataSdkMap = objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (Exception e) {
            logger.error("Failed to load SDK metadata", e);
            metadataSdkMap = Collections.emptyMap();
        }
    }

    /**
     * 获取SDK元数据
     *
     * @param candidate SDK的candidate名称
     * @return SDK元数据,如果不存在则返回null
     */
    public static SdkMetadata getMetadata(String candidate) {
        if (metadataSdkMap == null) {
            loadMetadata();
        }
        return metadataSdkMap.get(candidate);
    }

}
