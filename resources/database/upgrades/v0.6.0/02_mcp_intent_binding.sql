CREATE TABLE IF NOT EXISTS mcp_intent_binding (
  tenant_id VARCHAR(64) NOT NULL,
  intent_code VARCHAR(200) NOT NULL,
  connector_id VARCHAR(100) NOT NULL,
  tool_id VARCHAR(200) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (tenant_id, intent_code),
  FOREIGN KEY (tenant_id, connector_id, tool_id) REFERENCES mcp_catalog_tool(tenant_id, connector_id, tool_id)
);
