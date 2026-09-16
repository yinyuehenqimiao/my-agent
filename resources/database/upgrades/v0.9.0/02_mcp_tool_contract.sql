-- MCP 工具契约快照。重新发现时保留现有工具引用，契约变化必须重新审核。
ALTER TABLE mcp_catalog_tool ADD COLUMN IF NOT EXISTS contract_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE mcp_catalog_tool ADD COLUMN IF NOT EXISTS schema_hash VARCHAR(64);
ALTER TABLE mcp_catalog_tool ADD COLUMN IF NOT EXISTS tool_schema TEXT;
ALTER TABLE mcp_catalog_tool ADD COLUMN IF NOT EXISTS permission_code VARCHAR(128);
ALTER TABLE mcp_catalog_tool ADD COLUMN IF NOT EXISTS read_only BOOLEAN NOT NULL DEFAULT TRUE;
