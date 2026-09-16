-- 动态工具目录版本。数据库是多实例共享的权威状态，Redis 仅用于刷新通知。
CREATE TABLE IF NOT EXISTS mcp_catalog_revision (
  tenant_id VARCHAR(64) PRIMARY KEY,
  revision BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMP NOT NULL
);
