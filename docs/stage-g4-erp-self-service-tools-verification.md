# 阶段 G4：ERP 本人自助查询工具验收记录

- 完成项：扩展 `EnterpriseSystemPort`，增加本人入职年限和面向业务的权限名称；HTTP ERP 适配器已调用独立 ERP 的 `/employees/me/tenure` 与 `/employees/me/permissions`。
- 工具接入：`get_my_profile`、`get_leave_balance`、`get_my_tenure`、`get_my_business_permissions` 均通过端口访问，执行身份只来自服务端 `RuntimeContext`。
- 模式隔离：`HTTP_ERP` 模式只注册已完成契约的四个只读自助查询工具，不注册仍直接依赖本地请假库的历史查询、审批和写操作，避免错误读取或写入本地事实数据。
- 本地兼容：旧本地数据表没有入职日期时返回 `available=false`，不根据账号创建时间推测司龄；权限按现有企业业务角色映射为中文业务名称。
- 权限影响：不接受模型或前端提供的员工 ID；ERP 端仍以租户、员工和服务凭证验证请求。
- 测试命令与结果：
  - `mvn '-Dtest=HttpEnterpriseSystemAdapterTest' '-Dsurefire.failIfNoSpecifiedTests=false' -pl enterprise -am test`：4 项通过。
  - `mvn '-Dtest=EnterpriseToolsTest' '-Dsurefire.failIfNoSpecifiedTests=false' -pl agent -am test`：9 项通过。
  - `mvn -q test`：全项目 Maven 回归通过。
  - `cd frontend && npm run build`：生产构建通过。
- 已知限制：HTTP ERP 的请假创建、查询、撤回与审批尚未完成端口契约及确认卡对接；在完成前保持不向 HTTP 模式开放。
- 下一阶段准入结论：待专项测试、全量回归和提交完成后允许进入 HTTP 请假确认卡闭环阶段。
