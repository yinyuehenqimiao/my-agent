-- myagent v0.6.0: Workflow runtime tables (Stage 3)
-- Additive migration; uses IF NOT EXISTS for repeatable execution.
-- Flowable ACT_* 表由 Flowable 引擎自管，与本业务表隔离；本脚本只建工作流平台业务表。

-- 工作流定义（业务键唯一，如 leave-approval）
CREATE TABLE IF NOT EXISTS wf_definition (
    id              BIGSERIAL    PRIMARY KEY,
    def_key         VARCHAR(128) NOT NULL,
    name            VARCHAR(128) NOT NULL,
    description     VARCHAR(512),
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    current_published_version_id BIGINT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wf_def_key UNIQUE (def_key)
);
COMMENT ON TABLE wf_definition IS '工作流定义，按业务键唯一';
COMMENT ON COLUMN wf_definition.def_key IS '业务键，如 leave-approval';

-- 工作流版本（每次编辑产生新版本，发布版本不可编辑）
CREATE TABLE IF NOT EXISTS wf_version (
    id              BIGSERIAL    PRIMARY KEY,
    definition_id   BIGINT       NOT NULL,
    version_number  INTEGER      NOT NULL,
    bpmn_xml        TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',  -- DRAFT/IN_REVIEW/APPROVED/PUBLISHED/REJECTED/RETIRED
    created_by      VARCHAR(64)  NOT NULL,
    created_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_time  TIMESTAMP,
    published_by    VARCHAR(64),
    parent_version_id BIGINT,
    CONSTRAINT uk_wf_def_version UNIQUE (definition_id, version_number)
);
CREATE INDEX IF NOT EXISTS idx_wf_version_def ON wf_version (definition_id);
CREATE INDEX IF NOT EXISTS idx_wf_version_status ON wf_version (status);
COMMENT ON TABLE wf_version IS '工作流版本，发布后不可编辑，修改创建新版本';
COMMENT ON COLUMN wf_version.status IS 'DRAFT/IN_REVIEW/APPROVED/PUBLISHED/REJECTED/RETIRED';

-- 版本审核记录（编辑与审核分离，编辑者不得审核自己提交）
CREATE TABLE IF NOT EXISTS wf_review (
    id              BIGSERIAL    PRIMARY KEY,
    version_id      BIGINT       NOT NULL,
    reviewer_id     VARCHAR(64)  NOT NULL,
    action          VARCHAR(16)  NOT NULL,  -- APPROVE/REJECT
    comment         VARCHAR(1000),
    created_time    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_wf_review_version ON wf_review (version_id);

-- 路由绑定（意图码绑定工作流或 ReAct 配置）
CREATE TABLE IF NOT EXISTS wf_route (
    id              BIGSERIAL    PRIMARY KEY,
    route_key       VARCHAR(128) NOT NULL,
    intent_code     VARCHAR(64)  NOT NULL,
    workflow_def_key VARCHAR(128),  -- 绑定的工作流定义键
    react_config    TEXT,            -- ReAct 配置（JSON，可选）
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_wf_route_key UNIQUE (route_key)
);
CREATE INDEX IF NOT EXISTS idx_wf_route_intent ON wf_route (intent_code);
COMMENT ON TABLE wf_route IS '路由绑定，意图码到工作流或 ReAct 的映射';

-- 工作流动作（确认卡，与 ent_action 类似但用于工作流实例）
CREATE TABLE IF NOT EXISTS workflow_action (
    id              VARCHAR(64)  PRIMARY KEY,
    instance_id     VARCHAR(64)  NOT NULL,
    user_id         VARCHAR(64)  NOT NULL,
    action_type     VARCHAR(32)  NOT NULL,
    payload         TEXT         NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    result          TEXT,
    expires_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dedup_key       VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_wf_action_instance ON workflow_action (instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_action_user ON workflow_action (user_id);

-- 操作审计（工作流平台级审计）
CREATE TABLE IF NOT EXISTS operation_audit (
    id              BIGSERIAL    PRIMARY KEY,
    operation        VARCHAR(64)  NOT NULL,
    operator_id      VARCHAR(64),
    target_type      VARCHAR(64),
    target_id        VARCHAR(128),
    trace_id         VARCHAR(64),
    tenant_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    detail           TEXT,
    created_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_op_audit_operator ON operation_audit (operator_id);
CREATE INDEX IF NOT EXISTS idx_op_audit_target ON operation_audit (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_op_audit_time ON operation_audit (created_time);
COMMENT ON TABLE operation_audit IS '工作流平台操作审计';
