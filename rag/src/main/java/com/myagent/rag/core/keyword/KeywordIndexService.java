package com.myagent.rag.core.keyword;

import java.util.List;

/**
 * 关键词索引 SPI（当前无 ES 实现；删库清理时 ObjectProvider 可能为空）
 */
public interface KeywordIndexService {

    void deleteByCollection(String collectionName);

    default void deleteDocumentIndex(String collectionName, String docId) {
    }

    default void deleteChunksByIds(String collectionName, List<String> chunkIds) {
    }
}
