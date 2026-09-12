-- myagent v0.5.0: Platform RBAC seed (roles, permissions, role-permission mapping)
-- Idempotent via ON CONFLICT DO NOTHING.

-- ===== 平台角色 =====
INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('PLATFORM_ADMIN',    '平台管理员', '拥有全部平台权限；不自动获得企业业务审批权'),
    ('WORKFLOW_EDITOR',   '工作流编辑者', '编辑/提交工作流；不得审核自己提交的流程'),
    ('WORKFLOW_REVIEWER', '工作流审核者', '审核/发布/回滚工作流'),
    ('SECURITY_ADMIN',    '安全管理员', '维护权限策略与审计查看'),
    ('AUDITOR',           '审计员',     '只读审计与流程'),
    ('NORMAL_USER',       '普通用户',   '无平台管理权限')
ON CONFLICT (role_code) DO NOTHING;

-- ===== 权限码（首批） =====
INSERT INTO sys_permission (perm_code, perm_name, category, description) VALUES
    ('workflow:view',    '工作流查看', 'workflow', '查看工作流定义与版本'),
    ('workflow:edit',    '工作流编辑', 'workflow', '编辑工作流草稿'),
    ('workflow:submit',  '工作流提交', 'workflow', '提交工作流审核'),
    ('workflow:review',  '工作流审核', 'workflow', '审核工作流'),
    ('workflow:publish', '工作流发布', 'workflow', '发布/回滚工作流版本'),
    ('workflow:rollback','工作流回滚', 'workflow', '回滚已发布工作流版本'),
    ('tool:view',        '工具查看',   'tool',     '查看工具与连接器'),
    ('tool:edit',        '工具编辑',   'tool',     '编辑工具配置'),
    ('tool:test',        '工具测试',   'tool',     '在工具中心测试工具'),
    ('policy:view',      '策略查看',   'policy',   '查看权限策略'),
    ('policy:edit',      '策略编辑',   'policy',   '编辑权限策略'),
    ('audit:view',       '审计查看',   'audit',    '查看审计记录'),
    ('employee:manage',  '员工管理',   'employee', '管理平台用户与角色'),
    ('knowledge:authorize','知识授权', 'knowledge','授权知识库访问范围')
ON CONFLICT (perm_code) DO NOTHING;

-- ===== 角色-权限映射 =====
-- PLATFORM_ADMIN 拥有全部平台权限
INSERT INTO sys_role_permission (role_code, perm_code)
SELECT 'PLATFORM_ADMIN', perm_code FROM sys_permission
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- WORKFLOW_EDITOR
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('WORKFLOW_EDITOR','workflow:view'),
    ('WORKFLOW_EDITOR','workflow:edit'),
    ('WORKFLOW_EDITOR','workflow:submit'),
    ('WORKFLOW_EDITOR','tool:view'),
    ('WORKFLOW_EDITOR','tool:test')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- WORKFLOW_REVIEWER
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('WORKFLOW_REVIEWER','workflow:view'),
    ('WORKFLOW_REVIEWER','workflow:review'),
    ('WORKFLOW_REVIEWER','workflow:publish'),
    ('WORKFLOW_REVIEWER','workflow:rollback'),
    ('WORKFLOW_REVIEWER','audit:view')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- SECURITY_ADMIN
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('SECURITY_ADMIN','policy:view'),
    ('SECURITY_ADMIN','policy:edit'),
    ('SECURITY_ADMIN','audit:view'),
    ('SECURITY_ADMIN','tool:view')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- AUDITOR
INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('AUDITOR','audit:view'),
    ('AUDITOR','workflow:view'),
    ('AUDITOR','policy:view')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- NORMAL_USER 无平台管理权限（不插入任何管理权限码）

-- ===== 初始角色分配：现有 admin 用户（t_user.username='admin'）赋予 PLATFORM_ADMIN =====
-- 幂等：仅当该用户尚未拥有 PLATFORM_ADMIN 时插入
INSERT INTO sys_user_role (user_id, role_code)
SELECT u.id, 'PLATFORM_ADMIN'
FROM t_user u
WHERE u.username = 'admin' AND u.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_code = 'PLATFORM_ADMIN'
  )
ON CONFLICT (user_id, role_code) DO NOTHING;
