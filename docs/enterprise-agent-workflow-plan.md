# `my-agent` 企业智能体与 Flowable 工作流平台分阶段实施计划

## 一、执行原则

本项目严格串行实施，不并行跨阶段开发：

```text
阶段范围确认
→ 实现本阶段功能
→ 补齐本阶段自动化测试
→ 运行本阶段专项测试
→ 运行全项目回归测试
→ 修复全部阻断问题
→ 更新验收记录
→ 提交本阶段代码
→ 才能进入下一阶段
```

提交规则：

- 不提交未通过相应测试的代码。
- 阶段内允许按“数据库、后端、前端、测试”拆分小提交，但每个小提交前必须通过对应专项测试。
- 阶段结束必须执行全量回归，并增加一笔阶段验收记录提交。
- 当前阶段存在 P0/P1 问题时，不开始下一阶段。
- 每次提交只包含当前阶段相关文件，不夹带其他修改。
- 不执行远程 `push`，除非另外明确要求。
- 不修改、撤销或覆盖不属于当前阶段的用户代码。
- 所有提交采用 Conventional Commits 格式。

阶段完成报告至少包含：

- 本阶段完成项；
- 实际修改模块；
- 数据库变化；
- 新增或调整的接口；
- 测试命令及结果；
- 已知限制；
- Git commit ID；
- 下一阶段准入结论。

## 二、目标架构

保留现有模块：

- `system`：登录、用户和基础角色。
- `framework`：上下文、异常、Web、审计公共能力。
- `infra-ai`：模型、Embedding、重排和模型路由。
- `rag`：意图树、知识库、检索、摄取管线。
- `agent`：AgentScope ReAct、会话、SSE 和工具。
- `enterprise`：员工、年假、审批和知识权限。
- `frontend`：聊天工作台与管理后台。
- `bootstrap`：应用启动入口。

新增 `workflow` Maven 模块，嵌入当前 Spring Boot 应用并接入 Flowable：

```text
用户请求
  → 登录与企业身份解析
  → 意图识别
  → 风险和权限判断
  → 执行路由
      ├─ WORKFLOW：Flowable 固定流程
      ├─ REACT：受控开放式 Agent
      ├─ SYSTEM：系统回答
      ├─ CLARIFY：追问参数
      └─ DENY：拒绝
  → Tool Gateway
  → 本地企业系统 / 未来 ERP / 知识库 / MCP
  → 输出脱敏、引用和审计
```

模块依赖原则：

- `workflow` 定义流程运行时和 `WorkflowTaskHandler` SPI，不依赖具体请假业务。
- `enterprise` 实现请假相关流程节点和 ERP 适配接口。
- `agent` 调用路由服务、Flowable 和 Tool Gateway。
- `rag` 的文档摄取管线不承担业务工作流职责。
- 所有 Agent、Flowable、MCP 调用统一经过 Tool Gateway。
- 当前单公司运行，但新核心表和执行上下文使用 `tenantId=default`。

## 三、分阶段实施、测试与提交

### 阶段 0：整理并提交当前项目基线

目标：先把当前大量未提交的既有改造整理成可信基线，避免后续阶段提交混入旧代码。

实施内容：

- 完整检查当前 Git 修改和未跟踪文件。
- 区分应纳入项目的代码、本机构建文件、临时测试输出、敏感配置和不应提交的编译产物。
- 明确排除 `HashProbe.class` 等编译产物。
- 检查 `.gitignore`，确保密钥、环境变量、评测输出和临时文件不会提交。
- 静态检查现有数据库升级脚本是否具有可重复执行或明确的一次性约束。
- 将当前企业请假能力标记为 `legacy enterprise baseline`。
- 保存基线验收记录。

测试流程：

1. 运行 `mvn test`。
2. 运行 `mvn -pl bootstrap -am package -DskipTests`。
3. 在 `frontend` 运行 `npm run build`。
4. 运行 `npx tsc --noEmit`。
5. 检查现有 Node 测试脚本语法。
6. 基础设施可用时执行 `seed-knowledge.cjs`、`smoke-workflow.cjs` 和 `evaluate-agent.cjs`。
7. 检查提交内容中不存在 API Key、JWT 密钥、密码和本地 `.env`。
8. 对计划提交文件执行最终清单审阅。

