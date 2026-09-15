-- v0.8.0：增强助手能力发现类问法，避免“有哪些系统工具”退化为固定欢迎语。
UPDATE t_intent_node
SET description = '询问助手身份、可办理事项、已连接系统或当前可用工具',
    examples = '["你是谁","你是做什么的","你能帮我做什么","你都有什么工具可用","我能用你连接哪些系统","有哪些ERP工具","你是什么AI"]',
    update_time = CURRENT_TIMESTAMP
WHERE intent_code = 'sys-about-bot' AND deleted = 0;
