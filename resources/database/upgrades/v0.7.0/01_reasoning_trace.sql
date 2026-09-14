-- v0.7.0：补齐企业 Agent 推理链路审计存储。
CREATE TABLE IF NOT EXISTS t_rag_trace_run (
    id              VARCHAR(20)  PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL UNIQUE,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    trace_name      VARCHAR(128),
    entry_method    VARCHAR(256),
    conversation_id VARCHAR(64),
    task_id         VARCHAR(64),
    user_id         VARCHAR(64),
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(1000),
    start_time      TIMESTAMP(3) NOT NULL,
    end_time        TIMESTAMP(3),
    duration_ms     BIGINT,
    extra_data      TEXT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0
);

ALTER TABLE t_rag_trace_run
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX IF NOT EXISTS idx_rag_trace_run_tenant_time
    ON t_rag_trace_run (tenant_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_task
    ON t_rag_trace_run (task_id);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_conversation
    ON t_rag_trace_run (conversation_id, start_time DESC);

CREATE TABLE IF NOT EXISTS t_rag_trace_node (
    id              VARCHAR(20)  PRIMARY KEY,
    trace_id        VARCHAR(64)  NOT NULL,
    node_id         VARCHAR(64)  NOT NULL,
    parent_node_id  VARCHAR(64),
    depth           INTEGER      NOT NULL DEFAULT 0,
    node_type       VARCHAR(64),
    node_name       VARCHAR(128),
    class_name      VARCHAR(255),
    method_name     VARCHAR(255),
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    error_message   VARCHAR(1000),
    start_time      TIMESTAMP(3) NOT NULL,
    end_time        TIMESTAMP(3),
    duration_ms     BIGINT,
    extra_data      TEXT,
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_rag_trace_node UNIQUE (trace_id, node_id),
    CONSTRAINT fk_rag_trace_node_run FOREIGN KEY (trace_id) REFERENCES t_rag_trace_run(trace_id)
);
CREATE INDEX IF NOT EXISTS idx_rag_trace_node_timeline
    ON t_rag_trace_node (trace_id, start_time, id);
