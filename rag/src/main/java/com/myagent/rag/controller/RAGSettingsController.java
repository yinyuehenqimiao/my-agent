/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.myagent.rag.controller;

import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.infra.config.AiProperties;
import com.myagent.rag.controller.vo.SystemSettingsVO;
import com.myagent.rag.controller.vo.SystemSettingsVO.AISettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 设置只读接口：供前端选择 Embedding 模型、读取上传上限
 */
@RestController
@RequiredArgsConstructor
public class RAGSettingsController {

    private final AiProperties aiProperties;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private DataSize maxRequestSize;

    @GetMapping("/rag/settings")
    public Result<SystemSettingsVO> settings() {
        return Results.success(SystemSettingsVO.builder()
                .ai(toAISettings())
                .upload(SystemSettingsVO.UploadSettings.builder()
                        .maxFileSize(maxFileSize.toBytes())
                        .maxRequestSize(maxRequestSize.toBytes())
                        .build())
                .build());
    }

    private AISettings toAISettings() {
        Map<String, AISettings.ProviderConfig> providers = new HashMap<>();
        if (aiProperties.getProviders() != null) {
            aiProperties.getProviders().forEach((key, value) -> {
                Map<String, String> endpoints = new HashMap<>();
                if (value.getEndpoints() != null) {
                    endpoints.put("chat", value.getEndpoints().getChat());
                    endpoints.put("embedding", value.getEndpoints().getEmbedding());
                }
                providers.put(key, AISettings.ProviderConfig.builder()
                        .url(value.getUrl())
                        .apiKey(maskApiKey(value.getApiKey()))
                        .endpoints(endpoints)
                        .build());
            });
        }

        AiProperties.EmbeddingConfig embedding = aiProperties.getEmbedding();
        List<AISettings.ModelCandidate> candidates = new ArrayList<>();
        if (embedding.getCandidates() != null) {
            for (AiProperties.ModelCandidate candidate : embedding.getCandidates()) {
                candidates.add(AISettings.ModelCandidate.builder()
                        .id(candidate.getId())
                        .provider(candidate.getProvider())
                        .model(candidate.getModel())
                        .url(candidate.getUrl())
                        .dimension(candidate.getDimension())
                        .priority(candidate.getPriority())
                        .enabled(candidate.getEnabled() == null || candidate.getEnabled())
                        .build());
            }
        }
        // 兼容旧配置：没有 candidates 时，用当前默认 embedding 生成一项
        if (candidates.isEmpty() && StringUtils.hasText(embedding.getModel())) {
            String id = StringUtils.hasText(embedding.getDefaultModel())
                    ? embedding.getDefaultModel()
                    : embedding.getModel();
            candidates.add(AISettings.ModelCandidate.builder()
                    .id(id)
                    .provider(embedding.getProvider())
                    .model(embedding.getModel())
                    .dimension(embedding.getDimension())
                    .priority(1)
                    .enabled(true)
                    .build());
        }

        String defaultModel = StringUtils.hasText(embedding.getDefaultModel())
                ? embedding.getDefaultModel()
                : (candidates.isEmpty() ? embedding.getModel() : candidates.get(0).getId());

        return AISettings.builder()
                .providers(providers)
                .embedding(AISettings.ModelGroup.builder()
                        .defaultModel(defaultModel)
                        .candidates(candidates)
                        .build())
                .build();
    }

    private String maskApiKey(String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return null;
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
