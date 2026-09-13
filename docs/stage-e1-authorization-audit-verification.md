# 阶段 E1：租户权限隔离与鉴权审计可靠性

## 本阶段目标

修复权限查询硬编码 `default` 租户造成的跨租户授权风险，并消除测试环境因审计主键定义不一致产生的假告警。

## 红灯与修复

- 新增“相同用户 ID、不同租户”测试后，`tenant-b` 身份错误继承了 `default` 租户的 `workflow:edit` 权限。
- `hasPermission` 现在同时使用服务端身份中的用户 ID 和租户 ID 查询；缺少租户信息时默认拒绝。
- 鉴权审计写入失败由普通警告提升为结构化错误 `security_audit_write_failure`，并维护单调递增的失败计数，供后续健康检查或指标桥接读取。
- 审计库不可用时，`requirePermission` 仍抛出无权限异常，不会因审计异常而放行。
- `KnowledgeAccessTest` 的 H2 `sys_auth_audit.id` 改为与生产 `BIGSERIAL` 等价的自增主键，权限拒绝审计可以真实落库，不再被测试表结构吞掉。

## 定向测试

```powershell
mvn -pl enterprise -am "-Dtest=PolicyDecisionServiceTest,KnowledgeAccessTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：权限测试 12 项、知识访问测试 13 项通过；审计故障测试确认失败计数为 1 且权限保持拒绝。

全量 `mvn test` 九模块 `BUILD SUCCESS`，agent 模块 114 项测试通过。

## 阶段边界

本子阶段只处理鉴权审计和租户权限查询。工具执行审计持久化、统一审计查询页、ERP 调用审计和全链路 `traceId` 将在后续 E 子阶段逐项实现并分别提交。
