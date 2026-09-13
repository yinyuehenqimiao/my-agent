-- myagent v0.6.0: 生产路由绑定后台、租户约束与请假流程意图
-- 路由查看、修改分权；修改权限仅授予平台管理员和工作流审核者。

INSERT INTO sys_permission (perm_code, perm_name, category, description) VALUES
    ('route:view', '查看执行管线', 'agent', '查看当前租户的意图与执行管线绑定'),
    ('route:edit', '修改执行管线', 'agent', '创建、修改或删除当前租户的生产路由绑定')
ON CONFLICT (perm_code) DO UPDATE SET
    perm_name = EXCLUDED.perm_name,
    category = EXCLUDED.category,
    description = EXCLUDED.description;

INSERT INTO sys_role_permission (role_code, perm_code) VALUES
    ('PLATFORM_ADMIN', 'route:view'),
    ('PLATFORM_ADMIN', 'route:edit'),
    ('WORKFLOW_EDITOR', 'route:view'),
    ('WORKFLOW_REVIEWER', 'route:view'),
    ('WORKFLOW_REVIEWER', 'route:edit'),
    ('SECURITY_ADMIN', 'route:view'),
    ('AUDITOR', 'route:view')
ON CONFLICT (role_code, perm_code) DO NOTHING;

-- 允许不同租户使用相同的业务路由键，同时禁止同租户重复绑定同一意图。
ALTER TABLE wf_route DROP CONSTRAINT IF EXISTS uk_wf_route_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_route_tenant_key ON wf_route (tenant_id, route_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_wf_route_tenant_intent ON wf_route (tenant_id, intent_code);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code='leave-approval' AND deleted=0) THEN
        INSERT INTO t_intent_node
            (id,intent_code,name,level,parent_code,description,examples,collection_names,kind,sort_order,enabled,deleted)
        VALUES
            ('3000000000000000019','leave-approval','请假申请',2,'group-hr',
             '员工发起年假、事假、病假等请假申请，需要收集日期、类型和原因并进入固定审批流程',
             '["我想请年假","帮我提交请假申请","下周一请一天假"]','[]'::jsonb,3,18,1,0);
    END IF;
END $$;
