-- myagent 业务表结构（在 myagent 库中执行）

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS t_user (
    id           BIGSERIAL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    nickname     VARCHAR(64),
    role         VARCHAR(32)  NOT NULL DEFAULT 'user',
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
COMMENT ON TABLE t_user IS '系统用户';
COMMENT ON COLUMN t_user.password IS 'BCrypt 加密密码';
COMMENT ON COLUMN t_user.role IS '角色：admin/user';

CREATE TABLE IF NOT EXISTS t_note (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    content      TEXT,
    create_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_note_user_time ON t_note (user_id, update_time DESC);
COMMENT ON TABLE t_note IS '用户笔记';

-- Knowledge（对齐 ragent knowledge 部分）
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
COMMENT ON TABLE t_knowledge_base IS '知识库表';

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
COMMENT ON TABLE t_knowledge_document IS '知识库文档表';

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
COMMENT ON TABLE t_knowledge_chunk IS '知识库文档分块表';

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
COMMENT ON TABLE t_knowledge_document_chunk_log IS '知识库文档分块日志表';

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
COMMENT ON TABLE t_knowledge_document_schedule IS '知识库文档定时刷新任务表';

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
COMMENT ON TABLE t_knowledge_document_schedule_exec IS '知识库文档定时刷新执行记录';

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

-- ---------------------------------------------------------------------------
-- Ingestion Pipeline
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_ingestion_pipeline (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    created_by  VARCHAR(20)  DEFAULT '',
    updated_by  VARCHAR(20)  DEFAULT '',
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_name UNIQUE (name, deleted)
);
COMMENT ON TABLE t_ingestion_pipeline IS '摄取流水线表';

CREATE TABLE IF NOT EXISTS t_ingestion_pipeline_node (
    id             VARCHAR(20) NOT NULL PRIMARY KEY,
    pipeline_id    VARCHAR(20) NOT NULL,
    node_id        VARCHAR(20) NOT NULL,
    node_type      VARCHAR(16) NOT NULL,
    next_node_id   VARCHAR(20),
    settings_json  JSONB,
    condition_json JSONB,
    created_by     VARCHAR(20) DEFAULT '',
    updated_by     VARCHAR(20) DEFAULT '',
    create_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        SMALLINT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_ingestion_pipeline_node UNIQUE (pipeline_id, node_id, deleted)
);
CREATE INDEX IF NOT EXISTS idx_ingestion_pipeline_node_pipeline ON t_ingestion_pipeline_node (pipeline_id);
COMMENT ON TABLE t_ingestion_pipeline_node IS '摄取流水线节点表';

CREATE TABLE IF NOT EXISTS t_ingestion_task (
    id               VARCHAR(20)  NOT NULL PRIMARY KEY,
    pipeline_id      VARCHAR(20)  NOT NULL,
    source_type      VARCHAR(20)  NOT NULL,
    source_location  TEXT,
    source_file_name VARCHAR(255),
    status           VARCHAR(16)  NOT NULL,
    chunk_count      INTEGER      DEFAULT 0,
    error_message    TEXT,
    logs_json        JSONB,
    metadata_json    JSONB,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_by       VARCHAR(20)  DEFAULT '',
    updated_by       VARCHAR(20)  DEFAULT '',
    create_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted          SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_pipeline ON t_ingestion_task (pipeline_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_status ON t_ingestion_task (status);
COMMENT ON TABLE t_ingestion_task IS '摄取任务表';

CREATE TABLE IF NOT EXISTS t_ingestion_task_node (
    id            VARCHAR(20) NOT NULL PRIMARY KEY,
    task_id       VARCHAR(20) NOT NULL,
    pipeline_id   VARCHAR(20) NOT NULL,
    node_id       VARCHAR(20) NOT NULL,
    node_type     VARCHAR(16) NOT NULL,
    node_order    INTEGER     NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL,
    duration_ms   BIGINT      NOT NULL DEFAULT 0,
    message       TEXT,
    error_message TEXT,
    output_json   TEXT,
    create_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       SMALLINT    NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_node_task ON t_ingestion_task_node (task_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_node_pipeline ON t_ingestion_task_node (pipeline_id);
CREATE INDEX IF NOT EXISTS idx_ingestion_task_node_status ON t_ingestion_task_node (status);
COMMENT ON TABLE t_ingestion_task_node IS '摄取任务节点表';
-- Agent / intent (ReAct engine)
CREATE TABLE IF NOT EXISTS t_intent_node (
    id                    VARCHAR(20)       NOT NULL PRIMARY KEY,
    kb_id                 VARCHAR(20),
    intent_code           VARCHAR(64)  NOT NULL,
    name                  VARCHAR(64)  NOT NULL,
    level                 SMALLINT     NOT NULL,
    parent_code           VARCHAR(64),
    description           VARCHAR(512),
    examples              TEXT,
    collection_name       VARCHAR(128),
    collection_names      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    top_k                 INTEGER,
    mcp_tool_id           VARCHAR(128),
    kind                  SMALLINT     NOT NULL DEFAULT 0,
    prompt_snippet        TEXT,
    prompt_template       TEXT,
    param_prompt_template TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    enabled               SMALLINT     NOT NULL DEFAULT 1,
    create_by             VARCHAR(20),
    update_by             VARCHAR(20),
    create_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               SMALLINT     NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS t_query_term_mapping (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    domain      VARCHAR(64),
    source_term VARCHAR(128) NOT NULL,
    target_term VARCHAR(128) NOT NULL,
    match_type  SMALLINT     NOT NULL DEFAULT 1,
    priority    INTEGER      NOT NULL DEFAULT 100,
    enabled     SMALLINT     NOT NULL DEFAULT 1,
    remark      VARCHAR(255),
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_query_term_mapping_domain ON t_query_term_mapping (domain);
CREATE INDEX IF NOT EXISTS idx_query_term_mapping_source ON t_query_term_mapping (source_term);

CREATE TABLE IF NOT EXISTS t_agent_profile (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(512),
    avatar      VARCHAR(32),
    builtin     SMALLINT     NOT NULL DEFAULT 0,
    active      SMALLINT     NOT NULL DEFAULT 0,
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_name UNIQUE (name)
);
CREATE INDEX IF NOT EXISTS idx_agent_active ON t_agent_profile (active);

CREATE TABLE IF NOT EXISTS t_agent_prompt (
    id          VARCHAR(20)  NOT NULL PRIMARY KEY,
    agent_id    VARCHAR(20)  NOT NULL,
    slot_key    VARCHAR(64)  NOT NULL,
    content     TEXT,
    create_by   VARCHAR(20),
    update_by   VARCHAR(20),
    create_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     SMALLINT     NOT NULL DEFAULT 0,
    CONSTRAINT uk_agent_slot UNIQUE (agent_id, slot_key)
);
CREATE INDEX IF NOT EXISTS idx_agent_prompt_agent ON t_agent_prompt (agent_id);

CREATE TABLE IF NOT EXISTS t_agent_conversation (
    id              VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(20) NOT NULL,
    user_id         VARCHAR(20) NOT NULL,
    title           VARCHAR(128) NOT NULL,
    last_time       TIMESTAMP,
    create_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted         SMALLINT    DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_conversation_user
    ON t_agent_conversation (conversation_id, user_id)
    WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_agent_conv_user_time ON t_agent_conversation (user_id, last_time);

CREATE TABLE IF NOT EXISTS t_agent_message (
    id                  VARCHAR(20) NOT NULL PRIMARY KEY,
    conversation_id     VARCHAR(20) NOT NULL,
    user_id             VARCHAR(20) NOT NULL,
    role                VARCHAR(16) NOT NULL,
    content             TEXT,
    thinking_content    TEXT,
    blocks              JSONB,
    reply_to_message_id VARCHAR(20),
    message_status      VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    create_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted             SMALLINT    DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_agent_msg_conv ON t_agent_message (conversation_id, user_id, create_time);

CREATE TABLE IF NOT EXISTS t_agent_state (
    user_id     VARCHAR(64) NOT NULL,
    session_id  VARCHAR(64) NOT NULL,
    state_key   VARCHAR(64) NOT NULL,
    payload     JSONB,
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, session_id, state_key)
);

-- Agent / 意图树：表与字段注释（与 upgrades/v0.3.0/04_agent_table_comments.sql 保持一致）
COMMENT ON TABLE t_intent_node IS '意图树节点配置表';
COMMENT ON COLUMN t_intent_node.id IS '主键ID';
COMMENT ON COLUMN t_intent_node.kb_id IS '知识库ID';
COMMENT ON COLUMN t_intent_node.intent_code IS '业务唯一标识，如 group-hr / biz-oa-intro';
COMMENT ON COLUMN t_intent_node.name IS '展示名称';
COMMENT ON COLUMN t_intent_node.level IS '层级 0：DOMAIN 1：CATEGORY 2：TOPIC';
COMMENT ON COLUMN t_intent_node.parent_code IS '父节点 intent_code';
COMMENT ON COLUMN t_intent_node.description IS '语义描述，供意图识别与工具路由';
COMMENT ON COLUMN t_intent_node.examples IS '示例问题 JSON 数组';
COMMENT ON COLUMN t_intent_node.collection_name IS '兼容旧版本单 Collection，后续删除';
COMMENT ON COLUMN t_intent_node.collection_names IS '绑定的知识库 Collection 列表（JSON 数组）';
COMMENT ON COLUMN t_intent_node.top_k IS '节点级检索 TopK，null 表示回退全局默认';
COMMENT ON COLUMN t_intent_node.mcp_tool_id IS 'MCP 工具 ID（kind=2 时使用）';
COMMENT ON COLUMN t_intent_node.kind IS '意图类型 0：KB 1：SYSTEM 2：MCP';
COMMENT ON COLUMN t_intent_node.prompt_snippet IS '提示词片段';
COMMENT ON COLUMN t_intent_node.prompt_template IS '回答生成提示词模板';
COMMENT ON COLUMN t_intent_node.param_prompt_template IS 'MCP 参数提取提示词模板';
COMMENT ON COLUMN t_intent_node.sort_order IS '同级排序，越小越靠前';
COMMENT ON COLUMN t_intent_node.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_intent_node.create_by IS '创建人';
COMMENT ON COLUMN t_intent_node.update_by IS '修改人';
COMMENT ON COLUMN t_intent_node.create_time IS '创建时间';
COMMENT ON COLUMN t_intent_node.update_time IS '修改时间';
COMMENT ON COLUMN t_intent_node.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_query_term_mapping IS '关键词归一化映射表';
COMMENT ON COLUMN t_query_term_mapping.id IS '主键ID';
COMMENT ON COLUMN t_query_term_mapping.domain IS '业务域/系统标识，如 biz、group，可选';
COMMENT ON COLUMN t_query_term_mapping.source_term IS '用户原始短语';
COMMENT ON COLUMN t_query_term_mapping.target_term IS '归一化后的目标短语';
COMMENT ON COLUMN t_query_term_mapping.match_type IS '匹配类型 1：精确 2：前缀 3：正则 4：整词';
COMMENT ON COLUMN t_query_term_mapping.priority IS '优先级，数值越小优先级越高';
COMMENT ON COLUMN t_query_term_mapping.enabled IS '是否启用 1：启用 0：禁用';
COMMENT ON COLUMN t_query_term_mapping.remark IS '备注';
COMMENT ON COLUMN t_query_term_mapping.create_by IS '创建人';
COMMENT ON COLUMN t_query_term_mapping.update_by IS '修改人';
COMMENT ON COLUMN t_query_term_mapping.create_time IS '创建时间';
COMMENT ON COLUMN t_query_term_mapping.update_time IS '修改时间';
COMMENT ON COLUMN t_query_term_mapping.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_agent_profile IS '智能体人设配置表';
COMMENT ON COLUMN t_agent_profile.id IS '主键ID';
COMMENT ON COLUMN t_agent_profile.name IS '智能体名称，唯一';
COMMENT ON COLUMN t_agent_profile.description IS '智能体描述';
COMMENT ON COLUMN t_agent_profile.avatar IS '头像预设标识，取值由前端预设表定义';
COMMENT ON COLUMN t_agent_profile.builtin IS '是否内置 0：否 1：是。内置智能体不可删，空槽位回落终点';
COMMENT ON COLUMN t_agent_profile.active IS '是否激活 0：否 1：是。全局仅允许一条为 1';
COMMENT ON COLUMN t_agent_profile.create_by IS '创建人';
COMMENT ON COLUMN t_agent_profile.update_by IS '更新人';
COMMENT ON COLUMN t_agent_profile.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_profile.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_profile.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_agent_prompt IS '智能体提示词槽位表';
COMMENT ON COLUMN t_agent_prompt.id IS '主键ID';
COMMENT ON COLUMN t_agent_prompt.agent_id IS '所属智能体ID';
COMMENT ON COLUMN t_agent_prompt.slot_key IS '槽位标识，见 AgentPromptSlot 枚举';
COMMENT ON COLUMN t_agent_prompt.content IS '提示词全文，空白视为未配置并回落内置智能体';
COMMENT ON COLUMN t_agent_prompt.create_by IS '创建人';
COMMENT ON COLUMN t_agent_prompt.update_by IS '更新人';
COMMENT ON COLUMN t_agent_prompt.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_prompt.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_prompt.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_agent_conversation IS 'Agent 会话列表';
COMMENT ON COLUMN t_agent_conversation.id IS '主键ID';
COMMENT ON COLUMN t_agent_conversation.conversation_id IS '会话ID，与 AgentScope sessionId 一致';
COMMENT ON COLUMN t_agent_conversation.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_conversation.title IS '会话标题';
COMMENT ON COLUMN t_agent_conversation.last_time IS '最后活动时间';
COMMENT ON COLUMN t_agent_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_conversation.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_agent_message IS 'Agent 消息记录';
COMMENT ON COLUMN t_agent_message.id IS '主键ID';
COMMENT ON COLUMN t_agent_message.conversation_id IS '会话ID';
COMMENT ON COLUMN t_agent_message.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_message.role IS '角色 user：用户 assistant：助手';
COMMENT ON COLUMN t_agent_message.content IS '消息正文';
COMMENT ON COLUMN t_agent_message.thinking_content IS '思考内容';
COMMENT ON COLUMN t_agent_message.blocks IS '运行轨迹块（reasoning/answer/tool 有序序列）';
COMMENT ON COLUMN t_agent_message.reply_to_message_id IS '回复的用户消息ID';
COMMENT ON COLUMN t_agent_message.message_status IS '消息终态 NORMAL：正常 INTERRUPTED：用户中断';
COMMENT ON COLUMN t_agent_message.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_message.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_message.deleted IS '是否删除 0：正常 1：删除';

COMMENT ON TABLE t_agent_state IS 'AgentScope 工作状态存储，payload 为框架自有编码的不透明 JSON';
COMMENT ON COLUMN t_agent_state.user_id IS '用户ID，匿名会话为 __anon__';
COMMENT ON COLUMN t_agent_state.session_id IS '会话ID，即 AgentScope 的 sessionId';
COMMENT ON COLUMN t_agent_state.state_key IS '状态键，AgentScope 侧固定传 agent_state';
COMMENT ON COLUMN t_agent_state.payload IS '框架自有编码的状态 JSON，业务侧不解析';
COMMENT ON COLUMN t_agent_state.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_state.update_time IS '更新时间';
-- 推理链路运行记录；tenant_id 是查询与授权的强制隔离边界。
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
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_tenant_time
    ON t_rag_trace_run (tenant_id, start_time DESC);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_task
    ON t_rag_trace_run (task_id);
CREATE INDEX IF NOT EXISTS idx_rag_trace_run_conversation
    ON t_rag_trace_run (conversation_id, start_time DESC);

-- 推理链路节点。节点不单独保存租户，查询时必须关联运行记录校验 tenant_id。
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

-- 会话执行管线控制状态；与 AgentScope 模型记忆分离，供路由层稳定续接固定流程。
CREATE TABLE IF NOT EXISTS t_agent_route_state (
    tenant_id           VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(64)  NOT NULL,
    conversation_id     VARCHAR(64)  NOT NULL,
    pipeline_type       VARCHAR(16)  NOT NULL,
    pipeline_target     VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    expected_input      VARCHAR(64),
    action_id           VARCHAR(64),
    workflow_instance_id VARCHAR(64),
    version             BIGINT       NOT NULL DEFAULT 0,
    expires_at          TIMESTAMP(3) NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, user_id, conversation_id)
);
CREATE INDEX IF NOT EXISTS idx_agent_route_state_active
    ON t_agent_route_state (tenant_id, user_id, status, expires_at);
COMMENT ON TABLE t_agent_route_state IS '会话当前执行管线的显式控制状态，不存储模型上下文';
