package com.myagent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一大模型请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @Default
    private List<ChatMessage> messages = new ArrayList<>();

    private Double temperature;
    private Double topP;
    private Integer maxTokens;
}
