# 数据库说明

本项目使用 **两个 PostgreSQL 数据库**，不要混用，也不要与 [Ragent](https://github.com/nageoffer/ragent) 的库共用。

| 数据库 | 用途 | 初始化方式 |
| --- | --- | --- |
| `myagent` | 智能体平台：用户、知识库、向量、Agent、工作流、审计、本地企业演示表 | `resources/database/init.ps1` 或 `init.sh` |
| `internal_erp` | 独立 ERP：组织、员工、年假与请假事实数据 | Flyway：`internal-erp/src/main/resources/db/migration` |

另外：

- **pgvector 扩展**只装在 `myagent`，用于 `t_knowledge_vector.embedding`。
- **Redis** 不是业务库，存放登录会话辅助数据、分布式锁、Snowflake 与目录刷新通知，Key 前缀 `myagent:`。
- **MinIO/S3** 存知识库原文件，桶 `myagent-sources` / `myagent-assets`。
- **RocketMQ** 驱动文档分块事务消息，不持久化业务表。
- **Flowable** 启动后会在 `myagent` 中自动创建 `ACT_*` 引擎表，由引擎维护，不要手工改。

## 新库初始化

先启动 PostgreSQL（可用 `deploy/docker-compose.yml`）。容器名默认 `postgres`。

Windows:

```powershell
cd resources/database
.\init.ps1
```

Linux / macOS:

```bash
cd resources/database
chmod +x init.sh
./init.sh
```

脚本会：

1. 创建 `myagent`（已存在则跳过）
2. 执行 `01_schema.sql` 基础表（用户、知识库、摄取、意图、Agent、推理链路骨架）
3. 按版本顺序执行 `upgrades/v0.4.0` 至 `v0.10.0`（企业、RBAC、工作流、MCP、审计、字段注释）

`internal_erp` 在 ERP 服务首次启动时由 Flyway 自动迁移；compose 首次建卷时也会 `CREATE DATABASE internal_erp`。

## 已有库升级

增量脚本均可重复执行（`IF NOT EXISTS` / `ON CONFLICT`）。按 `upgrades/` 目录版本号顺序执行尚未应用的文件。字段注释可单独补齐：

```powershell
Get-Content .\upgrades\v0.10.0\02_column_comments.sql -Raw -Encoding utf8 |
  docker exec -i postgres psql -U postgres -d myagent -v ON_ERROR_STOP=1
```

## `myagent` 表分组

### 系统与权限

| 表 | 做什么 |
| --- | --- |
| `t_user` | 登录账号，密码只存 BCrypt |
| `t_note` | 示例笔记 |
| `sys_role` / `sys_permission` / `sys_user_role` / `sys_role_permission` | 平台 RBAC，与企业审批角色分离 |
| `sys_auth_audit` | 权限允许/拒绝审计 |

### 知识库与入库

| 表 | 做什么 |
| --- | --- |
| `t_knowledge_base` | 知识库及 embedding / collection |
| `t_knowledge_document` | 文档元数据，文件在对象存储 |
| `t_knowledge_chunk` | 切分文本 |
| `t_knowledge_vector` | pgvector 向量 |
| `t_knowledge_document_chunk_log` | 分块任务日志 |
| `t_knowledge_document_schedule` / `_exec` | 远程文档定时刷新 |
| `t_ingestion_pipeline*` / `t_ingestion_task*` | 可编排摄取流水线 |

### Agent 与会话

| 表 | 做什么 |
| --- | --- |
| `t_intent_node` | 树形意图，可绑知识库或 MCP |
| `t_query_term_mapping` | 查询词归一化 |
| `t_agent_profile` / `t_agent_prompt` | 智能体人设与提示词槽 |
| `t_agent_conversation` / `t_agent_message` | 会话与消息 |
| `t_agent_state` | AgentScope 不透明状态 |
| `t_agent_route_state` | 会话当前走哪条工作流，与模型记忆分离 |

### 企业请假（本地演示，adapter=LOCAL）

| 表 | 做什么 |
| --- | --- |
| `ent_employee` | 员工/主管/人事关系 |
| `ent_leave_account` | 年假额度、冻结与已用 |
| `ent_workday` | 工作日历 |
| `ent_leave_request` / `ent_approval_task` | 申请与两级审批任务 |
| `ent_action` | 聊天确认卡 |
| `ent_audit` | 请假业务审计 |
| `ent_kb_grant` / `ent_document_policy` | 知识库授权与制度生效版本 |
| `ent_erp_pending_action` | 调用外部 ERP 前的本地确认/幂等 |

### 工作流、MCP、审计

| 表 | 做什么 |
| --- | --- |
| `wf_definition` / `wf_version` / `wf_review` | 流程定义、版本、审核发布 |
| `wf_route` | 意图绑定到流程或 ReAct |
| `wf_instance` / `wf_node_execution` / `wf_wait_state` | 运行实例、节点、等待插问 |
| `wf_external_reference` | 关联 ERP 单据 |
| `wf_simulation_report` | 发布前模拟门禁 |
| `workflow_action` | 流程内确认卡 |
| `mcp_connector` / `mcp_catalog_tool` / `mcp_intent_binding` | MCP 连接器与已审核工具。`secret_ref` 不存 Token |
| `mcp_catalog_revision` | 运行时工具目录版本 |
| `t_rag_trace_run` / `t_rag_trace_node` | 推理链路 |
| `operation_audit` | 工作流平台操作审计 |

查看注释：

```sql
SELECT obj_description('t_intent_node'::regclass);
SELECT column_name, col_description('t_intent_node'::regclass, ordinal_position)
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 't_intent_node'
ORDER BY ordinal_position;
```

## `internal_erp` 表

| 表 | 做什么 |
| --- | --- |
| `erp_department` | 部门树 |
| `erp_employee` | 员工主数据（工号、入职日、主管、业务角色） |
| `erp_employee_permission` | 员工功能权限 |
| `erp_leave_balance` | 年假余额，带乐观锁 version |
| `erp_leave_request` | 请假事实单据 |
| `erp_idempotency` | 写接口幂等 |
| `erp_operation_audit` | ERP 操作审计 |

字段注释写在 `internal-erp/src/main/resources/db/migration/*.sql`。

## 注释约定

新建或变更表结构时必须补充：

1. `COMMENT ON TABLE ... IS '...';`
2. `COMMENT ON COLUMN ... IS '...';`
3. 枚举/状态在注释中写清取值
