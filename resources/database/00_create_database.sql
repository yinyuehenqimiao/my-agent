-- 在默认 postgres 库执行：创建独立库 myagent（不与 ragent 混用）
SELECT 'CREATE DATABASE myagent'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'myagent')\gexec
