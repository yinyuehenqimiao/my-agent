package com.myagent.framework.convention;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    public enum Role {
        SYSTEM, USER, ASSISTANT;

        public String apiValue() {
            return name().toLowerCase();
        }
    }

    /**
     * 消息结束状态
     */
    public enum MessageStatus {
        NORMAL,
        INTERRUPTED,
        REJECTED
    }

    private Role role;
    private String content;

    public static ChatMessage system(String content) {
        return new ChatMessage(Role.SYSTEM, content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
