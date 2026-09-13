# 阶段 E3：统一审计后台与租户隔离

## 阶段目标

将分散在平台操作、权限判定、工具调用和企业业务表中的审计记录汇总到同一个管理页面，并关闭原审计接口未按登录租户过滤的数据越权风险。

## 实现内容

- 新增 `AuditQueryService`，统一返回稳定的 `category`、`eventCode`、操作者、目标、`traceId`、详情和时间字段。
- 按登录身份中的 `tenantId` 分别查询 `operation_audit`、`sys_auth_audit` 和 `ent_audit`，在服务端合并、按时间倒序并统一限制最多 500 条。
- 缺失租户身份时默认拒绝，不回退到全局查询。
- 平台操作与工具调用虽然共用 `operation_audit`，但通过 `TOOL_EXECUTION` 事件划分为独立分类。
- 为 `ent_audit` 增加向后兼容的 `tenant_id`、`trace_id` 和查询索引，旧单租户写入仍使用 `default`。
- 管理后台增加“全部类型、平台操作、权限判定、工具调用、业务流程”中文筛选，并展示中文事件、原始稳定码和链路标识。
- 审计接口继续强制校验 `audit:view`，前端筛选不能绕过后端权限与租户边界。

## 测试流程

### 红灯

先增加跨三个审计表的聚合测试，测试因 `AuditQueryService` 不存在而编译失败，证明原实现不具备统一聚合能力；原控制器 SQL 也未包含 `tenant_id` 条件。

### 定向验证

```powershell
mvn -pl workflow -am "-Dtest=AuditQueryServiceTest,WorkflowAdminControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
cd frontend
npm test -- --run src/pages/admin/workflow/WorkflowAdminPages.test.tsx
npm run build
```

结果：后端 10 项通过，覆盖跨租户隔离、四类合并、分类、总量限制、缺失租户默认拒绝和控制器权限委托；前端 4 项通过，生产构建成功。

### 全量回归

根项目 `mvn test` 九个 Maven 模块全部 `BUILD SUCCESS`；前端完整测试 3 个测试文件、9 项测试全部通过。构建期间仍有项目既存的 Maven 镜像元数据告警、Mockito 动态代理告警和中文控制台乱码，这些不影响本阶段结果，乱码将在后续 UTF-8 子阶段处理。

## 阶段边界

- 本阶段不修改已有企业业务写入代码；新增列的默认值保证旧路径兼容，显式传递业务 `traceId` 将随 ERP HTTP 链路一并完成。
- 日志 UTF-8 和外部接口超时、熔断、幂等重试属于后续稳定性子阶段。
