-- 仅在空数据卷首次启动时由 PostgreSQL 容器执行。
-- POSTGRES_DB=myagent，因此本脚本运行在 myagent 库中。
CREATE EXTENSION IF NOT EXISTS vector;

-- 独立 ERP 库：与智能体库隔离，不共享表。
SELECT 'CREATE DATABASE internal_erp'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'internal_erp')\gexec
