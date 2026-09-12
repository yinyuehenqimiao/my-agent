# 阶段 B 验收记录：工作流正确性与设计器修复

验收日期：2026-09-12。
对应计划：`docs/stage-6-hardening-and-erp-closure-plan.md` 阶段 B。

## 完成项

- 修复空白 BPMN 模板缺少 `di` 命名空间、真实设计器无法可靠解析的问题。
- 保存与创建版本时直接使用设计器本次导出的 XML，避免 React 异步状态导致保存旧画布。
- 已发布版本允许作为新草稿的来源，同时保持原发布版本和当前运行版本不变。
- 工作流详情默认打开最新版本，不再优先打开旧的已发布版本而遮住新草稿。
- 版本选择、状态展示、审核意见、审核通过、驳回、发布、停用和历史版本切换采用中文交互。
- 生命周期操作期间禁用重复提交，并在成功后重新加载服务端详情。
- 后端详情接口统一返回前端可用的 camelCase 字段并包含 BPMN XML。
- BPMN 校验兼容 `bpmn:` 前缀、图形元数据和标准子元素形式的条件表达式。
- 工作流生命周期写入平台操作审计。

## 故障复现与修复证据

1. `npm test -- WorkflowDesignerPage.test.tsx` 曾稳定失败：`parsererror: unbound namespace prefix: "di"`；补齐命名空间后通过。
2. 同一命令曾稳定失败：期望加载 `draft-v2`，实际加载 `published-v1`；改为默认选择最新版本后通过。
3. 已发布版本派生草稿测试曾因“已发布版本不可编辑”失败；明确区分“修改原记录”与“复制为新草稿”后通过。

## 测试结果

| 检查 | 结果 |
| --- | --- |
| `npm test -- WorkflowDesignerPage.test.tsx` | 4 项通过 |
| `npx tsc --noEmit` | 通过 |
| `npm run build` | 通过，3970 个模块完成构建；保留大分包提示 |
| `mvn -pl workflow -am "-Dtest=BpmnValidatorTest,WorkflowVersionServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 20 项通过 |
| `mvn -pl workflow -am "-Dtest=WorkflowAdminControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 6 项通过 |
| `mvn test` | 9 个模块 BUILD SUCCESS |

## 已知限制与下一步

- 当前“创建新版本”采用追加版本，不提供对既有草稿的原地更新；后续应增加带乐观锁的草稿保存接口。
- 当前按钮是否显示尚未读取前端细粒度权限，后端权限仍是最终边界；阶段 C/E 继续处理。
- 工具中心与路由模拟仍需在阶段 D 替换为真实后端数据。
- 全量测试发现 `KnowledgeAccessTest` 的权限拒绝审计表主键与生产结构不一致，日志显示写入失败但测试仍通过；阶段 E 必须补成真实落库断言。
- Maven/Java 测试日志存在中文控制台乱码；不影响本阶段测试结论，阶段 E 统一处理编码。

## 下一阶段准入结论

本阶段核心回归用例、工作流专项测试、前端类型检查、生产构建和全项目测试均通过。完成阶段提交后，可进入阶段 C/D；企业请假 Flowable 链路的其他未提交修改不纳入本阶段提交。
