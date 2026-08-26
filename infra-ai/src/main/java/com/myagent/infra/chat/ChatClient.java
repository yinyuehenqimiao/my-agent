package com.myagent.infra.chat;

import com.myagent.framework.convention.ChatRequest;

/**
 * 聊天客户端
 */
public interface ChatClient {

    String provider();

    String chat(ChatRequest request, String model);

    void streamChat(ChatRequest request, String model, StreamCallback callback);
}
