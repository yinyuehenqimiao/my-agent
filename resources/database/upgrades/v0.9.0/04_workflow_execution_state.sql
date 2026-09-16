CREATE TABLE IF NOT EXISTS wf_instance (
  id VARCHAR(64) PRIMARY KEY, flowable_instance_id VARCHAR(64), tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL, conversation_id VARCHAR(64), definition_key VARCHAR(128) NOT NULL,
  version_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL, business_key VARCHAR(128),
  trace_id VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL, lock_version BIGINT NOT NULL DEFAULT 0,
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_wf_instance_request UNIQUE (tenant_id,user_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_wf_instance_owner ON wf_instance(tenant_id,user_id,conversation_id,status);

CREATE TABLE IF NOT EXISTS wf_node_execution (
  id VARCHAR(64) PRIMARY KEY, instance_id VARCHAR(64) NOT NULL, node_instance_id VARCHAR(128) NOT NULL,
  node_type VARCHAR(64) NOT NULL, attempt INTEGER NOT NULL DEFAULT 1, status VARCHAR(32) NOT NULL,
  input_ref TEXT, output_ref TEXT, error_code VARCHAR(64), trace_id VARCHAR(64) NOT NULL,
  started_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_time TIMESTAMP
);

CREATE TABLE IF NOT EXISTS wf_wait_state (
  id VARCHAR(64) PRIMARY KEY, instance_id VARCHAR(64) NOT NULL, flowable_task_id VARCHAR(64) NOT NULL,
  tenant_id VARCHAR(64) NOT NULL, user_id VARCHAR(64) NOT NULL, conversation_id VARCHAR(64),
  node_instance_id VARCHAR(128) NOT NULL, wait_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL,
  payload TEXT, lock_version BIGINT NOT NULL DEFAULT 0, expires_at TIMESTAMP,
  created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_wf_wait_task UNIQUE(flowable_task_id)
);
CREATE INDEX IF NOT EXISTS idx_wf_wait_owner ON wf_wait_state(tenant_id,user_id,status);

CREATE TABLE IF NOT EXISTS wf_external_reference (
  id VARCHAR(64) PRIMARY KEY, instance_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
  system_code VARCHAR(64) NOT NULL, object_type VARCHAR(64) NOT NULL, object_id VARCHAR(128) NOT NULL,
  latest_status VARCHAR(64), updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_wf_external_ref UNIQUE(tenant_id,system_code,object_type,object_id)
);
