package com.myagent.framework.tool;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具定义：声明工具的元数据与安全约束。
 * <p>
 * 包含输入输出 Schema、读写属性、风险等级、超时、重试、幂等要求、确认要求、
 * 敏感字段、允许来源与所需权限码。Tool Gateway 据此执行统一安全管道。
 */
@Data
@Builder
public class ToolDefinition {

    /** 工具名（唯一） */
    private String name;
    /** 工具描述（供模型理解） */
    private String description;
    /** 输入 JSON Schema */
    private Map<String, Object> inputSchema;
    /** 输出 JSON Schema（可选） */
    private Map<String, Object> outputSchema;

    /** 是否只读（true=不改变业务状态） */
    private boolean readOnly;
    /** 风险等级：LOW / MEDIUM / HIGH */
    private RiskLevel riskLevel;
    /** 执行超时（毫秒） */
    private long timeoutMs;
    /** 最大重试次数（失败时） */
    private int maxRetries;
    /** 写操作是否幂等（需携带幂等键） */
    private boolean idempotent;
    /** 是否需要用户确认（写工具默认 true） */
    private boolean requiresConfirmation;
    /** 敏感字段（日志/审计脱敏） */
    private List<String> sensitiveFields;
    /** 允许的调用来源：REACT / WORKFLOW / MCP / ADMIN */
    private List<Source> allowedSources;
    /** 调用所需权限码（可为空，表示仅需已认证身份） */
    private String permissionCode;
    /** 是否启用 */
    private boolean enabled;

    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum Source { REACT, WORKFLOW, MCP, ADMIN }
}
