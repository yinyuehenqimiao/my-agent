package com.myagent.infra.embedding;

import com.myagent.framework.exception.ServiceException;
import com.myagent.infra.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于候选模型配置的 EmbeddingService：按知识库保存的 modelId 路由到真实 provider/model
 */
@Service
@RequiredArgsConstructor
public class DefaultEmbeddingService implements EmbeddingService {

    private final EmbeddingClient embeddingClient;
    private final AiProperties aiProperties;

    @Override
    public List<Float> embed(String text) {
        return embed(text, resolveDefaultModelId());
    }

    @Override
    public List<Float> embed(String text, String modelId) {
        return embedBatch(List.of(text), modelId).get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        return embedBatch(texts, resolveDefaultModelId());
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, String modelId) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (!StringUtils.hasText(modelId)) {
            throw new ServiceException("Embedding 模型ID不能为空");
        }
        ResolvedModel resolved = resolveModel(modelId);
        List<float[]> raw = embeddingClient.embed(texts, resolved.provider(), resolved.model());
        if (raw == null || raw.size() != texts.size()) {
            throw new ServiceException("Embedding 返回条数与输入不一致");
        }
        List<List<Float>> result = new ArrayList<>(raw.size());
        for (float[] vector : raw) {
            List<Float> row = new ArrayList<>(vector.length);
            for (float v : vector) {
                row.add(v);
            }
            result.add(row);
        }
        return result;
    }

    private String resolveDefaultModelId() {
        AiProperties.EmbeddingConfig embedding = aiProperties.getEmbedding();
        if (StringUtils.hasText(embedding.getDefaultModel())) {
            return embedding.getDefaultModel();
        }
        if (embedding.getCandidates() != null && !embedding.getCandidates().isEmpty()
                && StringUtils.hasText(embedding.getCandidates().get(0).getId())) {
            return embedding.getCandidates().get(0).getId();
        }
        return embedding.getModel();
    }

    private ResolvedModel resolveModel(String modelId) {
        AiProperties.EmbeddingConfig embedding = aiProperties.getEmbedding();
        if (embedding.getCandidates() != null) {
            for (AiProperties.ModelCandidate candidate : embedding.getCandidates()) {
                if (modelId.equals(candidate.getId())
                        || modelId.equals(candidate.getModel())) {
                    String provider = StringUtils.hasText(candidate.getProvider())
                            ? candidate.getProvider()
                            : embedding.getProvider();
                    String model = StringUtils.hasText(candidate.getModel())
                            ? candidate.getModel()
                            : embedding.getModel();
                    return new ResolvedModel(provider, model);
                }
            }
        }
        // 兼容：直接传了真实模型名，或旧知识库存的是全局 model
        if (modelId.equals(embedding.getModel()) || modelId.equals(embedding.getDefaultModel())) {
            return new ResolvedModel(embedding.getProvider(), embedding.getModel());
        }
        // 找不到候选时仍用全局默认，避免历史数据阻断；日志级提示由上层维度校验兜底
        return new ResolvedModel(embedding.getProvider(), embedding.getModel());
    }

    private record ResolvedModel(String provider, String model) {
    }
}
