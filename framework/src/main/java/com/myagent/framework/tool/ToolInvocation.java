package com.myagent.framework.tool;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.tool.ToolDefinition.Source;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 工具调用请求。模型传入的 userId/tenantId/角色一律不可信，
 * 身份以服务端签发的 {@link ExecutionIdentity} 为准。
 */
@Data
@Builder
public class ToolInvocation {

    /** 工具名 */
    private String toolName;
    /** 模型提供的输入参数（会经 Schema 校验） */
    private Map<String, Object> input;
    /** 服务端签发的执行身份（不可由模型覆写） */
    private ExecutionIdentity identity;
    /** 调用来源 */
    private Source source;
    /** 追踪号 */
    private String traceId;
    /** 幂等键（写工具必填） */
    private String idempotencyKey;
}
