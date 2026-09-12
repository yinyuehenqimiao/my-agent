# 阶段 6 验收记录：工作流管理后台

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 6。
目标：允许业务人员配置流程，同时保证审核、发布和安全边界。

## 一、本阶段完成项

- 后端 `WorkflowAdminController`：定义/版本 CRUD、提交审核、审核、发布、退休、回滚、审计查询；后端权限为最终边界（workflow:edit/review/publish、audit:view），编辑者不得审核自己提交（服务层强制）。
- 前端引入 `bpmn-js`，新增工作流设计器页（`WorkflowDesignerPage`）：可视化建模，只展示允许节点（开始/结束/服务任务/人工任务/排他网关），保存草稿、创建新版本、提交审核、发布。
- 工作流列表页（`WorkflowListPage`）：列出定义与当前发布版本，进入编辑。
- 工具中心页（`ToolCenterPage`）：展示工具 Schema、风险、超时、重试、敏感字段、权限。
- 路由模拟页（`RouteSimulationPage`）：选择模拟用户与输入问题，对比路由决策。
- 审计查询页（`AuditPage`）：查询操作审计。
- AdminLayout 菜单新增「工作流中心」分组（工作流列表/工具与连接器/路由模拟/审计查询），路由与面包屑适配。
- 保留现有意图树、知识库、企业配置、智能体管理功能。

## 二、实际修改模块

- `workflow`：新增 `controller/WorkflowAdminController` + 控制器测试。
- `frontend`：新增 `services/workflowService`、`pages/admin/workflow`（设计器/列表/工具中心/路由模拟/审计）；`router` 增工作流路由；`AdminLayout` 增工作流菜单与面包屑；`package.json` 增 `bpmn-js`。

## 三、数据库变化

无新增表（复用阶段 3 的 `wf_*` 与 `operation_audit`）。

## 四、新增或调整的接口

- 对外 REST：`/api/workflow/definitions`（GET/POST）、`/api/workflow/definitions/{defKey}`（GET）、`/api/workflow/versions`（POST）、`/api/workflow/versions/{id}/submit-review|review|publish|retire`（POST）、`/api/workflow/definitions/{defId}/rollback/{versionId}`（POST）、`/api/workflow/audit`（GET）。
- 前端路由：`/admin/workflow`、`/admin/workflow/new`、`/admin/workflow/:defKey`、`/admin/workflow/tools`、`/admin/workflow/simulate`、`/admin/workflow/audit`。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl workflow -am test -B` | BUILD SUCCESS；`WorkflowAdminControllerTest` 5 项全过（权限码鉴权 + 委托服务层） |
| `mvn test -B`（全项目） | BUILD SUCCESS；9 模块全过；Tests run 177 项（原 172 + 新增 5），Failures 0，Errors 0 |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 14.37s（含 bpmn-js 设计器） |

阶段 6 专项测试覆盖：
1. createDefinition 权限拒绝 → 抛异常，不调用服务。
2. createDefinition 权限通过 → 委托 versionService.createDraft。
3. publish 需 workflow:publish 权限。
4. review 需 workflow:review 权限。
5. audit 需 audit:view 权限。
原有意图树/知识库/企业/智能体测试全量回归通过。

## 六、已知限制

- 工作流设计器当前加载空画布；加载已发布/最新版本 BPMN 到画布、版本差异审查、多人编辑版本冲突的浏览器端深度测试留待后续（后端能力已具备）。
- 路由模拟当前为前端演示决策；对接后端 RoutingService 实际决策接口留待后续。
- 确认按钮防重复点击、移动布局的完整浏览器测试留待阶段 8。
- 工具中心当前为静态展示；从 Tool Gateway 动态拉取工具定义留待后续。

## 七、Git 提交

- `feat(frontend): add workflow designer and lifecycle pages`
- `feat(frontend): add tool policy route simulation and audit pages`
- `test(frontend): add workflow administration browser tests`
- `docs(stage-6): record admin console verification`

## 八、下一阶段准入结论

工作流管理后台已具备定义/版本生命周期、设计器、工具中心、路由模拟、审计查询，后端权限为最终边界，前端隐藏菜单仅用于体验，原有功能未退化。可进入阶段 7（标准 ERP 接口准备）。
