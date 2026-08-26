package com.myagent.infra.chat;

import com.myagent.framework.convention.ChatRequest;
import com.myagent.infra.enums.Tier;

/**
 * 业务侧 LLM 门面：屏蔽供应商差异
 */
public interface LLMService {

    String chat(ChatRequest request);

    default String chat(ChatRequest request, Tier tier) {
        return chat(request);
    }

    default String chat(ChatRequest request, Tier tier, String preferredModelId) {
        return chat(request);
    }

    void streamChat(ChatRequest request, StreamCallback callback);
}