失败处理：

- 现有测试失败时先判断是环境问题还是代码问题。
- 代码失败必须在阶段 0 修复并重新全量测试。
- 外部模型或中间件不可用时，记录为环境验收未执行，但 Maven 单测和前端构建必须通过。
- 不为了形成干净状态而删除或重置用户修改。

提交：

```text
chore(baseline): capture current enterprise agent implementation
docs(baseline): record baseline verification results
```

阶段准入条件：

- 当前合法改动已形成基线提交。
- 工作区无本项目遗留修改。
- 敏感文件和编译产物未进入 Git。
- 记录基线 commit ID 后才能进入阶段 1。

### 阶段 1：平台权限与统一执行身份

目标：把当前简单的 `admin/user` 权限升级为平台 RBAC 与企业 ABAC。

实施内容：

- 保留 `t_user.role` 兼容现有逻辑。
- 新增 `sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`。
- 平台角色固定为 `PLATFORM_ADMIN`、`WORKFLOW_EDITOR`、`WORKFLOW_REVIEWER`、`SECURITY_ADMIN`、`AUDITOR`、`NORMAL_USER`。
- `EMPLOYEE/MANAGER/HR` 继续作为企业业务属性，不混入平台角色。
- 实现统一 `ExecutionIdentity`，包含租户、用户、平台角色、部门、企业角色、主管和扩展属性。
- 实现 `PolicyDecisionService`，权限策略采用默认拒绝。
- 后台管理接口由字符串 `admin` 判断迁移为权限码判断。
- 首批权限码包括工作流查看/编辑/提交/审核/发布/回滚、工具查看/编辑/测试、策略查看/编辑、审计查看、员工管理和知识授权。
- 编辑者不得审核自己提交的工作流。
- 系统管理员不自动获得主管或 HR 审批权限。
- 所有权限拒绝写入审计记录。

测试流程：

1. 角色与权限码映射单元测试。
2. 无权限、单角色和多角色测试。
3. 编辑者自审拦截测试。
4. 管理员业务审批越权测试。
5. 员工、主管、HR 数据范围测试。
6. 用户停用和角色撤销后的实时权限测试。
7. SSE、异步线程和 Flowable 预留上下文的身份隔离测试。
8. 执行全项目 `mvn test`。
9. 执行前端构建和类型检查。
10. 执行原有请假与知识授权回归测试。

阶段提交：

```text
feat(auth): add platform roles and permission model
feat(auth): add unified enterprise execution identity
test(auth): cover rbac abac and identity isolation
docs(stage-1): record authorization verification
```

阶段准入条件：所有后台接口都有明确权限码，越权测试全部通过，原有请假和知识库权限没有退化，完成阶段验收提交后进入阶段 2。

### 阶段 2：统一 Tool Gateway 与 ERP 适配层

目标：让 ReAct、Flowable、MCP 和后台测试调用使用同一个安全工具入口。

公共接口：

```java
interface ToolGateway {
    ToolResult invoke(ToolInvocation invocation);
}

interface EnterpriseSystemPort {
    EmployeeProfile getCurrentEmployee(ExecutionIdentity identity);
    LeaveBalance getLeaveBalance(ExecutionIdentity identity, int year);
    LeaveRequestResult createLeaveRequest(
        ExecutionIdentity identity,
        LeaveRequestCommand command
    );
}
```

实施内容：

- 将 `EnterpriseTools` 的业务执行迁入 Tool Gateway。
- AgentScope 工具只负责协议转换，MCP 工具调用纳入同一授权与审计链。
- 工具定义包含输入输出 Schema、读写属性、风险等级、超时、重试、幂等要求、确认要求、敏感字段和允许来源。
- 工具调用固定执行身份获取、定义加载、启用检查、权限判断、输入校验、受保护字段注入、限流执行、输出校验、脱敏和审计。
- 模型传入的 `userId`、`tenantId` 和角色一律不可信。
- 实现 `LocalEnterpriseSystemAdapter`，同时定义未来 `HttpEnterpriseSystemAdapter` 契约。
- ERP 写接口强制携带幂等键和 `traceId`。
- 日志禁止打印完整敏感参数。

测试流程：

