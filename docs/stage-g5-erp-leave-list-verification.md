# 阶段 G5：ERP 本人请假列表闭环验收记录

- 完成项：独立 ERP 增加 `GET /api/erp/v1/leave-requests/me`，仅根据服务认证的租户和员工身份返回本人单据；不接受员工 ID 查询参数。
- my-agent 接入：`EnterpriseSystemPort` 新增请假单摘要查询，`LOCAL` 与 `HTTP_ERP` 均实现；`list_my_leave_requests` 在 HTTP 模式下通过独立 ERP 查询，不再读取旧本地请假表。
- 最小返回：只返回单据标识、开始/结束日期、请假天数和状态，不将请假原因送入工具输出或模型上下文。
- 模式边界：HTTP 模式仍不开放创建、撤回和审批；这些写动作须先实现可持久化确认卡协议，避免绕过用户二次确认。
- 关联 ERP 提交：`internal-erp` 提交 `93d4aa9 feat(erp): expose authenticated leave request list`，同时修复 `EmployeeService` 多构造器导致的 Spring 启动失败。
- 测试命令与结果：
  - `internal-erp: mvn -Dtest=LeaveControllerTest,LeaveServiceTest test`：7 项通过。
  - `internal-erp: mvn -q test`：全量测试通过。
  - `my-agent: mvn -q -Dtest=HttpEnterpriseSystemAdapterTest,EnterpriseToolsTest -Dsurefire.failIfNoSpecifiedTests=false -pl agent,enterprise -am test`：专项测试通过。
  - `my-agent: mvn -q test`：全项目 Maven 回归通过。
  - `my-agent/frontend: npm run build`：生产构建通过。
- 运行验证：ERP 和 my-agent 的真实双服务联调待 Docker Desktop 恢复；当前宿主机 Redis/PostgreSQL 端口不可达，不能据此宣称运行闭环通过。
- 下一阶段准入结论：允许进入持久化确认卡与 HTTP ERP 写操作闭环设计阶段。
