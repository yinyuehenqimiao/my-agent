# MCP 租户目录发现（M2a）

本阶段只解决“发现什么”，不解决“谁可以使用”。远程 MCP 的工具元数据先进入租户级快照，之后必须经管理后台审核、启用并绑定意图节点，才会进入 Agent 可见工具集合。

```text
连接器（tenantId + connectorId + endpoint + secretRef）
  -> 管理端解析密钥引用，传入短期 discovery token
  -> tools/list
  -> McpCatalogDiscoveryService 成功快照
  -> [下一阶段] 持久化、审核、发布、意图绑定
  -> [后续] Tool Gateway 运行时二次鉴权与确认卡
```

约束：

- 缓存键固定为 `tenantId + connectorId`，不同租户不会覆盖彼此目录。
- 连接器定义仅保存 `secretRef`，目录对象不携带真实令牌。
- 发现异常不清空上一次成功快照，避免短暂网络错误让已审核能力消失。
- 该快照不能直接注册进 `McpToolRegistry`；现有启动期静态 MCP 注册会在后续发布管道替换。

测试：`McpCatalogDiscoveryServiceTest` 验证发现失败保留快照与租户隔离。
