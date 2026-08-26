package com.myagent.infra.chat;

import com.myagent.framework.convention.ChatRequest;
import com.myagent.infra.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 默认 LLMService：走配置中的 chat.provider / chat.model
 */
@Service
@RequiredArgsConstructor
public class DefaultLLMService implements LLMService {

    private final ChatClient chatClient;
    private final AiProperties aiProperties;

    @Override
    public String chat(ChatRequest request) {
        return chatClient.chat(request, aiProperties.getChat().getModel());
    }

    @Override
    public void streamChat(ChatRequest request, StreamCallback callback) {
        chatClient.streamChat(request, aiProperties.getChat().getModel(), callback);
    }
}
