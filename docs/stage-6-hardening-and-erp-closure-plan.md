# `my-agent` 阶段 6 后质量修复与轻量 ERP 闭环实施计划

## 一、文档用途与当前基线

本文是在 `docs/enterprise-agent-workflow-plan.md` 基础上的后续执行方案，适用于阶段 1～6 已完成后，对现有缺陷、管理后台体验、真实路由闭环和 ERP 集成进行系统补齐。

本方案只保存实施路线，不代表相关功能已经完成。后续执行时必须遵循：**一次只实施一个阶段，专项测试通过后再做全量回归，全量回归通过后提交当前阶段代码，然后才进入下一阶段。**

根据当前代码和阶段验收记录，已确认的主要缺口如下：

- 工作流设计器保存时存在使用旧 XML 状态、覆盖最新画布内容的风险。
- 已发布版本应能作为新草稿的来源，但不能直接修改已发布记录。
- 工作流设计器缺少清晰的版本选择、审核、驳回、停用和回滚操作。
- 工具中心是静态说明页，尚未读取 Tool Gateway 的真实工具定义。
- 路由模拟使用前端关键词判断，尚未调用后端真实路由服务。
- `RoutingService` 尚未完整接入聊天主链和 SSE 事件链。
- 后台仍有较多英文状态、技术字段和自由文本输入，不符合业务管理员的使用习惯。
- 管理端菜单主要依赖旧的 `admin` 角色显示，尚未完整映射细粒度权限码。
- 权限拒绝审计在部分测试表结构下可能写入失败且被吞掉，需要验证真实落库。
- 前端缺少覆盖核心后台操作的稳定组件测试和端到端测试。
- ERP 目前只有适配接口准备，尚无独立可运行的轻量 ERP 服务与 HTTP 闭环。

## 二、总体目标架构

采用“智能体平台与 ERP 分离部署、通过受控适配器连接”的结构：

```text
企业用户
  → my-agent 前端
  → 认证 + ExecutionIdentity
  → 真实意图识别与路由
      ├─ 固定业务：Workflow / Flowable
      ├─ 知识问题：RAG
      ├─ 开放分析：受控 ReAct
      └─ 无权或不确定：DENY / CLARIFY
  → Tool Gateway（二次鉴权、Schema、幂等、脱敏、审计）
  → EnterpriseSystemPort
      ├─ LOCAL：现有本地企业数据
      └─ HTTP_ERP：独立轻量 ERP 服务
  → 员工、组织、年假、权限、请假数据
```

