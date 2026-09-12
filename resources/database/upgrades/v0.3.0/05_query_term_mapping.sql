-- myagent v0.3.0: 关键词归一化映射表（Query Rewrite 使用）

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
