package com.myagent.infra.embedding;

import java.util.List;

/**
 * 向量化服务接口：屏蔽具体 Embedding 供应商差异
 */
public interface EmbeddingService {

    List<Float> embed(String text);

    List<Float> embed(String text, String modelId);

    List<List<Float>> embedBatch(List<String> texts);

    List<List<Float>> embedBatch(List<String> texts, String modelId);

    default int dimension() {
        return 0;
    }
}
