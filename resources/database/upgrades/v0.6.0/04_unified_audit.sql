-- E3：统一审计查询所需的企业业务租户与链路字段。
-- 默认值兼容升级前的单租户写入逻辑；新链路可逐步显式写入 tenant_id / trace_id。
ALTER TABLE ent_audit
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

ALTER TABLE ent_audit
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_ent_audit_tenant_created
    ON ent_audit (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ent_audit_trace
    ON ent_audit (trace_id);
