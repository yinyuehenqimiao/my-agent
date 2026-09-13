# 阶段 E2：工具调用持久化审计与安全降级

## 阶段目标

将 Tool Gateway 从“只输出日志”升级为可按 `traceId` 查询的数据库审计，并确保写操作在审计不可用时不会失效开放。

## 实现内容

- 新增 `ToolExecutionAuditService`，写入 `operation_audit`，记录操作者、租户、工具、来源、阶段、结果、耗时和 `traceId`。
- 审计内容不包含工具输入或输出，避免请假原因、手机号等敏感数据进入审计详情。
- Tool Gateway 生成或沿用同一个 `traceId`，并把它回写到调用对象，业务处理器、返回结果、日志和数据库审计使用同一追踪号。
- 所有拒绝决策尽力写入 `DECISION` 审计。
- 写工具及高风险工具在调用处理器前强制写入 `BEFORE_EXECUTION/READY`；失败时返回 `AUDIT_UNAVAILABLE`，业务处理器不会执行。
- 工具完成后写入 `COMPLETED` 审计。写工具完成审计失败时返回 `AUDIT_INCOMPLETE`，提示不要重复提交、联系管理员核验。
- 只读工具审计失败时允许降级返回查询结果，但产生结构化错误 `tool_audit_write_failure` 并递增失败计数。

## 测试流程

### 红灯

新增持久化审计和故障降级测试后，编译因 `ToolExecutionAuditService` 不存在失败，证明原网关没有数据库工具审计能力。

### 定向验证

```powershell
mvn -pl agent -am "-Dtest=ToolExecutionAuditServiceTest,ToolGatewayImplTest,EnterpriseToolsTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：26 项通过，覆盖真实落库、敏感输入不落库、失败计数、写操作执行前拒绝、只读降级和完成审计失败后的不确定结果。

全量 `mvn test` 九模块 `BUILD SUCCESS`，agent 模块 119 项测试通过。

### 安全语义

| 场景 | 业务是否执行 | 返回策略 |
| --- | --- | --- |
| 写/高风险工具执行前审计失败 | 否 | `AUDIT_UNAVAILABLE` |
| 写/高风险工具已执行、完成审计失败 | 已执行一次 | `AUDIT_INCOMPLETE`，禁止盲目重试 |
| 只读工具审计失败 | 可以执行 | 返回查询结果，同时记录故障计数 |

## 阶段边界

统一审计查询页面和多类审计合并属于 E3；ERP 调用审计将在 HTTP ERP 接入阶段复用同一 `traceId` 语义。
