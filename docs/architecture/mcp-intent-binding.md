# MCP 与意图树绑定（M3）

`mcp_intent_binding` 是租户级映射表，不修改旧 `t_intent_node.mcp_tool_id`：旧字段只能表达全局单工具，而连接器工具必须区分租户与来源连接器。

绑定规则：

1. 仅 `mcp_catalog_tool.review_status = APPROVED` 且 `enabled = true` 的工具可绑定。
2. 新绑定的状态为 `DRAFT`，编辑不影响正在运行的 Agent。
3. 管理员显式调用发布接口后才标记 `PUBLISHED`。
4. 下一阶段的运行时装配器只读取 `PUBLISHED` 绑定，并仍经过 Tool Gateway 的权限、确认卡和审计。

接口：`/api/rag/admin/mcp-intent-bindings`。
