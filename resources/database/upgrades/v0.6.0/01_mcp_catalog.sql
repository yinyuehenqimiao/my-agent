-- MCP connector metadata and discovered tool snapshots. secret_ref is a Vault/env reference, never a secret value.
CREATE TABLE IF NOT EXISTS mcp_connector (
  tenant_id VARCHAR(64) NOT NULL,
  connector_id VARCHAR(100) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  endpoint VARCHAR(1000) NOT NULL,
  secret_ref VARCHAR(300) NOT NULL,
  status VARCHAR(30) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, connector_id)
);
CREATE TABLE IF NOT EXISTS mcp_catalog_tool (
  tenant_id VARCHAR(64) NOT NULL,
  connector_id VARCHAR(100) NOT NULL,
  tool_id VARCHAR(200) NOT NULL,
  title VARCHAR(300),
  description TEXT,
  review_status VARCHAR(30) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  discovered_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, connector_id, tool_id),
  FOREIGN KEY (tenant_id, connector_id) REFERENCES mcp_connector(tenant_id, connector_id)
);
