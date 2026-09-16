# M1：internal-erp MCP 服务验证记录

## 已交付

- `internal-erp` 在配置 `ERP_MCP_JWT_PUBLIC_KEY` 后，通过 Streamable HTTP 在 `/mcp` 提供请假闭环 MCP 工具。
- 工具包括个人档案、入职年限、权限、年假余额、请假单查询、待审批任务，以及提交、撤回、主管审批、人事审批。
- 既有 `/api/erp/v1/**` REST 接口继续保留，便于平台逐步迁移。
- ERP 仅验证平台签发的 RSA 短期 JWT：`sub`（员工）、`tenant_id`、`tool_id`、`trace_id`、`iss`、`aud` 与过期时间；缺失或无效即拒绝。
- 每次 MCP 工具调用都会再次校验 `tool_id` 范围。工具清单可发现不代表可执行。

## 运行配置

```text
ERP_MCP_JWT_PUBLIC_KEY=<Base64 X.509 RSA public key>
ERP_MCP_JWT_ISSUER=my-agent
ERP_MCP_JWT_AUDIENCE=internal-erp
```

私钥不放入 ERP；后续由 `my-agent` 的连接器按租户取得密钥引用并签发短期调用令牌。写工具必须由平台确认卡、幂等键和审计网关驱动，M1 的 MCP 服务不承担模型自主写入授权。

## 验证结果

在 `internal-erp` 执行：

```text
mvn -q -Dtest=ErpMcpProtocolTest test
mvn -q test
```

均已通过。协议测试覆盖 MCP 初始化、工具发现、当前员工年假余额读取，以及令牌工具范围越权拒绝。
