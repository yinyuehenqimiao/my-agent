-- v0.8.0：会话级工作流控制状态，独立于 AgentScope 的模型记忆 JSON。
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
COMMENT ON COLUMN t_agent_route_state.tenant_id IS '租户隔离标识';
COMMENT ON COLUMN t_agent_route_state.user_id IS '当前登录用户标识';
COMMENT ON COLUMN t_agent_route_state.conversation_id IS '聊天会话标识';
COMMENT ON COLUMN t_agent_route_state.pipeline_type IS '管线类型：WORKFLOW';
COMMENT ON COLUMN t_agent_route_state.pipeline_target IS '服务端工作流定义键';
COMMENT ON COLUMN t_agent_route_state.status IS 'COLLECTING_PARAMETERS、AWAITING_CONFIRMATION 或终态';
COMMENT ON COLUMN t_agent_route_state.expected_input IS '等待用户补充的槽位名';
COMMENT ON COLUMN t_agent_route_state.action_id IS '已生成的确认动作标识';
COMMENT ON COLUMN t_agent_route_state.workflow_instance_id IS '业务工作流实例标识';
COMMENT ON COLUMN t_agent_route_state.version IS '并发更新版本号';
COMMENT ON COLUMN t_agent_route_state.expires_at IS '续接状态失效时间';
COMMENT ON COLUMN t_agent_route_state.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_route_state.update_time IS '更新时间';
