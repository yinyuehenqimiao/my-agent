-- v0.3.0: Agent / 意图树表与字段注释（可重复执行，对齐 ragent）

-- ========== t_intent_node ==========
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

-- ========== t_agent_profile ==========
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

-- ========== t_agent_prompt ==========
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

-- ========== t_agent_conversation ==========
COMMENT ON TABLE t_agent_conversation IS 'Agent 会话列表';

COMMENT ON COLUMN t_agent_conversation.id IS '主键ID';
COMMENT ON COLUMN t_agent_conversation.conversation_id IS '会话ID，与 AgentScope sessionId 一致';
COMMENT ON COLUMN t_agent_conversation.user_id IS '用户ID';
COMMENT ON COLUMN t_agent_conversation.title IS '会话标题';
COMMENT ON COLUMN t_agent_conversation.last_time IS '最后活动时间';
COMMENT ON COLUMN t_agent_conversation.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_conversation.update_time IS '更新时间';
COMMENT ON COLUMN t_agent_conversation.deleted IS '是否删除 0：正常 1：删除';

-- ========== t_agent_message ==========
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

-- ========== t_agent_state ==========
COMMENT ON TABLE t_agent_state IS 'AgentScope 工作状态存储，payload 为框架自有编码的不透明 JSON';

COMMENT ON COLUMN t_agent_state.user_id IS '用户ID，匿名会话为 __anon__';
COMMENT ON COLUMN t_agent_state.session_id IS '会话ID，即 AgentScope 的 sessionId';
COMMENT ON COLUMN t_agent_state.state_key IS '状态键，AgentScope 侧固定传 agent_state';
COMMENT ON COLUMN t_agent_state.payload IS '框架自有编码的状态 JSON，业务侧不解析';
COMMENT ON COLUMN t_agent_state.create_time IS '创建时间';
COMMENT ON COLUMN t_agent_state.update_time IS '更新时间';
