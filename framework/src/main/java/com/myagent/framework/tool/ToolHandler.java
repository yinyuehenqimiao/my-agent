package com.myagent.framework.tool;

/**
 * 工具处理器 SPI：每个工具提供具体业务逻辑实现。
 * <p>
 * 实现只接收 {@link ToolInvocation}（含服务端签发身份），返回 {@link ToolResult}。
 * 不直接读取模型输入的身份字段；输入校验、权限判断、脱敏、审计由 Tool Gateway 统一处理。
 */
@FunctionalInterface
public interface ToolHandler {

    ToolResult handle(ToolInvocation invocation) throws Exception;
}