1. 输入输出 Schema、缺参、错类型和多余字段测试。
2. 模型伪造其他员工身份测试。
3. 未注册、停用和未授权工具测试。
4. 工具超时、网络异常和重试测试。
5. 写操作幂等测试。
6. 日志和审计脱敏测试。
7. MCP 提示注入和越权测试。
8. 本地 ERP Adapter 契约测试。
9. Mock HTTP ERP 消费者契约测试。
10. 全项目 Maven 和前端回归。

阶段提交：

```text
feat(tool): add policy-aware tool gateway
refactor(agent): route enterprise and mcp tools through gateway
feat(enterprise): add standard enterprise system port
test(tool): cover authorization schema masking and idempotency
docs(stage-2): record tool gateway verification
```

阶段准入条件：Agent 和 Flowable 无法绕开 Tool Gateway，写工具具备确认和幂等约束，ERP 适配层契约稳定，验收提交后进入阶段 3。

### 阶段 3：嵌入式 Flowable 工作流运行时

目标：新增可持久化、可版本化、可恢复的固定业务流程能力。

实施内容：

- 新增 `workflow` Maven 模块并嵌入当前 Spring Boot 应用。
- 使用当前 PostgreSQL，Flowable `ACT_*` 表与业务表隔离。
- 新增 `wf_definition`、`wf_version`、`wf_review`、`wf_route`、`workflow_action` 和 `operation_audit`。
- 版本状态固定为 `DRAFT`、`IN_REVIEW`、`APPROVED`、`PUBLISHED`、`REJECTED`、`RETIRED`。
- 发布版本不可编辑，修改时创建新版本。
- 第一版仅允许开始、参数校验、权限检查、工具任务、知识检索、排他网关、用户确认、人工审批、响应模板和结束节点。
- 禁止任意 Java 类、脚本、动态 SQL 和任意 HTTP 节点。
- 条件表达式使用白名单字段和操作符。
- 发布前检查开始/结束节点、不可达节点、工具、Schema、高风险确认、办理人规则、节点上限和循环。
- 每个实例固定绑定发布版本，流程变量只保存必要业务引用。
- 每个实例携带 `tenantId`、`initiatorId`、`businessKey` 和 `traceId`。

测试流程：

1. 合法和非法 BPMN 校验。
2. 草稿、审核、发布、退休和回滚测试。
3. 发布版本不可编辑测试。
4. 运行中新版本发布隔离测试。
5. 应用重启后流程恢复测试。
6. 人工任务权限测试。
7. 服务任务超时和有限重试测试。
8. 流程重复启动和任务重复完成测试。
9. 敏感流程变量检查。
10. PostgreSQL 真实集成测试及全项目回归。

阶段提交：

```text
feat(workflow): add embedded flowable runtime
feat(workflow): add version review and publish lifecycle
feat(workflow): add safe node validation and runtime audit
test(workflow): cover persistence versioning and authorization
docs(stage-3): record flowable runtime verification
```

阶段准入条件：流程可在重启后恢复，错误定义无法发布，任务不能绕过权限和 Tool Gateway，验收提交后进入阶段 4。

### 阶段 4：现有请假流程迁移到 Flowable

目标：使用请假业务验证通用工作流平台。

实施内容：

- 保留 `ent_leave_request`、`ent_leave_account` 和 `ent_audit` 作为业务事实来源。
- Flowable 负责流程状态和人工任务。
- 新流程编码为 `leave-approval`，首版版本为 `1`。
- 流程依次完成身份、审批关系、工作日、日期冲突、余额、确认卡、余额冻结、主管审批、HR 审批、余额结算和审计。
- 新申请按灰度开关选择 legacy 或 Flowable。
- 已存在的待办继续走 legacy，不强制迁移。
- API 合并两类任务，前端不识别底层引擎。
- 保持现有 `/api/enterprise` 接口兼容。
- 增加流程状态与业务状态一致性检查；不一致时告警并人工处理。

测试流程：

1. 正常两级审批、主管拒绝、HR 拒绝和员工撤回。
2. 年假不足、日期重叠、非工作日和跨年。
3. 工作日历或审批关系变化导致确认卡失效。
4. 自审、越级审批和他人查看测试。
5. 确认过期、重复确认和刷新恢复。
6. 并发申请余额竞争测试。
7. Flowable 与业务事务故障注入。
8. legacy 与 Flowable 待办合并测试。
9. 灰度关闭和回退测试。
10. 原有企业测试全量回归。

