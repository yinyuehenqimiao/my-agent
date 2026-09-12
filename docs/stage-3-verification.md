# 阶段 3 验收记录：嵌入式 Flowable 工作流运行时

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 3。
目标：新增可持久化、可版本化、可恢复的固定业务流程能力。

## 一、本阶段完成项

- 新增 `workflow` Maven 模块并嵌入当前 Spring Boot 应用；引入 Flowable 8.0.0（兼容 Spring Boot 4.1）。
- 使用当前 PostgreSQL，Flowable `ACT_*` 表（引擎自管）与业务表（`wf_*`/`ent_*`/`t_*`）天然隔离。
- 新增业务表：`wf_definition`、`wf_version`、`wf_review`、`wf_route`、`workflow_action`、`operation_audit`。
- 版本状态固定为 `DRAFT`/`IN_REVIEW`/`APPROVED`/`PUBLISHED`/`REJECTED`/`RETIRED`。
- 发布版本不可编辑，修改创建新版本（`newVersionFrom`）。
- 编辑与审核分离：编辑者不得审核自己提交（`review` 校验）。
- BPMN 安全校验（`BpmnValidator`）：节点白名单（startEvent/endEvent/serviceTask/userTask/exclusiveGateway 等），禁止 `scriptTask`/Java 类（`flowable:class`/`expression`/`delegateExpression`）/脚本/HTTP；`flowable:type` 仅允许安全类型（tool/validate/permission/knowledge/template/confirm/approve）。
- 条件表达式使用白名单字段与操作符。
- 发布前检查：开始/结束节点、不可达节点、节点数上限（200）。
- 运行时（`WorkflowRuntimeService`）：每个已发布版本部署为独立流程定义（key 含版本 id），实例按该 key 启动，固定绑定特定版本；实例携带 `tenantId`/`initiatorId`/`businessKey`/`traceId`；流程变量只保存必要业务引用；Flowable 持久化保证重启后恢复。

## 二、实际修改模块

- `pom.xml`（根）：新增 `workflow` 模块、`flowable.version` 属性、`flowable-spring-boot` BOM 导入、`workflow` 依赖管理。
- `workflow`（新模块）：`enums/VersionStatus`、`dao/entity/{WfDefinitionDO,WfVersionDO,WfReviewDO}`、`validation/BpmnValidator`、`service/WorkflowVersionService`、`runtime/WorkflowRuntimeService`、`WorkflowRuntime`。
- `bootstrap`：引入 `workflow` 依赖。
- `system`：`@MapperScan` 评估（最终未新增 workflow mapper，因版本服务改用 JdbcTemplate 便于 H2 测试）。

## 三、数据库变化

- 新增 6 张业务表 + 6 个索引（`wf_definition`/`wf_version`/`wf_review`/`wf_route`/`workflow_action`/`operation_audit`），幂等迁移已在 myagent 库执行验证。
- Flowable `ACT_*` 表由引擎在应用启动时自动创建（`flowable.database-schema-update` 默认），与业务表隔离。

## 四、新增或调整的接口

- 内部服务：`WorkflowVersionService`（版本生命周期）、`WorkflowRuntimeService`（实例运行时）、`BpmnValidator`（安全校验）。
- 对外 HTTP 接口未新增（阶段 6 管理后台再暴露）。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl workflow -am test -B` | BUILD SUCCESS；`BpmnValidatorTest` 10 项、`WorkflowVersionServiceTest` 8 项全过 |
| `mvn test -B`（全项目） | BUILD SUCCESS；9 模块全过；Tests run 157 项（原 139 + 新增 18），Failures 0，Errors 0 |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 12.84s |

阶段 3 专项测试覆盖：
1. 合法 BPMN 通过语法与发布校验。
2. 非法节点（scriptTask/callActivity）拒绝；Java 类委托（flowable:class）拒绝。
3. 安全 serviceTask type（tool）允许；禁止脚本子元素。
4. 条件表达式白名单字段允许（days），非白名单字段拒绝（password）。
5. 发布前检查：缺开始/结束节点拒绝；不可达节点检测。
6. 空 BPMN 拒绝。
7. 版本生命周期：草稿→审核→批准→发布→退休→回滚全链路。
8. 编辑者自审拦截；驳回转 REJECTED。
9. 发布版本不可编辑（newVersionFrom 抛错）；草稿创建新版本号递增。
10. 重复 defKey 拒绝；回滚到非已发布版本拒绝；非法 BPMN 发布校验失败。

## 六、已知限制

- Flowable 运行时（实例启动/恢复/人工任务）的端到端集成测试需应用启动 + PostgreSQL + Flowable 引擎，未在单测覆盖；阶段 4 请假迁移将做真实流程联调验证。
- `WorkflowTaskHandler` SPI（工具任务/知识检索/权限检查节点的运行时解释）留待阶段 4/5 实现，本阶段已通过 `flowable:type` 白名单预留。
- `wf_route` 路由表已建，绑定逻辑在阶段 5 实现。

## 七、Git 提交

- `feat(workflow): add embedded flowable runtime`
- `feat(workflow): add version review and publish lifecycle`
- `feat(workflow): add safe node validation and runtime audit`
- `test(workflow): cover persistence versioning and authorization`
- `docs(stage-3): record flowable runtime verification`

## 八、下一阶段准入结论

工作流模块已嵌入并编译通过，版本生命周期与 BPMN 安全校验单测全过，发布版本不可编辑与自审拦截已强制，错误定义无法发布。Flowable 运行时端到端联调留待阶段 4 请假迁移验证。可进入阶段 4（现有请假流程迁移到 Flowable）。
