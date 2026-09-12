-- 默认意图树种子数据（来自 IntentTreeFactory，KB 绑定本地「人事系统知识库」）
-- 可重复执行：已存在的 intent_code 会跳过

DO $$
DECLARE
    kb_id_val VARCHAR(20) := '2092208981865934848';
    coll_name VARCHAR(128) := 'personnelsystem';
    coll_json JSONB := '["personnelsystem"]'::jsonb;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'group' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000001', kb_id_val, 'group', '集团信息化', 0, NULL, NULL, NULL, coll_name, coll_json, 0, 0, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'group-hr' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000002', kb_id_val, 'group-hr', '人事', 1, 'group',
                '招聘、入职、转正、离职、绩效、薪资、考勤、请假等人力资源相关问题',
                '["请假流程是怎样的？","试用期多久转正？","迟到会有什么处罚？"]',
                coll_name, coll_json, 0, 1, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'group-it' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000003', kb_id_val, 'group-it', 'IT支持', 1, 'group',
                'VPN、邮箱、打印机、网络、电脑账号密码、办公软件等 IT 支持相关问题',
                '["电脑打印机怎么连？","公司 VPN 连不上怎么办？","邮箱密码忘了怎么重置？"]',
                coll_name, coll_json, 0, 2, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'group-finance' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000004', kb_id_val, 'group-finance', '财务', 1, 'group',
                '报销、付款、成本中心、预算等财务相关问题',
                '["差旅报销需要哪些资料？"]',
                coll_name, coll_json, 0, 3, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'group-finance-invoice' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000005', kb_id_val, 'group-finance-invoice', '发票相关', 2, 'group-finance',
                '获取公司发票抬头相关信息',
                '["发票抬头有哪些？"]',
                coll_name, coll_json, 0, 4, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000006', kb_id_val, 'biz', '业务系统', 0, NULL, NULL, NULL, coll_name, coll_json, 0, 5, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-oa' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000007', kb_id_val, 'biz-oa', 'OA系统', 1, 'biz',
                'OA 系统相关，例如流程审批、待办、公告、文档中心等',
                '["OA系统主要提供哪些功能？","请假审批在哪个菜单？"]',
                coll_name, coll_json, 0, 6, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-oa-intro' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000008', kb_id_val, 'biz-oa-intro', '系统介绍', 2, 'biz-oa',
                'OA 系统整体功能说明、主要模块、典型使用场景',
                '["OA系统是做什么的？"]',
                coll_name, coll_json, 0, 7, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-oa-security' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000009', kb_id_val, 'biz-oa-security', '数据安全', 2, 'biz-oa',
                'OA系统的数据权限、访问控制、安全审计等相关说明',
                '["OA系统如何控制不同角色的权限？"]',
                coll_name, coll_json, 0, 8, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-ins' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000010', kb_id_val, 'biz-ins', '保险系统', 1, 'biz',
                '保险相关业务系统，如投保、核保、理赔等的功能与架构说明',
                '["保险系统整体架构是怎样的？"]',
                coll_name, coll_json, 0, 9, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-ins-intro' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000011', kb_id_val, 'biz-ins-intro', '系统介绍', 2, 'biz-ins',
                '保险系统业务模块说明与主要流程介绍',
                '["保险系统都包括哪些子系统？"]',
                coll_name, coll_json, 0, 10, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-ins-arch' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000012', kb_id_val, 'biz-ins-arch', '架构设计', 2, 'biz-ins',
                '保险系统的技术架构、服务拆分、数据库设计等',
                '["保险系统是如何做服务拆分的？"]',
                coll_name, coll_json, 0, 11, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'biz-ins-security' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, kb_id, intent_code, name, level, parent_code, description, examples, collection_name, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000013', kb_id_val, 'biz-ins-security', '数据安全', 2, 'biz-ins',
                '保险系统的数据脱敏、权限控制、审计与合规等',
                '["保险系统的敏感信息如何保护？"]',
                coll_name, coll_json, 0, 12, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'sales' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, intent_code, name, level, parent_code, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000014', 'sales', '销售汇总数据统计', 0, NULL, '[]'::jsonb, 2, 13, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'sales-data' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, intent_code, name, level, parent_code, description, examples, mcp_tool_id, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000015', 'sales-data', '销售数据统计', 1, 'sales',
                '销售数据统计，如：销售总额、销售量、销售占比、销售趋势、销售预测等',
                '["销售总额是多少？","销售量是多少？"]',
                'sales_query', '[]'::jsonb, 2, 14, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'sys' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, intent_code, name, level, parent_code, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000016', 'sys', '系统交互', 0, NULL, '[]'::jsonb, 1, 15, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'sys-welcome' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, intent_code, name, level, parent_code, description, examples, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000017', 'sys-welcome', '欢迎与问候', 1, 'sys',
                '用户与助手打招呼，如：你好、早上好、hi、在吗 等',
                '["你好","hello","早上好","在吗","嗨"]',
                '[]'::jsonb, 1, 16, 1, 0);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code = 'sys-about-bot' AND deleted = 0) THEN
        INSERT INTO t_intent_node (id, intent_code, name, level, parent_code, description, examples, collection_names, kind, sort_order, enabled, deleted)
        VALUES ('3000000000000000018', 'sys-about-bot', '关于助手', 1, 'sys',
                '询问助手是做什么的、是谁、能做什么等',
                '["你是谁","你是做什么的","你能帮我做什么","你是什么AI"]',
                '[]'::jsonb, 1, 17, 1, 0);
    END IF;
END $$;
