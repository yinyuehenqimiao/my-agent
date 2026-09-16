# MCP 连接器管理与审核（M2b）

管理员接口位于 `/api/rag/admin/mcp-connectors`，权限码为 `knowledge:authorize`。租户始终从服务端 `UserContext` 的 `ExecutionIdentity` 获取，客户端不能指定租户。

管理顺序：

1. `PUT /api/rag/admin/mcp-connectors` 保存连接器地址与 `secretRef`；数据库不保存 token。
2. `POST /{connectorId}/discover` 从环境或后续 Vault 解析 `secretRef`，调用 MCP `tools/list`。
3. 发现结果写入 `mcp_catalog_tool`，初始状态强制为 `PENDING_REVIEW` 和 `enabled=false`。
4. `PUT /{connectorId}/tools/{toolId}/review` 仅允许标记 `APPROVED` 或 `DISABLED`；只有 `APPROVED + enabled=true` 才能进入后续意图绑定和运行时发布。

部署前执行 [01_mcp_catalog.sql](../../resources/database/upgrades/v0.6.0/01_mcp_catalog.sql)。本地环境令牌变量命名为：

```text
MCP_DISCOVERY_TOKEN_<SECRET_REF_规范化为大写下划线>
```

例如 `vault:erp-a` 对应 `MCP_DISCOVERY_TOKEN_VAULT_ERP_A`。生产环境应以 Vault 实现替换 `EnvironmentMcpDiscoveryTokenProvider`。
