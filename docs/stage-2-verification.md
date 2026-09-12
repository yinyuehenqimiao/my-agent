# 阶段 2 验收记录：统一 Tool Gateway 与 ERP 适配层

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 2。
目标：让 ReAct、Flowable、MCP 和后台测试调用使用同一个安全工具入口。

## 一、本阶段完成项

- 定义统一 `ToolGateway` / `ToolInvocation` / `ToolResult` / `ToolDefinition` / `ToolHandler` 契约（framework）。
- `ToolDefinition` 含输入输出 Schema、读写属性、风险等级、超时、重试、幂等、确认要求、敏感字段、允许来源、所需权限码。
- 实现 `ToolGatewayImpl` 安全管道：执行身份获取 → 定义加载 → 启用检查 → 来源允许 → 权限判断 → 输入校验 → 写工具幂等键检查 → 执行（超时+重试） → 脱敏 → 审计。模型传入的 userId/tenantId/角色一律不可信。
- 定义 `EnterpriseSystemPort`（getCurrentEmployee / getLeaveBalance / createLeaveRequest）及 DTO（framework）。
- 实现 `LocalEnterpriseSystemAdapter`（包装现有 EnterpriseService，复用 requestKey 幂等机制）；定义 `HttpEnterpriseSystemAdapter` 契约（占位，未接入真实 ERP）。
- 重构 `EnterpriseTools`：仅做 AgentScope 协议转换，业务执行统一经 Tool Gateway；工具身份取自 RuntimeContext（服务端签发），模型传入的 userId 被忽略。
- ERP 写接口强制携带幂等键与 traceId；日志禁止打印完整敏感参数（脱敏审计）。

## 二、实际修改模块

- `framework`：新增 `tool` 包（ToolGateway/ToolInvocation/ToolResult/ToolDefinition/ToolHandler）、`enterprise` 包（EnterpriseSystemPort/EmployeeProfile/LeaveBalance/LeaveRequestCommand/LeaveRequestResult）。
- `enterprise`：新增 `adapter` 包（LocalEnterpriseSystemAdapter、HttpEnterpriseSystemAdapter）。
- `agent`：新增 `tool/gateway/ToolGatewayImpl`；重构 `EnterpriseTools` 走网关；更新 `EnterpriseToolsTest`；新增 `ToolGatewayImplTest`。

## 三、数据库变化

无（阶段 2 不改库）。

## 四、新增或调整的接口

- 内部 SPI：`ToolGateway`（agent 实现）、`EnterpriseSystemPort`（enterprise 实现）、`ToolHandler`。
- 对外 HTTP 接口不变；企业工具经网关执行，行为与错误码兼容（VALIDATION_OR_PERMISSION / INTERNAL_ERROR / AWAITING_CONFIRMATION）。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl agent -am test -B` | BUILD SUCCESS；`ToolGatewayImplTest` 13 项、`EnterpriseToolsTest` 6 项全过 |
| `mvn test -B`（全项目） | BUILD SUCCESS；8 模块全过；Tests run 139 项（原 126 + 新增 13），Failures 0，Errors 0 |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 11.56s |

阶段 2 专项测试覆盖：
1. 未注册工具 → TOOL_NOT_FOUND；停用工具 → TOOL_DISABLED。
2. 缺失身份 → UNAUTHENTICATED（fail-closed）。
3. 来源不被允许 → SOURCE_NOT_ALLOWED。
4. 权限拒绝 → FORBIDDEN；权限通过 → 执行。
5. 缺参 → VALIDATION_ERROR；错类型 → VALIDATION_ERROR。
6. 写工具缺幂等键 → MISSING_IDEMPOTENCY_KEY；携带则成功。
7. 敏感字段脱敏（reason → ***，other 保留）。
8. 模型伪造身份（input userId=admin）被忽略，handler 用服务端身份。
9. `isAvailable` 反映注册与启用状态。
原有企业工具安全测试（防伪造身份、缺参不创建、不暴露异常文本）经网关验证全过。

## 六、已知限制

- MCP 工具当前仍经 `McpToolBridge` 直接执行；阶段 5 将把 MCP 工具也纳入 Tool Gateway 的授权与审计链（本阶段已预留 `Source.MCP` 与管道能力）。
- `HttpEnterpriseSystemAdapter` 为契约占位，未接入真实 ERP；阶段 7 实现。
- 工具调用的限流未实现（管道已预留位置），留待阶段 8 可观测性补齐。

## 七、Git 提交

- `feat(tool): add policy-aware tool gateway`
- `refactor(agent): route enterprise and mcp tools through gateway`
- `feat(enterprise): add standard enterprise system port`
- `test(tool): cover authorization schema masking and idempotency`
- `docs(stage-2): record tool gateway verification`

## 八、下一阶段准入结论

Agent 与企业工具无法绕开 Tool Gateway（`EnterpriseTools` 仅协议转换），写工具具备幂等键与确认约束，ERP 适配层契约稳定（Local 实现 + HTTP 契约占位），强制回归（mvn test / 前端 build / tsc）全部通过。可进入阶段 3（嵌入式 Flowable 工作流运行时）。
