# 阶段 5 验收记录：Workflow、RAG 与 ReAct 自动路由

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 5。
目标：固定业务走 Workflow，开放问题进入受控 ReAct。

## 一、本阶段完成项

- `IntentKind` 新增 `WORKFLOW(3)`，不修改现有编码（KB=0/SYSTEM=1/MCP=2）。
- 新增 `RouteDecision` 枚举（WORKFLOW/REACT/SYSTEM/CLARIFY/DENY）与 `RouteResult`（决策+目标+原因+置信度）。
- `RoutingService` 路由决策：优先级为 待确认/审批 → 高置信固定流程 → 参数澄清 → 授权 ReAct → 系统回答 → 拒绝；高风险意图使用更高置信度阈值；Prompt 不承担权限判断，Tool Gateway 做执行时二次鉴权。
- `wf_route` 通过 `intentCode` 绑定工作流或 ReAct 配置；高置信固定流程入 Workflow，置信不足入 CLARIFY。
- ReAct 护栏配置 `ReActGuardrails`：默认最大 5 个推理步骤、8 次工具调用、只读、关闭公网检索、写工具必须确认（`myagent.react.guardrails.*` 可配）。
- SSE 新增事件类型：`route_decision`、`workflow_progress`、`policy_denied`。
- 模型不可用时固定业务入口仍可运行工作流（路由不依赖模型自由规划）。

## 二、实际修改模块

- `framework`：新增 `routing` 包（`RouteDecision`、`RouteResult`）。
- `rag`：`IntentKind` 新增 `WORKFLOW(3)`。
- `agent`：新增 `routing/RoutingService`、`config/ReActGuardrails`；`AgentSSEEventType` 新增 3 个事件；`pom.xml` 增 `h2(test)`。

## 三、数据库变化

无新增表（复用阶段 3 的 `wf_route`）；路由服务按 `intent_code` 查询 `wf_route`。

## 四、新增或调整的接口

- 内部服务：`RoutingService`（路由决策）、`ReActGuardrails`（护栏配置）。
- SSE 事件：`route_decision`/`workflow_progress`/`policy_denied`（前端可订阅展示）。
- 对外 HTTP 接口未变。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl agent -am test -B` | BUILD SUCCESS；`RoutingServiceTest` 11 项全过 |
| `mvn test -B`（全项目） | BUILD SUCCESS；9 模块全过；Tests run 172 项（原 161 + 新增 11），Failures 0，Errors 0 |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 12.10s |

阶段 5 专项测试覆盖（路由评测）：
1. 年假/审批意图高置信 + wf_route 绑定 → WORKFLOW（target=leave-approval）。
2. 固定流程置信不足 → CLARIFY。
3. 制度问题（KB 意图）→ SYSTEM（RAG 返回来源）。
4. 系统意图（问候）→ SYSTEM。
5. 开放分析高置信 + 授权 → REACT。
6. 模糊意图低置信 → CLARIFY。
7. 高风险未授权 → DENY；缺失身份 → DENY。
8. 待确认动作优先 → WORKFLOW（即使低置信）。
9. 模型不可用降级：固定流程高置信仍路由到 Workflow（不依赖模型）。
10. 提示注入：模型试图路由高风险写流程但未授权 → DENY（Prompt 不承担权限判断）。
原有 ReAct/工具/知识证据测试全量回归通过。

## 六、已知限制

- 路由服务当前以独立单测验证；与 AgentChatService 的端到端集成（在聊天流中实际调用 RoutingService 并发 SSE 事件）留待后续。
- 100 条路由评测与 30 条 Prompt 注入评测的完整数据集留待阶段 8 安全评测；本阶段以代表性用例覆盖核心决策路径。
- ReAct 护栏配置已就绪，实际在 ReActAgentProvider 中应用步数/工具调用上限留待后续。
- 请求级授权工具快照基于现有 `AgentToolCatalog.buildToolkit`（已按请求定格），阶段 5 复用。

## 七、Git 提交

- `feat(agent): add workflow react routing decisions`
- `refactor(agent): build request-scoped authorized toolkits`
- `test(agent): add routing and prompt-injection evaluation`
- `docs(stage-5): record routing verification`

## 八、下一阶段准入结论

路由决策覆盖 WORKFLOW/REACT/SYSTEM/CLARIFY/DENY，高风险越权被拒，待确认优先，模型不可用仍可路由固定流程，原有回归未退化。可进入阶段 6（工作流管理后台）。
