-- myagent v0.6.0: 真实路由模拟后台权限
-- 可重复执行；模拟仅使用当前登录人的权限快照，不允许伪造他人身份。

INSERT INTO sys_permission (perm_code, perm_name, category, description)
VALUES ('route:simulate', '路由模拟', 'agent', '使用当前登录身份预览意图识别和路由决策，不执行业务')
ON CONFLICT (perm_code) DO UPDATE SET
    perm_name = EXCLUDED.perm_name,
    category = EXCLUDED.category,
    description = EXCLUDED.description;

INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('PLATFORM_ADMIN', 'route:simulate'),
    ('WORKFLOW_EDITOR', 'route:simulate'),
    ('WORKFLOW_REVIEWER', 'route:simulate'),
    ('SECURITY_ADMIN', 'route:simulate'),
    ('AUDITOR', 'route:simulate')
ON CONFLICT (role_code, perm_code) DO NOTHING;
