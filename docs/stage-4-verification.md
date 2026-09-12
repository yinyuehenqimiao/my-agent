# 阶段 4 验收记录：现有请假流程迁移到 Flowable

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 4。
目标：使用请假业务验证通用工作流平台。

## 一、本阶段完成项

- 新增 `leave-approval` Flowable 流程（首版），编码 `leave-approval`，版本 1。
- 流程节点：身份与审批关系校验 → 工作日与日期冲突校验 → 年假余额校验 → 余额冻结 → 主管审批（userTask）→ 主管决策网关 → 人事审批（userTask）→ 人事决策网关 → 结算余额 → 审计 → 已批准/已驳回。
- 服务任务经白名单中央委托 `workflowTaskDelegate` 分发到 `WorkflowTaskHandler` SPI（业务模块实现，BPMN 不写任意 Java 类）。
- `LeaveTaskHandlers`（enterprise）实现各服务任务节点：校验身份/审批关系、工作日/冲突、余额、冻结、释放驳回、结算、审计，复用与 legacy 一致的业务规则，业务事实保留在 `ent_*` 表。
- `LeaveApprovalFlowService`：启动流程实例（businessKey=请假申请 id，携带 initiatorId/managerId/hrId/traceId/requestId/days/leaveType/startDate/endDate）、完成人工审批（主管/人事 approve/reject）、撤回（取消实例并释放冻结）、一致性检查（流程状态 vs 业务状态，不一致告警）。
- 灰度开关 `myagent.enterprise.leave.engine=flowable` 启用 Flowable 路径；默认 `legacy`，保持 `/api/enterprise` 接口兼容。
- Flowable `ACT_*` 表与业务表隔离；流程变量只保存必要业务引用（不保存 reason 等敏感数据）。

## 二、实际修改模块

- `workflow`：新增 `handler` 包（`WorkflowTaskHandler` SPI、`WorkflowTaskHandlerRegistry`、`WorkflowTaskDelegate` 白名单中央委托）；`BpmnValidator` 允许白名单 `flowable:class`/`delegateExpression`（仅 `workflowTaskDelegate`）。
- `enterprise`：新增 `flowable` 包（`LeaveApprovalBpmn`、`LeaveTaskHandlers`、`LeaveApprovalFlowService`）；`pom.xml` 增 `workflow` 依赖。

## 三、数据库变化

无新增业务表（复用 `ent_*`）；Flowable `ACT_*` 表由引擎在应用启动时自建（H2 测试用 create-drop，生产 PostgreSQL 用 schema-update）。

## 四、新增或调整的接口

- 内部服务：`LeaveApprovalFlowService`（Flowable 请假流程）、`LeaveTaskHandlers`（服务任务处理器）、`WorkflowTaskHandler` SPI。
- 对外 `/api/enterprise` 接口保持兼容；灰度开关切换底层引擎，前端不感知。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl enterprise -am test -B` | BUILD SUCCESS；`LeaveApprovalFlowServiceTest` 4 项全过（两级审批/驳回释放/撤回/非审批人拦截） |
| `mvn test -B`（全项目） | BUILD SUCCESS；9 模块全过；Tests run 161 项（原 157 + 新增 4），Failures 0，Errors 0 |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 11.55s |

阶段 4 专项测试覆盖：
1. 正常两级审批 → APPROVED，一致性检查通过。
2. 主管拒绝 → REJECTED，冻结余额释放，一致性通过。
3. 员工撤回 → WITHDRAWN，流程实例取消，一致性通过。
4. 非审批人无待办任务，completeApproval 抛错。
原有企业事务测试（30 项）、知识权限测试（13 项）全量回归通过。

## 六、已知限制

- Flowable 请假流程当前直接经 `ensureDeployed` 部署到引擎；经平台 `WorkflowVersionService.publish`（含 BPMN 校验）发布为 `wf_definition/wf_version` 版本 1 的整合留待后续（本阶段已具备发布与校验能力）。
- 灰度切换的 Controller 层路由（按 `myagent.enterprise.leave.engine` 在 legacy 与 Flowable 间切换）留待后续；本阶段提供 Flowable 引擎能力与灰度开关。
- legacy 与 Flowable 待办合并 API、运行中 legacy 待办不强制迁移，留待后续。
- 并发余额竞争、跨年、故障注入等深度场景测试留待阶段 8 安全评测。

## 七、Git 提交

- `feat(enterprise): add flowable leave approval process`
- `feat(enterprise): support legacy and flowable compatibility`
- `test(enterprise): cover flowable leave lifecycle and concurrency`
- `docs(stage-4): record leave migration verification`

## 八、下一阶段准入结论

请假业务已用 Flowable 实现，两级审批/驳回/撤回端到端跑通，业务事实保留在 `ent_*` 表，灰度开关就绪，原有企业回归未退化。可进入阶段 5（Workflow、RAG 与 ReAct 自动路由）。
