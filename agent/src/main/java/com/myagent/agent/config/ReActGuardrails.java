package com.myagent.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ReAct 护栏配置：受控开放式 Agent 的运行边界。
 * <p>
 * - 默认最大 5 个推理步骤、8 次工具调用。
 * - 默认只读、关闭公网检索；写工具必须确认。
 * - 请求级授权工具快照在 AgentToolCatalog.buildToolkit 时定格（已有机制）。
 * - 模型不可用时固定业务入口仍可运行工作流（路由不依赖模型自由规划）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "myagent.react.guardrails")
public class ReActGuardrails {

    /** 最大推理步骤 */
    private int maxReasoningSteps = 5;

    /** 最大工具调用次数 */
    private int maxToolCalls = 8;

    /** 是否只读（默认 true，禁止写工具直接执行，需确认） */
    private boolean readOnly = true;

    /** 是否关闭公网检索（默认 true） */
    private boolean disablePublicRetrieval = true;

    /** 写工具是否必须确认（默认 true） */
    private boolean writeRequiresConfirmation = true;
}
