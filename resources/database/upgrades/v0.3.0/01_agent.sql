-- myagent v0.3.0: Agent ReAct engine tables

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

INSERT INTO t_agent_profile (id, name, description, avatar, builtin, active, create_time, update_time, deleted)
VALUES ('2001523723396309001', 'Default Agent', 'myagent default ReAct profile', 'orbit-indigo', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT DO NOTHING;

INSERT INTO t_agent_prompt (id, agent_id, slot_key, content, create_time, update_time, deleted)
VALUES (
  '2001523723396309017',
  '2001523723396309001',
  'AGENT_MAIN',
  '# Role
You are a helpful ReAct agent with tool access. Follow system and tool instructions; use search_knowledge for factual questions about internal docs.

# Tools
Prefer search_knowledge for policy, process, and product questions. Call tools with clear queries; do not invent tool results.

# Answer
Be concise, accurate, and cite knowledge when available.',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
) ON CONFLICT DO NOTHING;

INSERT INTO t_agent_prompt (id, agent_id, slot_key, content, create_time, update_time, deleted)
VALUES (
  '2001523723396309018',
  '2001523723396309001',
  'KNOWLEDGE_TOOL_DESCRIPTION',
  '检索本助手配置的知识库，返回一份已基于命中资料合成的完整答案。

适用：答案写在资料里的问题——规范、制度、流程、操作指南、政策、产品与业务说明等。判断依据是答案能否在资料中找到，而非问题属于哪个领域；用词通用但各家规定可能不同的问题同样适用。不确定是否收录时也调用一次，由检索结果说明。

不适用：某个具体对象的当前状态、某条记录、某个实时数值，改用能查到该对象的业务工具；只处理用户本轮给出的文本、或只靠通用语言能力就能完成的任务。

参数 query：完整、独立、可单独读懂的疑问句，使用用户原语言；指代先替换为明确对象，必要背景一并写入；复合问题整体传入，无需你预先拆成子问题。

返回值：面向用户的成品答案，可能含 Markdown 图片、链接与 HTML 表格；未检索到相关内容时会明确说明这一点。',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
) ON CONFLICT DO NOTHING;
