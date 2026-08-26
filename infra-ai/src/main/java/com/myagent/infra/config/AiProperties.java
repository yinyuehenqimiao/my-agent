package com.myagent.infra.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 模型配置（对齐 ragent 的 ai.providers / candidates 风格）
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Map<String, ProviderConfig> providers = new HashMap<>();
    private ChatConfig chat = new ChatConfig();
    private EmbeddingConfig embedding = new EmbeddingConfig();

    @Data
    public static class ProviderConfig {
        private String url;
        private String apiKey;
        private Endpoints endpoints = new Endpoints();
    }

    @Data
    public static class Endpoints {
        private String chat = "/v1/chat/completions";
        private String embedding = "/v1/embeddings";
    }

    @Data
    public static class ChatConfig {
        /** 引用 providers 下的 key，如 siliconflow */
        private String provider = "siliconflow";
        private String model = "deepseek-ai/DeepSeek-V3";
        private Double temperature = 0.3;
        private Integer maxTokens = 1024;
    }

    @Data
    public static class EmbeddingConfig {
        private String provider = "siliconflow";
        private String model = "BAAI/bge-m3";
        /** 知识库创建时下拉默认选中的候选 id */
        private String defaultModel;
        private Integer dimension = 1024;
        private List<ModelCandidate> candidates = new ArrayList<>();
    }

    @Data
    public static class ModelCandidate {
        private String id;
        private String provider;
        private String model;
        private String url;
        private Integer dimension;
        private Integer priority;
        private Boolean enabled = true;
    }
}
