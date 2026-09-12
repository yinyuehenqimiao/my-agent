# 数据库脚本约定

## 注释（必须）

新建或变更表结构时，**必须**补充 PostgreSQL 注释，便于 `\d+ 表名` 或客户端查看：

1. 每张表：`COMMENT ON TABLE ... IS '...';`
2. 每个业务字段：`COMMENT ON COLUMN ... IS '...';`
3. 枚举/状态字段在注释中写清取值含义（如 `0：正常 1：删除`）

参考：`upgrades/v0.3.0/04_agent_table_comments.sql`（对齐 ragent `schema_pg.sql`）

## 脚本分层

| 路径 | 用途 |
|------|------|
| `01_schema.sql` | 全量建表 + 注释（新库 init） |
| `02_init_data.sql` | 占位/说明性 init 数据 |
| `upgrades/vX.Y.Z/*.sql` | 增量升级，需可重复执行 |

## v0.3.0 升级顺序（已有库）

```powershell
cd resources/database/upgrades/v0.3.0
Get-Content 01_agent.sql -Raw | docker exec -i postgres psql -U postgres -d myagent
Get-Content 02_intent_tree_seed.sql -Raw | docker exec -i postgres psql -U postgres -d myagent
Get-Content 03_agent_knowledge_tool_prompt.sql -Raw | docker exec -i postgres psql -U postgres -d myagent
Get-Content 04_agent_table_comments.sql -Raw | docker exec -i postgres psql -U postgres -d myagent
Get-Content 05_query_term_mapping.sql -Raw | docker exec -i postgres psql -U postgres -d myagent
```

## 查询注释示例

```sql
-- 表注释
SELECT obj_description('t_intent_node'::regclass);

-- 列注释
SELECT column_name, col_description('t_intent_node'::regclass, ordinal_position)
FROM information_schema.columns
WHERE table_name = 't_intent_node'
ORDER BY ordinal_position;
```
