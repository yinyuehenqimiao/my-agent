-- v0.2.0：为已有 myagent 库增量增加知识库相关表与向量扩展

CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE t_user ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'user';

CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id              VARCHAR(20)  NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    embedding_model VARCHAR(64)  NOT NULL,
    collection_name VARCHAR(64)  NOT NULL,
    created_by      VARCHAR(64)  NOT NULL,
    updated_by      VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_kb_collection_name UNIQUE (collection_name)
);
CREATE INDEX IF NOT EXISTS idx_kb_name ON t_knowledge_base (name);

CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id               VARCHAR(20)   NOT NULL PRIMARY KEY,
    kb_id            VARCHAR(20)   NOT NULL,
    doc_name         VARCHAR(256)  NOT NULL,
    enabled          SMALLINT      NOT NULL DEFAULT 1,
    chunk_count      INTEGER       DEFAULT 0,
    file_url         VARCHAR(1024) NOT NULL,
    file_type        VARCHAR(16)   NOT NULL,
    mime_type        VARCHAR(128),
    file_size        BIGINT,
    process_mode     VARCHAR(16)   DEFAULT 'chunk',
    status           VARCHAR(16)   NOT NULL DEFAULT 'pending',
    source_type      VARCHAR(16),
    source_location  VARCHAR(1024),
    schedule_enabled SMALLINT,
    schedule_cron    VARCHAR(64),
    ingestion_spec   JSONB,
    pipeline_id      VARCHAR(20),
    created_by       VARCHAR(64)   NOT NULL,
    updated_by       VARCHAR(64),
    create_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT      NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_kd_kb_id ON t_knowledge_document (kb_id);

CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id             VARCHAR(20) NOT NULL PRIMARY KEY,
    kb_id          VARCHAR(20) NOT NULL,
    doc_id         VARCHAR(20) NOT NULL,
    chunk_index    INTEGER     NOT NULL,
    content        TEXT        NOT NULL,
    content_hash   VARCHAR(64),
    char_count     INTEGER,
    token_count    INTEGER,
    embedding_text TEXT,
    enabled        SMALLINT    NOT NULL DEFAULT 1,
    created_by     VARCHAR(64) NOT NULL,
    updated_by     VARCHAR(64),
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_kc_doc_id ON t_knowledge_chunk (doc_id);

CREATE TABLE IF NOT EXISTS t_knowledge_document_chunk_log (
    id                 VARCHAR(20) NOT NULL PRIMARY KEY,
    doc_id             VARCHAR(20) NOT NULL,
    status             VARCHAR(16) NOT NULL,
    process_mode       VARCHAR(16),
    parse_profile      VARCHAR(16),
    pipeline_id        VARCHAR(20),
    extract_duration   BIGINT,
    chunk_duration     BIGINT,
    embed_duration     BIGINT,
    persist_duration   BIGINT,
    total_duration     BIGINT,
    chunk_count        INTEGER,
    error_message      TEXT,
    start_time         TIMESTAMP,
    end_time           TIMESTAMP,
    create_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kdcl_doc_id ON t_knowledge_document_chunk_log (doc_id);

CREATE TABLE IF NOT EXISTS t_knowledge_document_schedule (
    id                VARCHAR(20) NOT NULL PRIMARY KEY,
    doc_id            VARCHAR(20) NOT NULL,
    kb_id             VARCHAR(20) NOT NULL,
    cron_expr         VARCHAR(64),
    enabled           SMALLINT DEFAULT 0,
    next_run_time     TIMESTAMP,
    last_run_time     TIMESTAMP,
    last_success_time TIMESTAMP,
    last_status       VARCHAR(16),
    last_error        VARCHAR(512),
    last_etag         VARCHAR(256),
    last_modified     VARCHAR(256),
    last_content_hash VARCHAR(128),
    lock_owner        VARCHAR(128),
    lock_until        TIMESTAMP,
    create_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kds_doc_id UNIQUE (doc_id)
);
CREATE INDEX IF NOT EXISTS idx_kds_next_run ON t_knowledge_document_schedule (next_run_time);
CREATE INDEX IF NOT EXISTS idx_kds_lock_until ON t_knowledge_document_schedule (lock_until);

CREATE TABLE IF NOT EXISTS t_knowledge_document_schedule_exec (
    id            VARCHAR(20) NOT NULL PRIMARY KEY,
    schedule_id   VARCHAR(20) NOT NULL,
    doc_id        VARCHAR(20) NOT NULL,
    kb_id         VARCHAR(20) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    message       VARCHAR(512),
    start_time    TIMESTAMP,
    end_time      TIMESTAMP,
    file_name     VARCHAR(512),
    file_size     BIGINT,
    content_hash  VARCHAR(128),
    etag          VARCHAR(256),
    last_modified VARCHAR(256),
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kdse_schedule_time ON t_knowledge_document_schedule_exec (schedule_id, start_time);
CREATE INDEX IF NOT EXISTS idx_kdse_doc_id ON t_knowledge_document_schedule_exec (doc_id);

-- pgvector 存储（维度需与 embedding 模型一致；默认按 BAAI/bge-m3 = 1024）
CREATE TABLE IF NOT EXISTS t_knowledge_vector (
    id              VARCHAR(20) PRIMARY KEY,
    collection_name VARCHAR(64) NOT NULL,
    content         TEXT,
    metadata        JSONB,
    embedding       vector(1024)
);
CREATE INDEX IF NOT EXISTS idx_kv_collection_name ON t_knowledge_vector (collection_name);
CREATE INDEX IF NOT EXISTS idx_kv_metadata ON t_knowledge_vector USING gin (metadata);
COMMENT ON TABLE t_knowledge_vector IS '知识库向量存储表';
