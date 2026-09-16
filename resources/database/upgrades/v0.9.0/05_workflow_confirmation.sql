ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS node_instance_id VARCHAR(128);
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS tool_id VARCHAR(200);
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS tool_version VARCHAR(64);
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS parameter_hash VARCHAR(64);
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;
ALTER TABLE workflow_action ADD COLUMN IF NOT EXISTS lock_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_workflow_action_owner ON workflow_action(tenant_id,user_id,status,expires_at);