阶段提交：

```text
feat(enterprise): add flowable leave approval process
feat(enterprise): support legacy and flowable compatibility
test(enterprise): cover flowable leave lifecycle and concurrency
docs(stage-4): record leave migration verification
```

阶段准入条件：用户接口保持兼容，业务、流程和审计状态一致，可配置切回 legacy，验收提交后进入阶段 5。

### 阶段 5：Workflow、RAG 与 ReAct 自动路由

目标：固定业务走 Workflow，开放问题进入受控 ReAct。

实施内容：

- `IntentKind` 新增 `WORKFLOW`，编码使用 `3`，不修改现有编码。
- `wf_route` 通过 `intentCode` 绑定工作流或 ReAct 配置。
- 路由结果固定为 `WORKFLOW`、`REACT`、`SYSTEM`、`CLARIFY`、`DENY`。
- 路由优先处理确认与审批、高置信固定流程、参数澄清、授权 ReAct、系统回答和拒绝。
- 高风险意图使用更高置信度阈值。
- ReAct 使用请求级授权工具快照。
- 默认最大 5 个推理步骤、8 次工具调用、只读、关闭公网检索；写工具必须确认。
- Tool Gateway 继续做执行时二次鉴权，Prompt 不承担权限判断。
- SSE 增加 `route_decision`、`workflow_progress` 和 `policy_denied`。

测试流程：

1. 年假和审批问题进入 Workflow。
2. 制度问题进入 RAG 并返回来源。
3. 开放分析问题进入只读 ReAct。
4. 模糊意图返回澄清。
5. 身份和工具提示注入测试。
6. 多轮会话角色变化测试。
7. 模型不可用时明确业务入口仍可运行工作流。
8. 至少 100 条路由评测和 30 条 Prompt 注入评测。
9. 固定流程误执行数为 0。
10. 全项目回归测试。

阶段提交：

```text
feat(agent): add workflow react routing decisions
refactor(agent): build request-scoped authorized toolkits
test(agent): add routing and prompt-injection evaluation
docs(stage-5): record routing verification
```

阶段准入条件：固定业务不依赖模型自由规划，ReAct 无法使用未授权工具，固定流程路由准确率不低于 95%，验收提交后进入阶段 6。

### 阶段 6：工作流管理后台

目标：允许业务人员配置流程，同时保证审核、发布和安全边界。

实施内容：

- 前端引入 `bpmn-js`。
- 后台新增工作流中心、工具与连接器、权限策略、路由模拟、运行实例和审计查询。
- 工作流设计器只展示允许节点。
- 支持保存草稿、版本历史、校验、测试运行、提交审核、差异审查、发布和回滚。
- 工具中心展示 Schema、风险、超时、重试、敏感字段和权限。
- 路由模拟支持选择模拟用户和输入问题。
- 审核者不能审核自己提交的流程。
- 后端权限为最终边界，前端隐藏菜单只用于体验。
- 保留现有意图树、知识库和企业配置功能。

测试流程：

1. 工作流创建、编辑和刷新恢复。
2. 非法节点不能保存或发布。
3. 编辑、审核、发布和回滚完整浏览器测试。
4. 编辑者自审拦截。
5. 多人编辑版本冲突测试。
6. 路由模拟与实际运行对比。
7. 工具测试页面权限测试。
8. 确认按钮防重复点击。
9. 桌面和移动布局测试。
10. 前端构建、类型检查及全项目 Maven 回归。

阶段提交：

```text
feat(frontend): add workflow designer and lifecycle pages
feat(frontend): add tool policy route simulation and audit pages
test(frontend): add workflow administration browser tests
docs(stage-6): record admin console verification
```

阶段准入条件：无需修改代码即可创建固定流程，发布经过独立审核，后台不能配置任意代码执行，验收提交后进入阶段 7。

### 阶段 7：标准 ERP 接口准备

目标：为后续公司内部 ERP 提供稳定对接协议。

实施内容：

