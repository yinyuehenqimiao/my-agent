package com.myagent.framework.tool;

import java.util.List;

/**
 * Tool Gateway：ReAct、Flowable、MCP 与后台测试调用的统一安全工具入口。
 * <p>
 * 固定管道：执行身份获取 → 定义加载 → 启用检查 → 来源允许 → 权限判断 →
 * 输入校验 → 受保护字段注入 → 限流执行 → 输出校验 → 脱敏 → 审计。
 * 模型传入的 userId/tenantId/角色一律不可信。
 */
public interface ToolGateway {

    /**
     * 执行工具调用，按统一安全管道处理。
     *
     * @param invocation 工具调用请求（身份以服务端签发为准）
     * @return 脱敏后的工具结果
     */
    ToolResult invoke(ToolInvocation invocation);

    /** 注册工具定义与处理器 */
    void register(ToolDefinition definition, ToolHandler handler);

    /** 工具是否已注册且启用 */
    boolean isAvailable(String toolName);

    /**
     * 返回按名称排序的工具定义只读快照，供授权后的管理后台展示。
     * 该方法不暴露处理器，也不提供绕过 invoke 安全管道的执行入口。
     */
    List<ToolDefinition> listDefinitions();
}
