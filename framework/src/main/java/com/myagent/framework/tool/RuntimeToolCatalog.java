package com.myagent.framework.tool;

import com.myagent.framework.context.ExecutionIdentity;

import java.util.List;
import java.util.Optional;

/**
 * 租户运行时工具目录。
 *
 * <p>与进程启动时注册的内部工具不同，ERP/MCP 工具由数据库中的审核、启用和绑定状态
 * 动态决定。实现必须以服务端身份解析工具，并在每次调用时重新校验当前状态。</p>
 */
public interface RuntimeToolCatalog {

    /** 按可信身份和调用来源解析一个当前可调用的工具。 */
    Optional<ResolvedTool> resolve(ExecutionIdentity identity, String toolName, ToolDefinition.Source source);

    /** 返回当前租户在指定来源下可见的工具定义。 */
    List<ToolDefinition> list(ExecutionIdentity identity, ToolDefinition.Source source);

    /** 返回租户目录版本，用于让 Agent 工具快照自动失效。 */
    long revision(ExecutionIdentity identity);

    record ResolvedTool(ToolDefinition definition, ToolHandler handler) { }
}
