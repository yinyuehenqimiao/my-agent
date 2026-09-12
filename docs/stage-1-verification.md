# 阶段 1 验收记录：平台权限与统一执行身份

验收日期：2026-09-12。
对应计划：`docs/enterprise-agent-workflow-plan.md` 阶段 1。
目标：把当前简单的 `admin/user` 权限升级为平台 RBAC 与企业 ABAC，实现统一执行身份与默认拒绝的权限决策。

## 一、本阶段完成项

- 新增平台 RBAC 表：`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_auth_audit`。
- 平台角色固定为 `PLATFORM_ADMIN`、`WORKFLOW_EDITOR`、`WORKFLOW_REVIEWER`、`SECURITY_ADMIN`、`AUDITOR`、`NORMAL_USER`。
- `EMPLOYEE/MANAGER/HR` 继续作为企业业务属性（来自 `ent_employee`），不混入平台角色。
- 实现统一 `ExecutionIdentity`（租户、用户、平台角色、部门、企业角色、主管、人事、扩展属性）。
- 实现 `PolicyDecisionService`，权限策略默认拒绝，拒绝写审计。
- 后台管理接口由字符串 `admin` 判断迁移为权限码判断。
- 首批权限码：工作流查看/编辑/提交/审核/发布/回滚、工具查看/编辑/测试、策略查看/编辑、审计查看、员工管理、知识授权。
- 编辑者不得审核自己提交的工作流（`requireNotSelfReview`）。
- 系统管理员不自动获得主管或 HR 审批权限（企业角色仅来自员工档案）。
- 所有权限拒绝写入 `sys_auth_audit`。

## 二、实际修改模块

- `resources/database/upgrades/v0.5.0/`：RBAC 表与种子迁移脚本。
- `framework`：新增 `ExecutionIdentity` 接口；`LoginUser` 扩展实现该接口（保留 3 参构造与 builder 兼容）；`UserContext.requireIdentity()`。
- `system`：新增 `auth` 包（RBAC 实体、Mapper、`PolicyDecisionService` 接口与实现、`ExecutionIdentityEnricher` SPI）；更新 `SaTokenStpInterfaceImpl`（从 DB 加载权限/角色）、`UserContextInterceptor`（装配平台角色 + 调用企业富化 SPI）、`UserController`（4 处 `checkRole("admin")` → `employee:manage`）、`MybatisPlusConfiguration`（@MapperScan 增 auth 包）；`pom.xml` 增 `spring-boot-starter-jdbc`、`h2(test)`。
- `enterprise`：新增 `EnterpriseIdentityEnricher`（从 `ent_employee` 填企业属性）；`KnowledgeAccessService` 3 处 `admin` 判断 → `knowledge:authorize`；测试 Config 增 `PolicyDecisionService` bean。
- 测试：新增 `system/PolicyDecisionServiceTest`（10 项）；更新 `KnowledgeAccessTest`（播种 RBAC 表 + 用 policy 构造 service）。

## 三、数据库变化

- 新增 5 张表：`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_auth_audit`（含 3 个索引）。
- 种子：6 个平台角色、14 个权限码、31 条角色-权限映射；现有 `admin` 用户自动赋予 `PLATFORM_ADMIN`。
- 迁移幂等（`IF NOT EXISTS` / `ON CONFLICT DO NOTHING`），已在 `myagent` 库执行验证。

## 四、新增或调整的接口

- 无对外 HTTP 接口新增；`/api/users` 与 `/api/enterprise/grants` 等后台接口鉴权由 `admin` 字符串改为权限码 `employee:manage` / `knowledge:authorize`，响应契约不变。
- 内部 SPI：`ExecutionIdentityEnricher`（system 定义，enterprise 实现）。

## 五、测试命令及结果

| 命令 | 结果 |
| --- | --- |
| `mvn -pl system -am test -B` | BUILD SUCCESS；`PolicyDecisionServiceTest` 10 项全过 |
| `mvn test -B`（全项目） | BUILD SUCCESS；8 模块全过；Tests run 126 项（原 116 + 新增 10），Failures 0，Errors 0，Skipped 0 |
| `mvn -pl bootstrap -am package -DskipTests -B` | 编译通过；repackage 因运行中应用占用 jar 文件锁未完成（环境问题，非代码问题；mvn test 已验证全部主代码编译通过） |
| `npx tsc --noEmit`（frontend） | 通过，零错误 |
| `npm run build`（frontend） | 通过，built in 10.96s，仅大包体积警告 |

阶段 1 专项测试覆盖：
1. 角色与权限码映射（PLATFORM_ADMIN 全权限、WORKFLOW_EDITOR 子集、不含 review）。
2. 无权限/缺失身份默认拒绝；单角色与多角色权限并集。
3. 编辑者自审拦截（`requireNotSelfReview`）。
4. 平台管理员不自动获得企业业务审批角色（企业角色仍为 EMPLOYEE）。
5. 用户停用/角色撤销/角色禁用后实时生效（实时查库，不缓存）。
6. 异步线程身份隔离（子线程身份修改不污染父线程）。
7. 缺失身份 fail-closed。
原有请假事务（30 项）与知识授权（13 项）回归全过。

## 六、已知限制

- `mvn package` 的 repackage 因运行中的应用实例（PID 40716）锁定 `bootstrap-0.0.1-SNAPSHOT.jar` 未完成；停掉该实例后可正常打包。属环境限制，非代码缺陷。
- RBAC 实体与 Mapper（`SysRoleDO` 等）作为阶段 6 管理后台脚手架保留，当前权限决策走 `PolicyDecisionService`（JdbcTemplate），未使用这些 Mapper。
- 平台角色的管理界面（增删改、给用户分配角色）属阶段 6 范围，本阶段仅提供 DB 种子与决策能力。

## 七、Git 提交

- `feat(auth): add platform roles and permission model`
- `feat(auth): add unified enterprise execution identity`
- `test(auth): cover rbac abac and identity isolation`
- `docs(stage-1): record authorization verification`

## 八、下一阶段准入结论

阶段 1 所有后台接口已迁移为权限码判断，越权与自审测试通过，原有请假与知识库权限未退化，强制回归（mvn test / 前端 build / tsc）全部通过。可进入阶段 2（统一 Tool Gateway 与 ERP 适配层）。
