# G6：外部 ERP 请假确认闭环验证

## 行为边界

- HTTP ERP 模式下，`prepare_leave_request` 只创建本地确认卡，不调用 ERP 写接口。
- 卡片确认仅允许同一租户、同一登录用户执行；重复确认返回原结果，不重复写入 ERP。
- 确认卡使用服务端会话标识隔离；前端以中文提示“提交请假申请”，并请求独立的 `/api/enterprise/erp-actions/{id}` 接口。
- 本阶段仅开放“创建请假申请”的外部 ERP 写闭环。审批、撤回保持禁用，避免把本地流程语义错误映射到外部系统。

## 部署前置条件

在启用 `myagent.enterprise.adapter=HTTP_ERP` 前，先执行：

`resources/database/upgrades/v0.5.0/01_erp_pending_action.sql`

该表只保存确认意图、归属和幂等上下文，不保存 ERP 业务事实。

## 自动化验证

- `mvn -pl enterprise -am -Dtest=ExternalErpActionServiceTest '-Dsurefire.failIfNoSpecifiedTests=false' test`：2 项通过。
- `mvn -pl agent -am '-Dtest=EnterpriseToolsTest,EnterprisePendingActionResolverTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`：12 项通过。
- `mvn -q test`：全量通过。
- `frontend/npm run build`：通过；现有大文件分包告警不影响构建结果。