- 输出 ERP OpenAPI 文档。
- 定义身份传递、幂等键、分页、错误码、超时、追踪号和数据脱敏。
- 首批接口包括员工档案、入职日期和司龄、组织关系、直属主管、年假余额、业务权限以及请假创建/查询/撤回。
- 提供 Mock ERP 服务。
- 增加 `LOCAL` 和 `HTTP_ERP` 适配模式。
- ERP 写请求不得使用模型提供的身份字段。
- ERP 不可用时明确返回失败或处理中，禁止模型编造成功。

测试流程：

1. OpenAPI Schema 校验。
2. 本地和 HTTP Adapter 契约一致性测试。
3. Mock ERP 正常、超时、5xx 和非法响应测试。
4. 重复写入幂等测试。
5. 身份伪造和越权测试。
6. ERP 敏感字段脱敏测试。
7. Adapter 切换及全项目回归测试。

阶段提交：

```text
feat(erp): define standard enterprise erp contract
feat(erp): add local and mock http adapters
test(erp): add consumer contract and failure tests
docs(stage-7): record erp integration readiness
```

阶段准入条件：真实 ERP 只需实现适配器，Agent、Flowable 和前端不依赖具体 ERP，验收提交后进入阶段 8。

### 阶段 8：安全评测、灰度和生产验收

目标：形成可以逐步上线、监控和回退的企业智能体平台。

实施内容：

- 建立业务、路由、权限和注入四类评测集。
- 增加流程成功率、节点耗时、工具失败率、策略拒绝率、审批积压、ReAct 步骤数、模型成本和 ERP 超时率指标。
- 对高风险工具失败、越权突增、状态不一致、审批超期、ERP 异常和敏感信息泄露建立告警。
- 按开发环境、测试账号、单一部门、只读能力、请假写入、全公司的顺序灰度。
- 支持路由切回 legacy、停止新 Flowable 实例、流程版本回滚和人工终止。
- 保留全部业务与审计数据。

最终测试：

1. 全量 Maven 测试。
2. PostgreSQL、Redis、Flowable、RocketMQ、MinIO 全栈测试。
3. 前端构建、类型检查和浏览器测试。
4. 至少 100 条路由评测。
5. 至少 50 条权限攻击测试。
6. 至少 30 条 Prompt 注入测试。
7. 请假生命周期和高并发测试。
8. 应用重启、模型故障、ERP 故障和消息积压演练。
9. 人工审核高风险模型输出。
10. 验证部署、监控、回退和数据对账手册。

生产验收门槛：

- 高风险越权成功数为 0。
- 高风险误执行数为 0。
- 固定流程路由准确率不低于 95%。
- 请假主流程成功率不低于 99.5%。
- 所有写操作具备确认、幂等和审计。
- 所有关键故障有明确回退路径。

阶段提交：

```text
test(security): add enterprise agent security evaluation
feat(observability): add workflow tool and policy monitoring
docs(stage-8): add production acceptance and rollback guide
```

## 四、阶段问题处理规则

| 级别 | 示例 | 处理要求 |
|---|---|---|
| P0 | 越权成功、数据破坏、重复扣减 | 立即停止，当前阶段修复并执行全量回归 |
| P1 | 主流程失败、状态不一致、无法恢复 | 当前阶段不得验收和提交完成状态 |
| P2 | 非主路径功能错误、后台操作异常 | 原则上本阶段修复，不能影响下一阶段接口 |
| P3 | 文案、样式和非阻断体验问题 | 可记录，但必须在最终生产验收前关闭 |

如果阶段测试失败：

```text
保留在当前阶段
→ 定位原因
→ 添加复现测试
→ 修复
→ 重跑专项测试
→ 重跑全量回归
→ 测试通过后提交
```

不得通过跳过测试、删除失败用例或降低权限检查来获得阶段通过。

## 五、既定假设

- 第一阶段开始前，先验收并提交当前脏工作区作为独立基线。
- 每阶段采用“小提交 + 阶段验收提交”，但任何小提交也不能包含未经专项测试的代码。
- Flowable 使用同应用嵌入式部署。
- 工作流发布采用编辑与审核分离。
- 当前单公司运行，新设计预留 `tenantId`。
- 未来 ERP 尚未开发，先实现稳定契约、Mock 和本地 Adapter。
- 现有请假 API 和前端操作保持兼容。
- 旧的运行中请假申请不强制迁移。
- 模型只负责理解、参数收集和表达，不负责权限判断和业务事务。
- 不自动推送远程仓库。
