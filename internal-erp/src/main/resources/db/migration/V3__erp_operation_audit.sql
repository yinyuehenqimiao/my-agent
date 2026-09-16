CREATE TABLE erp_operation_audit (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    event VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_erp_operation_audit_tenant_created ON erp_operation_audit (tenant_id, created_at DESC);
CREATE INDEX idx_erp_operation_audit_trace ON erp_operation_audit (trace_id);

COMMENT ON TABLE erp_operation_audit IS 'ERP 写操作审计，供对账与追踪';
COMMENT ON COLUMN erp_operation_audit.id IS '审计 ID';
COMMENT ON COLUMN erp_operation_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_operation_audit.actor_id IS '操作人（员工 ID）';
COMMENT ON COLUMN erp_operation_audit.event IS '事件类型';
COMMENT ON COLUMN erp_operation_audit.target_id IS '目标对象 ID';
COMMENT ON COLUMN erp_operation_audit.trace_id IS '与智能体推理链路对齐的 traceId';
COMMENT ON COLUMN erp_operation_audit.created_at IS '发生时间';
