# internal-erp

独立轻量 ERP，给 `my-agent` 提供员工、组织、年假和请假接口。  
**独立数据库 `internal_erp`、独立认证边界**，不与智能体库共用表。

## 能力

- 组织与员工主数据、司龄、功能权限
- 年假余额（冻结 / 已用 / 乐观锁）
- 请假申请、撤回与操作审计
- HTTP 业务 API + MCP 工具入口（配置了 JWT 公钥后启用 `/mcp`）

业务 HTTP 必须携带：

- `X-ERP-Service-Token`
- `X-Tenant-Id`
- `X-Employee-Id`
- `X-Trace-Id`

`ERP_SERVICE_TOKEN` 为空时拒绝全部业务请求。MCP 只校验智能体签发的 RS256 JWT 公钥，私钥不要放到本服务。

## 数据库

脚本在 `src/main/resources/db/migration`，服务启动时由 **Flyway** 执行：

| 版本 | 文件 | 内容 |
| --- | --- | --- |
| V1 | `V1__organization.sql` | 部门、员工、员工权限 |
| V2 | `V2__leave.sql` | 年假余额、请假单、幂等表 |
| V3 | `V3__erp_operation_audit.sql` | 操作审计 |

字段注释见各 SQL 文件中的 `COMMENT ON`。更完整的库表说明见仓库 [`resources/database/README.md`](../resources/database/README.md)。

## 启动

1. 创建库 `internal_erp`（`deploy/docker-compose.yml` 首次启动会建库）。
2. 复制 `.env.example` 为 `.env`，填写 `YOUR_*` 占位符。
3. 与 `my-agent` 使用同一对 MCP JWT：公钥给本服务，私钥只放智能体。
4. 打包并启动：

```bash
mvn -q -DskipTests package
java -jar target/internal-erp-0.1.0-SNAPSHOT.jar
```

默认端口 `18081`。健康检查：`GET /actuator/health`。

智能体侧将 `ENTERPRISE_ADAPTER=HTTP_ERP`，并配置 `ERP_BASE_URL`、`ERP_SERVICE_TOKEN`。本地只演示请假闭环时可以继续用 `ENTERPRISE_ADAPTER=LOCAL`，不必启动本服务。
