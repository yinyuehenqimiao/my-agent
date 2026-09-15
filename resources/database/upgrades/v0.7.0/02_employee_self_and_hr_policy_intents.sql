-- my-agent v0.7.0: 人事叶子意图补齐
-- leave-approval 挂到 group-hr 后，原 group-hr 不再是叶子；补充制度 KB 与本人信息 MCP，
-- 并绑定 employee-self → ReAct，避免“查询我的年假”误入知识库。

INSERT INTO t_intent_node
  (id,intent_code,name,level,parent_code,description,examples,collection_names,kind,sort_order,enabled,deleted)
SELECT '3000000000000000020','group-hr-policy','人事制度',2,'group-hr',
       '查询公司人事、考勤、年假制度与流程说明（非本人余额/非发起请假）',
       '["请假流程是怎样的？","试用期多久转正？","迟到会有什么处罚？","公司年假制度是怎样的"]',
       '[]'::jsonb,0,10,1,0
WHERE NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code='group-hr-policy' AND deleted=0);

INSERT INTO t_intent_node
  (id,intent_code,name,level,parent_code,description,examples,collection_names,kind,sort_order,enabled,deleted)
SELECT '3000000000000000021','employee-self','本人信息查询',2,'group-hr',
       '查询本人员工档案、年假余额、本人请假记录与业务权限等实时个人信息',
       '["查询我的年假","我的年假余额还有几天","查一下我的员工档案","我请过哪些假"]',
       '[]'::jsonb,2,11,1,0
WHERE NOT EXISTS (SELECT 1 FROM t_intent_node WHERE intent_code='employee-self' AND deleted=0);

INSERT INTO wf_route(route_key,intent_code,workflow_def_key,react_config,tenant_id,create_time,update_time)
SELECT 'employee-self','employee-self',NULL,'default','default',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM wf_route WHERE tenant_id='default' AND (route_key='employee-self' OR intent_code='employee-self')
);
