-- HTTP ERP 写操作的本地协调记录：不保存 ERP 业务事实，仅保存用户确认与幂等上下文。
CREATE TABLE IF NOT EXISTS ent_erp_pending_action (
 id VARCHAR(64) PRIMARY KEY,
 tenant_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(64) NOT NULL,
 conversation_id VARCHAR(64) NOT NULL,
 action_type VARCHAR(32) NOT NULL CHECK (action_type IN ('CREATE_LEAVE','WITHDRAW_LEAVE')),
 payload TEXT NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL,
 trace_id VARCHAR(128) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','DONE','CANCELLED','EXPIRED')),
 result TEXT,
 expires_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL,
 updated_at TIMESTAMP NOT NULL,
 UNIQUE(tenant_id,user_id,conversation_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS ent_erp_pending_action_owner_idx
 ON ent_erp_pending_action(tenant_id,user_id,conversation_id,status,created_at);