ERP 技术参考优先采用 Java 生态的 [Apache OFBiz](https://github.com/apache/ofbiz-framework) 的模块边界与企业领域划分；同时参考 [ERPNext](https://github.com/frappe/erpnext) 和 [Odoo](https://github.com/odoo/odoo) 的员工、组织、审批与权限设计。**不直接复制或嵌入完整 ERP 项目**，避免引入远超当前需求的会计、库存、采购和部署复杂度；只实现能够验证智能体闭环的企业人事与请假子集。

推荐新增独立项目：

```text
D:\Project\AI\myAgent\
├─ my-agent\                 # 智能体、工作流、RAG、管理后台
└─ internal-erp\             # 独立轻量 ERP Spring Boot 服务
```

独立服务的好处是 ERP 拥有自己的数据边界、认证、审计和事务，`my-agent` 只能通过契约访问，能够真实验证未来替换公司 ERP 时的集成方式。

## 三、统一权限和中文交互设计

### 3.1 推荐角色

| 中文名称 | 后端角色码 | 主要能力 |
| --- | --- | --- |
| 平台管理员 | `PLATFORM_ADMIN` | 用户、角色、基础配置，不自动拥有业务审批权 |
| 流程编辑员 | `WORKFLOW_EDITOR` | 创建草稿、编辑、校验、提交审核 |
| 流程审核员 | `WORKFLOW_REVIEWER` | 审核或驳回他人提交的流程 |
| 安全管理员 | `SECURITY_ADMIN` | 工具策略、权限策略、连接器配置 |
| 审计员 | `AUDITOR` | 只读查看操作、权限、流程和工具审计 |
| 普通员工 | `NORMAL_USER` | 查询本人信息、发起本人业务 |
| 直属主管 | 企业属性 `MANAGER` | 查看并处理授权范围内的下属任务 |
| 人事人员 | 企业属性 `HR` | 人事数据范围内查询及 HR 审批 |

平台角色与企业角色必须分离。平台管理员不能因为管理系统配置而查看全员敏感数据或替代主管、HR 审批。

### 3.2 权限原则

- 默认拒绝，后端权限判断是最终边界。
- 前端只根据权限隐藏或禁用操作，不替代后端鉴权。
- 查询本人信息不接受前端或模型传入的 `userId`，由登录身份注入。
- 查询他人信息必须同时满足功能权限和数据范围权限。
- 流程编辑、审核、发布分权，禁止提交人审核自己的版本。
- 工具测试默认只允许只读工具；写工具必须二次确认、幂等键和审计。
- ERP 服务使用服务凭证验证 `my-agent`，终端用户身份以签名请求上下文传递，不直接信任普通 Header。
- 敏感字段按角色脱敏；身份证号、手机号、薪资等默认不进入模型上下文。

### 3.3 中文后台规范

- 页面标题、按钮、状态、风险等级、节点类型和错误提示全部提供中文名称。
- 数据库存储稳定枚举码，前端统一映射中文，例如 `IN_REVIEW → 待审核`、`RETIRED → 已停用`。
- 普通操作使用下拉框、单选、开关和分步表单；JSON、Schema、BPMN XML 放入“高级配置”。
- “流程键”显示为“流程标识”，并提示只能包含小写字母、数字和短横线。
- 危险操作使用明确影响说明，不使用含糊的“确定/取消”。
- 空状态说明下一步怎么做；失败提示包含原因和可执行的恢复建议。

## 四、分阶段实施计划

## 阶段 A：冻结现场、建立缺陷基线

### 目标

不修改业务行为，先识别当前工作区已有修改、可复现缺陷和真实测试基线，为后续提交隔离范围。

### 实施内容

1. 检查 `git status` 和每个已修改文件的差异，标记用户修改、历史阶段遗留和本阶段允许修改。
2. 建立缺陷清单，每条包含：编号、级别、复现步骤、预期、实际、影响范围和负责人。
3. 记录当前 Maven、前端类型检查、构建和已有测试结果。
4. 对日志乱码、审计写入告警、静态页面和路由未接主链分别建立可重复验证方法。
5. 禁止为了清理工作区执行强制重置或覆盖用户修改。

### 测试流程

1. `mvn test`
2. `mvn -pl bootstrap -am package -DskipTests`
3. `cd frontend && npx tsc --noEmit`
4. `cd frontend && npm run build`
5. 记录失败用例和环境依赖，不在本阶段隐藏失败。

### 验收门槛

- 所有已有修改均已归属。
- P0～P3 缺陷清单完成。
- 基线命令、结果和总测试数已记录。

### 建议提交

```text
docs(quality): record post-stage-6 defect baseline
```

## 阶段 B：修复工作流版本与设计器正确性

### 目标

先解决数据丢失、版本不可继续演进和生命周期操作不完整等核心正确性问题。

### 实施内容

1. 保存前主动调用设计器 `saveXML()`，请求必须使用本次导出的 XML，禁止依赖异步 React 旧状态。
2. 已发布版本保持不可编辑，但允许复制为下一版草稿；新版本记录父版本，原发布版本不变。
3. 默认打开最新草稿；无草稿时打开已发布版本并提示“需创建新版本后编辑”。
4. 增加中文版本选择器，显示版本号、中文状态、创建人和时间。
5. 补齐提交审核、审核通过、驳回、发布、停用和回滚操作。
6. 所有提交按钮增加请求中状态，防止重复点击。
7. 为多人同时编辑增加乐观锁或版本号校验，冲突时提示刷新后重新合并。
8. 发布前再次执行后端 BPMN 安全校验，前端校验仅用于快速反馈。

### 测试流程

1. 先写失败测试：画布刚修改后立即保存，断言后端收到最新 XML。
2. 已发布 v1 创建 v2 草稿，断言 v1 仍为 `PUBLISHED`。
3. 草稿、待审核、通过、驳回、发布、停用、回滚状态机测试。
4. 提交人自审必须返回 403 或业务拒绝码。
5. 两个客户端修改同一版本，后提交者收到版本冲突。
6. 刷新页面后画布、版本和状态正确恢复。
7. 非法节点、任意 Java 类、脚本和动态 HTTP 节点无法发布。
8. 运行 `mvn -pl workflow -am test`、前端组件测试、类型检查和构建。
9. 最后运行全项目 `mvn test`。

### 验收门槛

- 不再出现保存旧 XML 或覆盖发布版本。
- 生命周期每一步均有后端权限测试和前端交互测试。
- 专项测试和全量回归全部通过。

### 建议提交

```text
fix(workflow): preserve latest canvas and published version history
feat(frontend): complete workflow review publish and rollback actions
test(workflow): cover designer persistence and version conflicts
docs(quality): record workflow hardening verification
```

## 阶段 C：后台中文化与易用性重构

### 目标

让非研发管理员可以理解和操作后台，减少英文枚举、自由输入和技术配置直接暴露。

### 实施内容

1. 建立全局枚举字典：流程状态、路由类型、工具风险、读写类型、审计结果、员工状态。
2. 将流程节点配置改为中文节点面板：开始、参数校验、权限检查、读取数据、知识检索、条件判断、用户确认、人工审批、响应、结束。
3. 限制或隐藏 `bpmn-js` 默认英文节点面板，只允许安全白名单节点。
4. 将办理人、工具、权限、条件操作符改为后端字典驱动的选择控件。
5. 增加表单校验、帮助提示、空状态、加载状态、失败恢复和移动端布局。
6. 按实际权限显示按钮；无权限时可以查看但明确说明不可操作原因。
7. 技术信息放入可折叠高级区，业务表单默认不展示 JSON/XML。

### 测试流程

1. 中文枚举映射单元测试，确保未知值有安全兜底显示。
2. 表单必填、格式、互斥选项和危险操作确认测试。
3. 编辑员、审核员、发布员、审计员四种权限界面测试。
4. 1366×768、1920×1080 和常见移动宽度布局测试。
5. 键盘操作、焦点、表单标签和基础可访问性检查。
6. 页面刷新、接口超时、403、409 和 500 状态展示测试。
7. 前端组件测试、浏览器 E2E、类型检查和生产构建。
8. 全项目 Maven 回归。

### 验收门槛

- 业务管理员完成常用配置无需编辑 JSON/XML。
- 核心页面不存在未翻译的业务状态和操作按钮。
- 权限不同的用户只能看到或操作其授权内容。

### 建议提交

```text
feat(frontend): localize and simplify workflow administration
test(frontend): cover localized forms permissions and responsive layout
docs(quality): record admin usability verification
```

## 阶段 D：真实工具中心、路由模拟与聊天主链闭环

### 目标

移除后台假数据，让配置、模拟和用户实际聊天使用同一套后端决策与工具定义。

### 实施内容

1. Tool Gateway 提供只读工具目录查询，返回名称、中文说明、Schema、风险、超时、重试、确认要求、权限码、来源和启用状态。
2. 工具中心从真实接口加载，默认显示中文摘要，Schema 放在高级详情。
3. 新增真实路由模拟接口，输入问题和模拟身份，返回候选意图、置信度、最终路由、目标流程、拒绝原因和命中策略。
4. 模拟他人身份仅限拥有专门权限的管理员，并且必须写审计；普通管理员只能模拟自己或预设匿名权限模板。
5. `AgentChatService` 接入 `IntentClassifier → RoutingService → Workflow/RAG/ReAct`。
6. SSE 实际发送 `route_decision`、`workflow_progress` 和 `policy_denied`。
7. ReAct 应用最大步骤、最大工具次数、只读默认和公网关闭等护栏。
8. Tool Gateway 每次执行再次鉴权，不能只依赖路由阶段的判断。
9. 删除前端关键词假路由，模拟结果与实际聊天共用后端服务。

### 测试流程

1. 工具目录返回真实注册工具，停用工具和无权工具按策略过滤或标记。
2. 未授权用户不能查看敏感 Schema、测试工具或模拟高权限身份。
3. 同一问题在模拟接口与真实聊天中的路由结果一致。
4. 年假/请假进入 Workflow，制度问题进入 RAG，开放分析进入受控 ReAct。
5. 模糊问题进入 CLARIFY，高风险无权限进入 DENY。
6. 模型不可用时，明确固定入口仍能执行 Workflow。
7. Prompt 注入、伪造 `userId`、诱导调用停用工具全部失败。
8. 验证 SSE 事件顺序、断线恢复和错误终止事件。
9. 至少执行 100 条路由评测和 30 条 Prompt 注入评测。
10. Maven、前端、浏览器和全项目回归全部通过。

### 验收门槛

- 后台不再使用静态工具数据和前端假路由。
- 模拟与实际执行使用同一决策服务。
- 固定流程误执行为 0，固定流程路由准确率不低于 95%。

### 建议提交

```text
feat(agent): connect routing service to chat execution
feat(agent): expose authorized tool catalog and route simulation
feat(frontend): replace workflow admin placeholders with live data
test(agent): cover routing chat sse and prompt injection
docs(quality): record routing closure verification
```

## 阶段 E：权限审计与稳定性加固

### 目标

确保权限拒绝、流程动作、工具调用和 ERP 请求可追踪，且审计失败不会被悄悄忽略。

### 实施内容

1. 对齐生产升级脚本、H2 测试表和实体的审计主键类型及默认值。
2. 审计写入失败必须产生独立告警指标；高风险操作在无法审计时默认拒绝。
3. 统一 `traceId`、操作者、租户、目标资源、权限决策、脱敏输入摘要、结果和耗时。
4. 审计页面合并操作审计、权限拒绝、流程动作和工具调用，支持中文筛选和详情。
5. 增加日志 UTF-8 配置，确保中文异常和测试输出可读。
6. 为外部接口增加超时、熔断、有限重试；写操作仅在幂等条件下重试。

### 测试流程

1. 审计真实落库测试，不只断言业务请求成功。
2. 审计库不可用时，高风险写操作拒绝、只读操作按配置降级。
3. 权限拒绝、流程审核、工具调用和 ERP 调用均能按 `traceId` 串联。
4. 敏感字段不出现在日志、SSE、审计详情和模型上下文。
5. 并发写入、重复请求、超时和故障恢复测试。
6. PostgreSQL 真实集成测试及全项目回归。

### 验收门槛

- 高风险操作审计覆盖率 100%。
- 审计失败有明确告警和安全降级行为。
- 不存在敏感信息明文泄露。

### 建议提交

```text
fix(audit): guarantee authorization and tool audit persistence
feat(frontend): add unified localized audit explorer
test(security): cover audit failure masking and traceability
docs(quality): record security hardening verification
```

## 阶段 F：开发独立轻量 ERP 服务

### 目标

实现一个能独立运行、具有真实权限和事务边界的最小企业 ERP，覆盖智能体闭环所需的人事与请假能力。

### 领域范围

第一版只实现：

- 员工档案：工号、姓名、入职日期、在职状态、部门和岗位。
- 组织关系：公司、部门、直属主管。
- 业务角色：员工、主管、HR。
- 年假账户：年度额度、已用、冻结、可用。
- 请假申请：创建、查询、撤回、主管审批、HR 审批。
- 业务权限：本人、下属、部门和 HR 数据范围。
- 幂等记录、服务访问凭证和操作审计。

明确不做：财务总账、薪酬核算、采购、库存、生产、CRM 等与当前闭环无关的完整 ERP 功能。

### 推荐模块

```text
internal-erp
├─ erp-bootstrap       # 启动与配置
├─ erp-identity        # 服务认证与用户上下文
├─ erp-organization    # 员工、部门、岗位、主管
├─ erp-leave           # 年假账户与请假事务
├─ erp-audit           # 审计与幂等
└─ erp-api             # DTO、错误码和 OpenAPI
```

若项目规模较小，可以先保持单 Maven 模块并按 package 分层，避免过早拆分；当领域和团队扩大后再拆成多模块。

### API 范围

- `GET /api/erp/v1/employees/me`
- `GET /api/erp/v1/employees/me/tenure`
- `GET /api/erp/v1/employees/me/permissions`
- `GET /api/erp/v1/leave-balances/me?year=2026`
- `POST /api/erp/v1/leave-requests`
- `GET /api/erp/v1/leave-requests/{id}`
- `POST /api/erp/v1/leave-requests/{id}/withdraw`
- `GET /api/erp/v1/tasks/me`
- `POST /api/erp/v1/tasks/{id}/approve`
- `POST /api/erp/v1/tasks/{id}/reject`

写接口必须携带 `Idempotency-Key` 和 `X-Trace-Id`。接口返回稳定业务错误码，不向调用方暴露数据库异常。

### 数据一致性

- 年假创建先冻结余额，审批通过后结算，拒绝或撤回后释放。
- 年假账户使用版本号或原子条件更新，防止并发超扣。
- 幂等键按“调用方 + 接口 + 键”唯一。
- 业务表记录事实状态；`my-agent` 工作流只负责协调，不能成为 ERP 业务事实源。

### 测试流程

1. 员工、部门、主管关系单元测试。
2. 入职年限边界测试：周年日前后、闰日、离职员工。
3. 年假额度、冻结、结算、释放和跨年测试。
4. 同一用户并发申请，余额不能超扣。
5. 同一幂等键重复写入只产生一笔业务记录。
6. 员工不能查询他人，主管只能查询下属，HR 按授权范围查询。
7. 服务凭证缺失、过期、签名错误和重放请求测试。
8. PostgreSQL Testcontainers 或真实测试库集成测试。
9. OpenAPI Schema 校验和错误响应契约测试。
10. 服务启动、健康检查和基础冒烟测试。

### 验收门槛

- ERP 可独立启动和测试，不依赖 `my-agent` 数据库表。
- 权限、幂等、并发余额和审计测试全部通过。
- OpenAPI 文档可用于生成或验证客户端。

### 建议提交

以下提交位于 `internal-erp` 自己的 Git 仓库；如果未单独建仓，则先由执行者明确仓库策略：

```text
feat(erp): add employee organization and authorization domains
feat(erp): add leave balance and request lifecycle
feat(erp): add service authentication idempotency and audit
test(erp): cover authorization concurrency and api contracts
docs(erp): add deployment and integration guide
```

## 阶段 G：接入 HTTP ERP 并完成端到端闭环

### 目标

让用户在 `my-agent` 中通过聊天或固定工作流安全查询 ERP 信息并完成请假业务。

### 实施内容

1. 实现 `HttpEnterpriseSystemAdapter`，与 `LocalEnterpriseSystemAdapter` 共享契约测试。
2. 配置 `enterprise.adapter-mode=LOCAL|HTTP_ERP`，默认开发环境仍可使用 LOCAL。
3. 增加 ERP 地址、服务凭证、连接池、超时、重试和熔断配置；密钥只从环境变量或密钥系统读取。
4. 将当前登录用户映射为 ERP 员工身份，不允许模型指定查询对象。
5. Tool Gateway 注册本人档案、司龄、年假余额、本人权限、请假创建/查询/撤回等工具。
6. 固定查询优先走 Workflow 或确定性工具，不需要 ReAct 自由规划。
7. 请假写操作展示确认卡，确认后携带幂等键调用 ERP。
8. Flowable 记录流程协调状态，ERP 保存请假和余额事实；通过业务键和 `traceId` 对账。
9. ERP 不可用时返回明确的“暂时无法查询/提交”，禁止模型生成虚假成功结果。
10. 管理后台增加 ERP 连接状态、最近失败和只读连通性测试，不提供任意 URL 测试能力。

### 端到端测试场景

1. “我的入职时间是什么时候”返回本人 ERP 数据。
2. “我入职几年了”由确定性日期逻辑计算，并展示数据更新时间。
3. “我还有多少年假”返回指定年度余额，不泄露他人数据。
4. “我有什么权限”返回业务可理解的中文权限，不直接暴露内部权限码。
5. “帮我请下周一到周二的年假”进入固定流程、补全参数、生成确认卡、确认后只创建一次。
6. 主管和 HR 在各自范围内处理审批，普通员工无法越权处理。
7. 用户诱导模型查询他人工号或伪造 Header 时被拒绝并审计。
8. ERP 超时、5xx、非法响应和断路器打开时，业务状态不被误标为成功。
9. `LOCAL` 与 `HTTP_ERP` 适配器对同一契约返回一致语义。
10. 前端聊天、SSE、工作流实例、ERP 记录和审计可通过 `traceId` 对齐。
11. 后端全量测试、前端组件测试、浏览器 E2E、构建和真实双服务冒烟全部通过。

### 验收门槛

- 查询本人信息、年假、权限和入职年限形成真实 ERP 闭环。
- 请假写操作满足确认、幂等、权限、事务和审计要求。
- ERP 故障不会造成虚假成功、重复申请或余额错误。
- `LOCAL` 模式可以作为开发回退，但生产切换有明确操作说明。

### 建议提交

```text
feat(enterprise): add authenticated http erp adapter
feat(agent): expose erp employee and leave capabilities safely
test(integration): cover my-agent and internal-erp end-to-end flow
docs(stage-7): record erp closure verification and rollback
```

## 阶段 H：生产级验收、灰度与回退

### 目标

完成上线前的安全、性能、可观测性和回退验证。

### 实施内容

1. 建立路由、权限、Prompt 注入、ERP 故障和流程状态一致性评测集。
2. 采集流程成功率、路由准确率、工具失败率、策略拒绝率、ERP 延迟、超时率、重复写入数和审批积压。
3. 配置高风险越权、审计失败、余额不一致、ERP 连续失败和敏感信息泄露告警。
4. 按测试账号 → 单部门只读 → 单部门请假 → 多部门 → 全公司的顺序灰度。
5. 准备路由切回 legacy、切换 LOCAL、暂停新流程、回滚流程版本和人工对账手册。

### 最终测试

1. 全量 Maven 测试和前端测试。
2. PostgreSQL、Redis、Flowable、RocketMQ、MinIO、`internal-erp` 全栈测试。
3. 至少 100 条路由、50 条权限攻击、30 条 Prompt 注入用例。
4. 200 个并发查询和受控并发请假压测，写请求不得重复。
5. 应用重启、ERP 重启、网络中断、模型不可用和数据库短暂故障演练。
6. 审计对账：抽样请求必须能从前端追踪到 Agent、Workflow、Tool Gateway 和 ERP。

### 生产验收门槛

- 高风险越权成功数为 0。
- 高风险误执行数为 0。
- 固定流程路由准确率不低于 95%。
- 核心查询在约定负载下满足团队确定的 P95 延迟指标。
- 请假写操作重复落单数为 0，余额不一致数为 0。
- 所有关键故障均有已演练的恢复或回退路径。

### 建议提交

```text
test(security): add post-stage-6 security evaluation suite
feat(observability): add routing workflow tool and erp monitoring
docs(release): add gray release rollback and reconciliation guide
```

## 五、每阶段固定执行模板

每一个阶段均按以下顺序执行，不能跳步：

```text
1. 确认阶段范围和当前工作区
2. 为缺陷或需求建立失败测试
3. 实现最小改动使专项测试通过
4. 重构并再次运行专项测试
5. 执行模块级集成测试
6. 执行全项目 Maven 回归
7. 执行前端测试、类型检查和构建
8. 涉及 UI 时执行浏览器 E2E 和人工合理性检查
9. 更新阶段验收文档
10. 审阅 git diff，排除用户修改、密钥和构建产物
11. 提交当前阶段代码并记录 commit ID
12. 工作区符合预期后才进入下一阶段
```

阶段验收记录模板：

```markdown
# 阶段 X 验收记录

- 完成项：
- 修复缺陷：
- 修改模块：
- 数据库变化：
- 接口变化：
- 权限影响：
- 测试命令与结果：
- 浏览器验证结果：
- 已知限制：
- Git commit ID：
- 下一阶段准入结论：允许 / 不允许
```

## 六、缺陷优先级与停止条件

| 级别 | 典型问题 | 处理规则 |
| --- | --- | --- |
| P0 | 越权、数据泄露、余额错误、重复写入、数据破坏 | 立即停止后续阶段，修复并全量回归 |
| P1 | 主流程不可用、发布版本被覆盖、状态不一致、无法恢复 | 当前阶段不得提交完成状态 |
| P2 | 后台操作异常、模拟与实际不一致、审计缺失 | 原则上当前阶段修复 |
| P3 | 文案、布局、提示和非阻断体验问题 | 记录并最迟在阶段 H 前关闭 |

不得通过以下方式让阶段“通过”：

- 跳过、删除或放宽失败测试；
- 将安全默认值改为允许；
- 吞掉异常并返回成功；
- 使用前端假数据代替真实后端闭环；
- 将模型回答当作 ERP 写入成功凭证；
- 把未完成能力写入验收文档的“已完成项”。

## 七、推荐执行顺序摘要

```text
A 缺陷基线
→ B 工作流正确性
→ C 中文化与易用性
→ D 真实工具、路由与聊天闭环
→ E 权限审计加固
→ F 独立轻量 ERP
→ G my-agent × ERP 完整闭环
→ H 灰度、生产验收与回退
```

这个顺序优先修复现有系统的真实性和安全性，再开发 ERP，避免把当前的静态页面、假路由或权限缺口带入新的集成链路。
