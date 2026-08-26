package com.myagent.infra.embedding;

import java.util.List;

/**
 * Embedding 客户端
 */
public interface EmbeddingClient {

    String provider();

    /**
     * 使用默认 embedding 模型向量化
     */
    List<float[]> embed(List<String> texts);

    /**
     * 指定供应商 + 模型名向量化（知识库候选模型路由）
     */
    List<float[]> embed(List<String> texts, String provider, String model);
}
