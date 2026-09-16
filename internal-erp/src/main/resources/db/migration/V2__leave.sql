-- 请假余额与申请事实表。智能体只通过 HTTP/MCP 访问，不直连本库。

CREATE TABLE erp_leave_balance (
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    leave_year INTEGER NOT NULL,
    total_days DECIMAL(6,2) NOT NULL,
    used_days DECIMAL(6,2) NOT NULL DEFAULT 0,
    frozen_days DECIMAL(6,2) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, employee_id, leave_year),
    FOREIGN KEY (tenant_id, employee_id) REFERENCES erp_employee(tenant_id, employee_id)
);
COMMENT ON TABLE erp_leave_balance IS '年假额度；提交冻结，通过计入已用，拒绝/撤回释放';
COMMENT ON COLUMN erp_leave_balance.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_leave_balance.employee_id IS '员工 ID';
COMMENT ON COLUMN erp_leave_balance.leave_year IS '年度';
COMMENT ON COLUMN erp_leave_balance.total_days IS '总额度';
COMMENT ON COLUMN erp_leave_balance.used_days IS '已通过占用';
COMMENT ON COLUMN erp_leave_balance.frozen_days IS '审批中冻结';
COMMENT ON COLUMN erp_leave_balance.version IS '乐观锁版本';

CREATE TABLE erp_leave_request (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    employee_id VARCHAR(64) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    leave_days DECIMAL(6,2) NOT NULL,
    reason VARCHAR(1000),
    status VARCHAR(32) NOT NULL,
    manager_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (tenant_id, employee_id) REFERENCES erp_employee(tenant_id, employee_id)
);
COMMENT ON TABLE erp_leave_request IS '请假事实单据';
COMMENT ON COLUMN erp_leave_request.id IS '单据 ID';
COMMENT ON COLUMN erp_leave_request.tenant_id IS '租户 ID';
COMMENT ON COLUMN erp_leave_request.employee_id IS '申请人';
COMMENT ON COLUMN erp_leave_request.start_date IS '开始日期（含）';
COMMENT ON COLUMN erp_leave_request.end_date IS '结束日期（含）';
COMMENT ON COLUMN erp_leave_request.leave_days IS '请假天数';
COMMENT ON COLUMN erp_leave_request.reason IS '原因';
COMMENT ON COLUMN erp_leave_request.status IS '单据状态';
COMMENT ON COLUMN erp_leave_request.manager_id IS '主管';
COMMENT ON COLUMN erp_leave_request.created_at IS '创建时间';
COMMENT ON COLUMN erp_leave_request.updated_at IS '更新时间';

CREATE TABLE erp_idempotency (
    caller_id VARCHAR(64) NOT NULL,
    endpoint VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    resource_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (caller_id, endpoint, idempotency_key)
);
COMMENT ON TABLE erp_idempotency IS '写接口幂等，相同 key 返回同一 resource_id';
COMMENT ON COLUMN erp_idempotency.caller_id IS '调用方，如 my-agent';
COMMENT ON COLUMN erp_idempotency.endpoint IS '接口标识';
COMMENT ON COLUMN erp_idempotency.idempotency_key IS '幂等键';
COMMENT ON COLUMN erp_idempotency.request_hash IS '请求体哈希，冲突则拒绝';
COMMENT ON COLUMN erp_idempotency.resource_id IS '已创建资源 ID';
