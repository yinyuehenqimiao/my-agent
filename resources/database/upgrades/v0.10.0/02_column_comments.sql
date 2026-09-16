-- 为全部业务表补齐 PostgreSQL 注释（可重复执行，COMMENT ON 会覆盖旧注释）。
-- Flowable 引擎表 ACT_* 由引擎自行维护，不在此注释。

-- ---------------------------------------------------------------------------
-- 系统账号
-- ---------------------------------------------------------------------------
COMMENT ON TABLE t_user IS '平台登录用户，密码仅存 BCrypt 哈希';
COMMENT ON COLUMN t_user.id IS '用户主键';
COMMENT ON COLUMN t_user.username IS '登录名，全局唯一';
COMMENT ON COLUMN t_user.password IS 'BCrypt 加密密码，禁止存明文';
COMMENT ON COLUMN t_user.nickname IS '展示昵称';
COMMENT ON COLUMN t_user.role IS '兼容旧字段：admin/user；细粒度权限以 sys_role 为准';
COMMENT ON COLUMN t_user.create_time IS '创建时间';
COMMENT ON COLUMN t_user.update_time IS '更新时间';
COMMENT ON COLUMN t_user.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_note IS '用户笔记（示例业务表）';
COMMENT ON COLUMN t_note.id IS '笔记主键';
COMMENT ON COLUMN t_note.user_id IS '所属用户 ID';
COMMENT ON COLUMN t_note.title IS '标题';
COMMENT ON COLUMN t_note.content IS '正文';
COMMENT ON COLUMN t_note.create_time IS '创建时间';
COMMENT ON COLUMN t_note.update_time IS '更新时间';
COMMENT ON COLUMN t_note.deleted IS '逻辑删除 0：正常 1：删除';

-- ---------------------------------------------------------------------------
-- 知识库与向量
-- ---------------------------------------------------------------------------
COMMENT ON TABLE t_knowledge_base IS '知识库；每个库对应一个 pgvector collection';
COMMENT ON COLUMN t_knowledge_base.id IS '知识库 ID（雪花）';
COMMENT ON COLUMN t_knowledge_base.name IS '知识库名称';
COMMENT ON COLUMN t_knowledge_base.embedding_model IS '入库使用的 Embedding 模型 ID';
COMMENT ON COLUMN t_knowledge_base.collection_name IS '向量集合名，对应 t_knowledge_vector.collection_name';
COMMENT ON COLUMN t_knowledge_base.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_base.updated_by IS '最后修改人';
COMMENT ON COLUMN t_knowledge_base.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_base.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_base.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_knowledge_document IS '知识库文档元数据；正文文件在对象存储中';
COMMENT ON COLUMN t_knowledge_document.id IS '文档 ID';
COMMENT ON COLUMN t_knowledge_document.kb_id IS '所属知识库 ID';
COMMENT ON COLUMN t_knowledge_document.doc_name IS '原始文件名';
COMMENT ON COLUMN t_knowledge_document.enabled IS '是否参与检索 1：启用 0：停用';
COMMENT ON COLUMN t_knowledge_document.chunk_count IS '已生成分块数';
COMMENT ON COLUMN t_knowledge_document.file_url IS '对象存储地址';
COMMENT ON COLUMN t_knowledge_document.file_type IS '扩展名，如 pdf/md/docx';
COMMENT ON COLUMN t_knowledge_document.mime_type IS 'MIME 类型';
COMMENT ON COLUMN t_knowledge_document.file_size IS '文件字节数';
COMMENT ON COLUMN t_knowledge_document.process_mode IS '处理模式，默认 chunk';
COMMENT ON COLUMN t_knowledge_document.status IS 'pending/processing/ready/failed';
COMMENT ON COLUMN t_knowledge_document.source_type IS '来源类型：upload/url 等';
COMMENT ON COLUMN t_knowledge_document.source_location IS '远程源地址（定时刷新用）';
COMMENT ON COLUMN t_knowledge_document.schedule_enabled IS '是否开启定时刷新';
COMMENT ON COLUMN t_knowledge_document.schedule_cron IS '刷新 Cron 表达式';
COMMENT ON COLUMN t_knowledge_document.ingestion_spec IS '入库流水线参数 JSON';
COMMENT ON COLUMN t_knowledge_document.pipeline_id IS '使用的摄取流水线 ID';
COMMENT ON COLUMN t_knowledge_document.created_by IS '上传人';
COMMENT ON COLUMN t_knowledge_document.updated_by IS '最后修改人';
COMMENT ON COLUMN t_knowledge_document.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_document.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_knowledge_chunk IS '文档切分后的文本块，供检索拼装上下文';
COMMENT ON COLUMN t_knowledge_chunk.id IS '分块 ID，通常与向量表主键一致';
COMMENT ON COLUMN t_knowledge_chunk.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_knowledge_chunk.doc_id IS '文档 ID';
COMMENT ON COLUMN t_knowledge_chunk.chunk_index IS '文档内顺序，从 0 开始';
COMMENT ON COLUMN t_knowledge_chunk.content IS '分块正文';
COMMENT ON COLUMN t_knowledge_chunk.content_hash IS '正文哈希，用于去重';
COMMENT ON COLUMN t_knowledge_chunk.char_count IS '字符数';
COMMENT ON COLUMN t_knowledge_chunk.token_count IS '估算 token 数';
COMMENT ON COLUMN t_knowledge_chunk.embedding_text IS '实际送入向量模型的文本';
COMMENT ON COLUMN t_knowledge_chunk.enabled IS '是否参与检索 1：启用 0：停用';
COMMENT ON COLUMN t_knowledge_chunk.created_by IS '创建人';
COMMENT ON COLUMN t_knowledge_chunk.updated_by IS '修改人';
COMMENT ON COLUMN t_knowledge_chunk.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_chunk.update_time IS '更新时间';
COMMENT ON COLUMN t_knowledge_chunk.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_knowledge_document_chunk_log IS '单次分块任务的耗时与结果日志';
COMMENT ON COLUMN t_knowledge_document_chunk_log.id IS '日志 ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.doc_id IS '文档 ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.status IS '任务状态';
COMMENT ON COLUMN t_knowledge_document_chunk_log.process_mode IS '处理模式';
COMMENT ON COLUMN t_knowledge_document_chunk_log.parse_profile IS '解析配置档';
COMMENT ON COLUMN t_knowledge_document_chunk_log.pipeline_id IS '流水线 ID';
COMMENT ON COLUMN t_knowledge_document_chunk_log.extract_duration IS '解析耗时毫秒';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_duration IS '切分耗时毫秒';
COMMENT ON COLUMN t_knowledge_document_chunk_log.embed_duration IS '向量化耗时毫秒';
COMMENT ON COLUMN t_knowledge_document_chunk_log.persist_duration IS '落库耗时毫秒';
COMMENT ON COLUMN t_knowledge_document_chunk_log.total_duration IS '总耗时毫秒';
COMMENT ON COLUMN t_knowledge_document_chunk_log.chunk_count IS '生成分块数';
COMMENT ON COLUMN t_knowledge_document_chunk_log.error_message IS '失败原因';
COMMENT ON COLUMN t_knowledge_document_chunk_log.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.create_time IS '记录创建时间';
COMMENT ON COLUMN t_knowledge_document_chunk_log.update_time IS '记录更新时间';

COMMENT ON TABLE t_knowledge_document_schedule IS '远程文档定时刷新任务';
COMMENT ON COLUMN t_knowledge_document_schedule.id IS '任务 ID';
COMMENT ON COLUMN t_knowledge_document_schedule.doc_id IS '文档 ID';
COMMENT ON COLUMN t_knowledge_document_schedule.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_knowledge_document_schedule.cron_expr IS 'Cron 表达式';
COMMENT ON COLUMN t_knowledge_document_schedule.enabled IS '是否启用 1：启用 0：停用';
COMMENT ON COLUMN t_knowledge_document_schedule.next_run_time IS '下次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_run_time IS '上次执行时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_success_time IS '上次成功时间';
COMMENT ON COLUMN t_knowledge_document_schedule.last_status IS '上次状态';
COMMENT ON COLUMN t_knowledge_document_schedule.last_error IS '上次错误摘要';
COMMENT ON COLUMN t_knowledge_document_schedule.last_etag IS '远程 ETag，用于增量判断';
COMMENT ON COLUMN t_knowledge_document_schedule.last_modified IS '远程 Last-Modified';
COMMENT ON COLUMN t_knowledge_document_schedule.last_content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_owner IS '分布式锁持有者';
COMMENT ON COLUMN t_knowledge_document_schedule.lock_until IS '锁到期时间';
COMMENT ON COLUMN t_knowledge_document_schedule.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule.update_time IS '更新时间';

COMMENT ON TABLE t_knowledge_document_schedule_exec IS '定时刷新的每次执行记录';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.id IS '执行记录 ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.schedule_id IS '任务 ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.doc_id IS '文档 ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.kb_id IS '知识库 ID';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.status IS '执行状态';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.message IS '结果说明';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.start_time IS '开始时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.end_time IS '结束时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_name IS '拉取到的文件名';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.file_size IS '文件大小';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.content_hash IS '内容哈希';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.etag IS '远程 ETag';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.last_modified IS '远程 Last-Modified';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.create_time IS '创建时间';
COMMENT ON COLUMN t_knowledge_document_schedule_exec.update_time IS '更新时间';

COMMENT ON TABLE t_knowledge_vector IS 'pgvector 向量存储，维度需与 Embedding 模型一致（默认 1024）';
COMMENT ON COLUMN t_knowledge_vector.id IS '与分块 ID 对齐的主键';
COMMENT ON COLUMN t_knowledge_vector.collection_name IS '知识库集合名';
COMMENT ON COLUMN t_knowledge_vector.content IS '检索命中时回显的文本';
COMMENT ON COLUMN t_knowledge_vector.metadata IS '来源、文档、分块等元数据 JSON';
COMMENT ON COLUMN t_knowledge_vector.embedding IS '向量，默认 vector(1024)，度量 COSINE';

-- ---------------------------------------------------------------------------
-- 文档摄取流水线
-- ---------------------------------------------------------------------------
COMMENT ON TABLE t_ingestion_pipeline IS '可编排的文档摄取流水线定义';
COMMENT ON COLUMN t_ingestion_pipeline.id IS '流水线 ID';
COMMENT ON COLUMN t_ingestion_pipeline.name IS '名称，未删除范围内唯一';
COMMENT ON COLUMN t_ingestion_pipeline.description IS '说明';
COMMENT ON COLUMN t_ingestion_pipeline.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline.updated_by IS '修改人';
COMMENT ON COLUMN t_ingestion_pipeline.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_ingestion_pipeline_node IS '流水线节点（解析/切分/向量化等）';
COMMENT ON COLUMN t_ingestion_pipeline_node.id IS '记录 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.pipeline_id IS '流水线 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_id IS '节点业务 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_pipeline_node.next_node_id IS '后继节点 ID';
COMMENT ON COLUMN t_ingestion_pipeline_node.settings_json IS '节点配置';
COMMENT ON COLUMN t_ingestion_pipeline_node.condition_json IS '分支条件';
COMMENT ON COLUMN t_ingestion_pipeline_node.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_pipeline_node.updated_by IS '修改人';
COMMENT ON COLUMN t_ingestion_pipeline_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_pipeline_node.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_ingestion_task IS '一次文档摄取任务';
COMMENT ON COLUMN t_ingestion_task.id IS '任务 ID';
COMMENT ON COLUMN t_ingestion_task.pipeline_id IS '流水线 ID';
COMMENT ON COLUMN t_ingestion_task.source_type IS '来源类型';
COMMENT ON COLUMN t_ingestion_task.source_location IS '来源位置';
COMMENT ON COLUMN t_ingestion_task.source_file_name IS '源文件名';
COMMENT ON COLUMN t_ingestion_task.status IS '任务状态';
COMMENT ON COLUMN t_ingestion_task.chunk_count IS '产出分块数';
COMMENT ON COLUMN t_ingestion_task.error_message IS '失败原因';
COMMENT ON COLUMN t_ingestion_task.logs_json IS '运行日志';
COMMENT ON COLUMN t_ingestion_task.metadata_json IS '任务元数据';
COMMENT ON COLUMN t_ingestion_task.started_at IS '开始时间';
COMMENT ON COLUMN t_ingestion_task.completed_at IS '完成时间';
COMMENT ON COLUMN t_ingestion_task.created_by IS '创建人';
COMMENT ON COLUMN t_ingestion_task.updated_by IS '修改人';
COMMENT ON COLUMN t_ingestion_task.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_ingestion_task_node IS '摄取任务中每个节点的执行记录';
COMMENT ON COLUMN t_ingestion_task_node.id IS '记录 ID';
COMMENT ON COLUMN t_ingestion_task_node.task_id IS '任务 ID';
COMMENT ON COLUMN t_ingestion_task_node.pipeline_id IS '流水线 ID';
COMMENT ON COLUMN t_ingestion_task_node.node_id IS '节点业务 ID';
COMMENT ON COLUMN t_ingestion_task_node.node_type IS '节点类型';
COMMENT ON COLUMN t_ingestion_task_node.node_order IS '执行顺序';
COMMENT ON COLUMN t_ingestion_task_node.status IS '节点状态';
COMMENT ON COLUMN t_ingestion_task_node.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_ingestion_task_node.message IS '说明';
COMMENT ON COLUMN t_ingestion_task_node.error_message IS '失败原因';
COMMENT ON COLUMN t_ingestion_task_node.output_json IS '节点输出';
COMMENT ON COLUMN t_ingestion_task_node.create_time IS '创建时间';
COMMENT ON COLUMN t_ingestion_task_node.update_time IS '更新时间';
COMMENT ON COLUMN t_ingestion_task_node.deleted IS '逻辑删除 0：正常 1：删除';

-- ---------------------------------------------------------------------------
-- 推理链路
-- ---------------------------------------------------------------------------
COMMENT ON TABLE t_rag_trace_run IS '一次用户请求的推理链路总览，tenant_id 是查询隔离边界';
COMMENT ON COLUMN t_rag_trace_run.id IS '记录主键';
COMMENT ON COLUMN t_rag_trace_run.trace_id IS '对外展示的链路 ID，唯一';
COMMENT ON COLUMN t_rag_trace_run.tenant_id IS '租户 ID，当前单租户默认 default';
COMMENT ON COLUMN t_rag_trace_run.trace_name IS '链路标题，通常取用户问题摘要';
COMMENT ON COLUMN t_rag_trace_run.entry_method IS '入口方法或路由';
COMMENT ON COLUMN t_rag_trace_run.conversation_id IS '会话 ID';
COMMENT ON COLUMN t_rag_trace_run.task_id IS '任务 ID';
COMMENT ON COLUMN t_rag_trace_run.user_id IS '发起用户';
COMMENT ON COLUMN t_rag_trace_run.status IS 'RUNNING/SUCCESS/ERROR/CANCELLED';
COMMENT ON COLUMN t_rag_trace_run.error_message IS '失败摘要，避免写入完整敏感原文';
COMMENT ON COLUMN t_rag_trace_run.start_time IS '开始时间（毫秒精度）';
COMMENT ON COLUMN t_rag_trace_run.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_run.duration_ms IS '总耗时毫秒';
COMMENT ON COLUMN t_rag_trace_run.extra_data IS '扩展 JSON，如模型、token 统计';
COMMENT ON COLUMN t_rag_trace_run.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_run.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_run.deleted IS '逻辑删除 0：正常 1：删除';

COMMENT ON TABLE t_rag_trace_node IS '推理链路节点：模型路由、工具调用、检索、工作流步骤等';
COMMENT ON COLUMN t_rag_trace_node.id IS '节点记录主键';
COMMENT ON COLUMN t_rag_trace_node.trace_id IS '所属链路 ID';
COMMENT ON COLUMN t_rag_trace_node.node_id IS '链路内节点 ID';
COMMENT ON COLUMN t_rag_trace_node.parent_node_id IS '父节点 ID';
COMMENT ON COLUMN t_rag_trace_node.depth IS '树深度，0 为根';
COMMENT ON COLUMN t_rag_trace_node.node_type IS '节点类型，如 LLM/TOOL/RETRIEVE';
COMMENT ON COLUMN t_rag_trace_node.node_name IS '展示名称';
COMMENT ON COLUMN t_rag_trace_node.class_name IS '实现类名';
COMMENT ON COLUMN t_rag_trace_node.method_name IS '方法名';
COMMENT ON COLUMN t_rag_trace_node.status IS 'RUNNING/SUCCESS/ERROR/CANCELLED';
COMMENT ON COLUMN t_rag_trace_node.error_message IS '失败摘要';
COMMENT ON COLUMN t_rag_trace_node.start_time IS '开始时间';
COMMENT ON COLUMN t_rag_trace_node.end_time IS '结束时间';
COMMENT ON COLUMN t_rag_trace_node.duration_ms IS '耗时毫秒';
COMMENT ON COLUMN t_rag_trace_node.extra_data IS '输入输出摘要 JSON';
COMMENT ON COLUMN t_rag_trace_node.create_time IS '创建时间';
COMMENT ON COLUMN t_rag_trace_node.update_time IS '更新时间';
COMMENT ON COLUMN t_rag_trace_node.deleted IS '逻辑删除 0：正常 1：删除';

-- ---------------------------------------------------------------------------
-- 平台 RBAC
-- ---------------------------------------------------------------------------
COMMENT ON COLUMN sys_role.id IS '角色主键';
COMMENT ON COLUMN sys_role.role_code IS '角色码：PLATFORM_ADMIN/WORKFLOW_EDITOR/WORKFLOW_REVIEWER/SECURITY_ADMIN/AUDITOR/NORMAL_USER';
COMMENT ON COLUMN sys_role.role_name IS '角色名称';
COMMENT ON COLUMN sys_role.description IS '说明；平台管理员不自动获得企业审批权';
COMMENT ON COLUMN sys_role.enabled IS '是否启用';
COMMENT ON COLUMN sys_role.tenant_id IS '租户 ID';
COMMENT ON COLUMN sys_role.create_time IS '创建时间';
COMMENT ON COLUMN sys_role.update_time IS '更新时间';

COMMENT ON COLUMN sys_permission.id IS '权限主键';
COMMENT ON COLUMN sys_permission.perm_code IS '权限码，如 workflow:publish、audit:view';
COMMENT ON COLUMN sys_permission.perm_name IS '权限名称';
COMMENT ON COLUMN sys_permission.description IS '说明';
COMMENT ON COLUMN sys_permission.category IS '分组：workflow/tool/policy/audit/employee/knowledge/agent';
COMMENT ON COLUMN sys_permission.enabled IS '是否启用';
COMMENT ON COLUMN sys_permission.create_time IS '创建时间';
COMMENT ON COLUMN sys_permission.update_time IS '更新时间';

COMMENT ON COLUMN sys_user_role.id IS '关联主键';
COMMENT ON COLUMN sys_user_role.user_id IS 't_user.id';
COMMENT ON COLUMN sys_user_role.role_code IS '平台角色码';
COMMENT ON COLUMN sys_user_role.tenant_id IS '租户 ID';
COMMENT ON COLUMN sys_user_role.create_time IS '创建时间';

COMMENT ON COLUMN sys_role_permission.id IS '关联主键';
COMMENT ON COLUMN sys_role_permission.role_code IS '角色码';
COMMENT ON COLUMN sys_role_permission.perm_code IS '权限码';
COMMENT ON COLUMN sys_role_permission.create_time IS '创建时间';

COMMENT ON COLUMN sys_auth_audit.id IS '鉴权审计主键';
COMMENT ON COLUMN sys_auth_audit.user_id IS '用户 ID';
COMMENT ON COLUMN sys_auth_audit.username IS '用户名';
COMMENT ON COLUMN sys_auth_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN sys_auth_audit.perm_code IS '被检查的权限码';
COMMENT ON COLUMN sys_auth_audit.action IS 'ALLOW/DENY 等';
COMMENT ON COLUMN sys_auth_audit.reason IS '原因说明';
COMMENT ON COLUMN sys_auth_audit.trace_id IS '关联推理/请求链路';
COMMENT ON COLUMN sys_auth_audit.create_time IS '发生时间';

-- ---------------------------------------------------------------------------
-- 企业请假（本地演示引擎，与 internal_erp 库隔离）
-- ---------------------------------------------------------------------------
COMMENT ON TABLE ent_employee IS '企业员工身份，与平台账号 user_id 对齐';
COMMENT ON COLUMN ent_employee.user_id IS '对应用户 ID 或用户名';
COMMENT ON COLUMN ent_employee.display_name IS '展示姓名';
COMMENT ON COLUMN ent_employee.department IS '部门';
COMMENT ON COLUMN ent_employee.business_role IS 'EMPLOYEE/MANAGER/HR';
COMMENT ON COLUMN ent_employee.manager_id IS '直属主管 user_id';
COMMENT ON COLUMN ent_employee.hr_id IS '人事审批人 user_id';
COMMENT ON COLUMN ent_employee.active IS '是否在职';

COMMENT ON TABLE ent_leave_account IS '年假额度账户，按自然年';
COMMENT ON COLUMN ent_leave_account.user_id IS '员工';
COMMENT ON COLUMN ent_leave_account.year IS '年度';
COMMENT ON COLUMN ent_leave_account.total IS '总额度（天）';
COMMENT ON COLUMN ent_leave_account.frozen IS '审批中冻结额度';
COMMENT ON COLUMN ent_leave_account.used IS '已通过占用额度';

COMMENT ON TABLE ent_workday IS '工作日历，覆盖默认周一至周五';
COMMENT ON COLUMN ent_workday.day IS '日期';
COMMENT ON COLUMN ent_workday.working IS '是否工作日';
COMMENT ON COLUMN ent_workday.description IS '节假日/调休说明';

COMMENT ON TABLE ent_leave_request IS '请假申请单';
COMMENT ON COLUMN ent_leave_request.id IS '申请 ID';
COMMENT ON COLUMN ent_leave_request.user_id IS '申请人';
COMMENT ON COLUMN ent_leave_request.leave_type IS 'ANNUAL 年假 / PERSONAL 事假';
COMMENT ON COLUMN ent_leave_request.start_date IS '开始日期（含）';
COMMENT ON COLUMN ent_leave_request.end_date IS '结束日期（含）';
COMMENT ON COLUMN ent_leave_request.days IS '工作日天数';
COMMENT ON COLUMN ent_leave_request.reason IS '请假原因';
COMMENT ON COLUMN ent_leave_request.status IS 'DRAFT/PENDING_MANAGER/PENDING_HR/APPROVED/REJECTED/WITHDRAWN';
COMMENT ON COLUMN ent_leave_request.manager_id IS '主管审批人，禁止等于申请人';
COMMENT ON COLUMN ent_leave_request.hr_id IS '人事审批人，禁止与主管或申请人相同';
COMMENT ON COLUMN ent_leave_request.created_at IS '创建时间';
COMMENT ON COLUMN ent_leave_request.updated_at IS '更新时间';

COMMENT ON TABLE ent_approval_task IS '请假审批任务';
COMMENT ON COLUMN ent_approval_task.id IS '任务 ID';
COMMENT ON COLUMN ent_approval_task.request_id IS '请假申请 ID';
COMMENT ON COLUMN ent_approval_task.assignee_id IS '审批人';
COMMENT ON COLUMN ent_approval_task.stage IS 'MANAGER/HR';
COMMENT ON COLUMN ent_approval_task.status IS '任务状态';

COMMENT ON TABLE ent_action IS '聊天确认卡：写操作必须用户确认后才落库';
COMMENT ON COLUMN ent_action.id IS '动作 ID';
COMMENT ON COLUMN ent_action.user_id IS '发起人';
COMMENT ON COLUMN ent_action.conversation_id IS '会话 ID';
COMMENT ON COLUMN ent_action.kind IS '动作类型';
COMMENT ON COLUMN ent_action.request_id IS '关联请假单';
COMMENT ON COLUMN ent_action.payload IS '冻结的业务参数 JSON';
COMMENT ON COLUMN ent_action.status IS 'PENDING/DONE/CANCELLED/EXPIRED';
COMMENT ON COLUMN ent_action.result IS '执行结果';
COMMENT ON COLUMN ent_action.expires_at IS '默认 15 分钟失效';
COMMENT ON COLUMN ent_action.created_at IS '创建时间';
COMMENT ON COLUMN ent_action.dedup_key IS '幂等键';

COMMENT ON TABLE ent_audit IS '请假业务审计';
COMMENT ON COLUMN ent_audit.id IS '审计 ID';
COMMENT ON COLUMN ent_audit.request_id IS '请假申请 ID';
COMMENT ON COLUMN ent_audit.actor_id IS '操作人';
COMMENT ON COLUMN ent_audit.action_id IS '关联确认卡';
COMMENT ON COLUMN ent_audit.event IS '事件类型';
COMMENT ON COLUMN ent_audit.comment IS '审批意见等';
COMMENT ON COLUMN ent_audit.created_at IS '发生时间';
COMMENT ON COLUMN ent_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN ent_audit.trace_id IS '推理链路 ID';

COMMENT ON TABLE ent_kb_grant IS '知识库授权：按全员/部门/角色开放';
COMMENT ON COLUMN ent_kb_grant.kb_id IS '知识库 ID';
COMMENT ON COLUMN ent_kb_grant.audience IS 'ALL/DEPARTMENT/ROLE';
COMMENT ON COLUMN ent_kb_grant.subject IS '受众取值，ALL 时可为空占位';

COMMENT ON TABLE ent_document_policy IS '制度文档版本与生效日期';
COMMENT ON COLUMN ent_document_policy.doc_id IS '文档 ID';
COMMENT ON COLUMN ent_document_policy.version IS '版本号，如 v1.0';
COMMENT ON COLUMN ent_document_policy.effective_date IS '生效日期';

COMMENT ON TABLE ent_erp_pending_action IS 'HTTP ERP 写操作的本地协调记录，不保存 ERP 业务事实';
COMMENT ON COLUMN ent_erp_pending_action.id IS '动作 ID';
COMMENT ON COLUMN ent_erp_pending_action.tenant_id IS '租户 ID';
COMMENT ON COLUMN ent_erp_pending_action.user_id IS '发起人';
COMMENT ON COLUMN ent_erp_pending_action.conversation_id IS '会话 ID';
COMMENT ON COLUMN ent_erp_pending_action.action_type IS 'CREATE_LEAVE/WITHDRAW_LEAVE';
COMMENT ON COLUMN ent_erp_pending_action.payload IS '待提交参数';
COMMENT ON COLUMN ent_erp_pending_action.idempotency_key IS '调用 ERP 的幂等键';
COMMENT ON COLUMN ent_erp_pending_action.trace_id IS '链路 ID';
COMMENT ON COLUMN ent_erp_pending_action.status IS 'PENDING/DONE/CANCELLED/EXPIRED';
COMMENT ON COLUMN ent_erp_pending_action.result IS 'ERP 返回摘要';
COMMENT ON COLUMN ent_erp_pending_action.expires_at IS '失效时间';
COMMENT ON COLUMN ent_erp_pending_action.created_at IS '创建时间';
COMMENT ON COLUMN ent_erp_pending_action.updated_at IS '更新时间';

-- ---------------------------------------------------------------------------
-- 工作流平台
-- ---------------------------------------------------------------------------
COMMENT ON COLUMN wf_definition.id IS '定义主键';
COMMENT ON COLUMN wf_definition.name IS '流程名称';
COMMENT ON COLUMN wf_definition.description IS '说明';
COMMENT ON COLUMN wf_definition.tenant_id IS '租户 ID';
COMMENT ON COLUMN wf_definition.current_published_version_id IS '当前已发布版本 ID';
COMMENT ON COLUMN wf_definition.create_time IS '创建时间';
COMMENT ON COLUMN wf_definition.update_time IS '更新时间';

COMMENT ON COLUMN wf_version.id IS '版本主键';
COMMENT ON COLUMN wf_version.definition_id IS '所属定义';
COMMENT ON COLUMN wf_version.version_number IS '版本号，从 1 递增';
COMMENT ON COLUMN wf_version.bpmn_xml IS '发布时冻结的 BPMN，运行时以它为准';
COMMENT ON COLUMN wf_version.created_by IS '创建人';
COMMENT ON COLUMN wf_version.created_time IS '创建时间';
COMMENT ON COLUMN wf_version.updated_time IS '更新时间';
COMMENT ON COLUMN wf_version.published_time IS '发布时间';
COMMENT ON COLUMN wf_version.published_by IS '发布人';
COMMENT ON COLUMN wf_version.parent_version_id IS '来源版本';
COMMENT ON COLUMN wf_version.content_hash IS '发布包内容哈希，用于门禁比对';
COMMENT ON COLUMN wf_version.dependency_snapshot IS '依赖的工具/知识库快照 JSON';
COMMENT ON COLUMN wf_version.source_type IS 'LEGACY_BPMN 或可视化编排';
COMMENT ON COLUMN wf_version.lock_version IS '草稿乐观锁';
COMMENT ON COLUMN wf_version.tested_content_hash IS '最近一次模拟通过的内容哈希';
COMMENT ON COLUMN wf_version.reviewed_content_hash IS '审核时的内容哈希';

COMMENT ON TABLE wf_review IS '工作流版本审核记录，编辑者不得审核自己提交的版本';
COMMENT ON COLUMN wf_review.id IS '审核记录 ID';
COMMENT ON COLUMN wf_review.version_id IS '版本 ID';
COMMENT ON COLUMN wf_review.reviewer_id IS '审核人';
COMMENT ON COLUMN wf_review.action IS 'APPROVE/REJECT';
COMMENT ON COLUMN wf_review.comment IS '审核意见';
COMMENT ON COLUMN wf_review.created_time IS '审核时间';

COMMENT ON COLUMN wf_route.id IS '路由主键';
COMMENT ON COLUMN wf_route.route_key IS '租户内路由键';
COMMENT ON COLUMN wf_route.intent_code IS '意图码';
COMMENT ON COLUMN wf_route.workflow_def_key IS '绑定的工作流定义键';
COMMENT ON COLUMN wf_route.react_config IS '可选 ReAct 配置 JSON';
COMMENT ON COLUMN wf_route.tenant_id IS '租户 ID';
COMMENT ON COLUMN wf_route.create_time IS '创建时间';
COMMENT ON COLUMN wf_route.update_time IS '更新时间';

COMMENT ON TABLE workflow_action IS '工作流确认卡，写操作需用户确认';
COMMENT ON COLUMN workflow_action.id IS '动作 ID';
COMMENT ON COLUMN workflow_action.instance_id IS '流程实例 ID';
COMMENT ON COLUMN workflow_action.user_id IS '确认人';
COMMENT ON COLUMN workflow_action.action_type IS '动作类型';
COMMENT ON COLUMN workflow_action.payload IS '冻结参数';
COMMENT ON COLUMN workflow_action.status IS 'PENDING/DONE/CANCELLED/EXPIRED';
COMMENT ON COLUMN workflow_action.result IS '执行结果';
COMMENT ON COLUMN workflow_action.expires_at IS '失效时间';
COMMENT ON COLUMN workflow_action.created_at IS '创建时间';
COMMENT ON COLUMN workflow_action.dedup_key IS '幂等键';
COMMENT ON COLUMN workflow_action.tenant_id IS '租户 ID';
COMMENT ON COLUMN workflow_action.node_instance_id IS '节点实例 ID';
COMMENT ON COLUMN workflow_action.tool_id IS '将调用的工具 ID';
COMMENT ON COLUMN workflow_action.tool_version IS '工具契约版本';
COMMENT ON COLUMN workflow_action.parameter_hash IS '参数哈希';
COMMENT ON COLUMN workflow_action.confirmed_at IS '确认时间';
COMMENT ON COLUMN workflow_action.lock_version IS '乐观锁';

COMMENT ON COLUMN operation_audit.id IS '审计主键';
COMMENT ON COLUMN operation_audit.operation IS '操作码';
COMMENT ON COLUMN operation_audit.operator_id IS '操作人';
COMMENT ON COLUMN operation_audit.target_type IS '对象类型';
COMMENT ON COLUMN operation_audit.target_id IS '对象 ID';
COMMENT ON COLUMN operation_audit.trace_id IS '链路 ID';
COMMENT ON COLUMN operation_audit.tenant_id IS '租户 ID';
COMMENT ON COLUMN operation_audit.detail IS '详情 JSON，避免完整敏感原文';
COMMENT ON COLUMN operation_audit.created_time IS '发生时间';

COMMENT ON TABLE wf_instance IS '动态工作流业务实例，与 Flowable 运行时实例映射';
COMMENT ON COLUMN wf_instance.id IS '业务实例 ID';
COMMENT ON COLUMN wf_instance.flowable_instance_id IS 'Flowable process instance id';
COMMENT ON COLUMN wf_instance.tenant_id IS '租户 ID';
COMMENT ON COLUMN wf_instance.user_id IS '发起人';
COMMENT ON COLUMN wf_instance.conversation_id IS '会话 ID';
COMMENT ON COLUMN wf_instance.definition_key IS '流程定义键';
COMMENT ON COLUMN wf_instance.version_id IS '发布版本 ID';
COMMENT ON COLUMN wf_instance.status IS '实例状态';
COMMENT ON COLUMN wf_instance.business_key IS '业务键';
COMMENT ON COLUMN wf_instance.trace_id IS '推理链路 ID';
COMMENT ON COLUMN wf_instance.idempotency_key IS '创建幂等键';
COMMENT ON COLUMN wf_instance.lock_version IS '乐观锁';
COMMENT ON COLUMN wf_instance.created_time IS '创建时间';
COMMENT ON COLUMN wf_instance.updated_time IS '更新时间';

COMMENT ON TABLE wf_node_execution IS '工作流节点执行记录';
COMMENT ON COLUMN wf_node_execution.id IS '记录 ID';
COMMENT ON COLUMN wf_node_execution.instance_id IS '业务实例 ID';
COMMENT ON COLUMN wf_node_execution.node_instance_id IS '节点实例 ID';
COMMENT ON COLUMN wf_node_execution.node_type IS '节点类型：知识问答/工具/确认/人工等待等';
COMMENT ON COLUMN wf_node_execution.attempt IS '重试次数，从 1 开始';
COMMENT ON COLUMN wf_node_execution.status IS '节点状态';
COMMENT ON COLUMN wf_node_execution.input_ref IS '输入摘要或对象引用';
COMMENT ON COLUMN wf_node_execution.output_ref IS '输出摘要或对象引用';
COMMENT ON COLUMN wf_node_execution.error_code IS '错误码';
COMMENT ON COLUMN wf_node_execution.trace_id IS '链路 ID';
COMMENT ON COLUMN wf_node_execution.started_time IS '开始时间';
COMMENT ON COLUMN wf_node_execution.finished_time IS '结束时间';

COMMENT ON TABLE wf_wait_state IS '流程等待用户补充参数或确认';
COMMENT ON COLUMN wf_wait_state.id IS '等待记录 ID';
COMMENT ON COLUMN wf_wait_state.instance_id IS '业务实例 ID';
COMMENT ON COLUMN wf_wait_state.flowable_task_id IS 'Flowable 用户任务 ID';
COMMENT ON COLUMN wf_wait_state.tenant_id IS '租户 ID';
COMMENT ON COLUMN wf_wait_state.user_id IS '等待的用户';
COMMENT ON COLUMN wf_wait_state.conversation_id IS '会话 ID';
COMMENT ON COLUMN wf_wait_state.node_instance_id IS '节点实例 ID';
COMMENT ON COLUMN wf_wait_state.wait_type IS '等待类型：参数/确认/插问';
COMMENT ON COLUMN wf_wait_state.status IS '等待状态';
COMMENT ON COLUMN wf_wait_state.payload IS '等待上下文';
COMMENT ON COLUMN wf_wait_state.lock_version IS '乐观锁';
COMMENT ON COLUMN wf_wait_state.expires_at IS '超时时间';
COMMENT ON COLUMN wf_wait_state.created_time IS '创建时间';
COMMENT ON COLUMN wf_wait_state.updated_time IS '更新时间';

COMMENT ON TABLE wf_external_reference IS '流程关联的外部系统对象，如 ERP 请假单';
COMMENT ON COLUMN wf_external_reference.id IS '记录 ID';
COMMENT ON COLUMN wf_external_reference.instance_id IS '业务实例 ID';
COMMENT ON COLUMN wf_external_reference.tenant_id IS '租户 ID';
COMMENT ON COLUMN wf_external_reference.system_code IS '外部系统编码，如 ERP';
COMMENT ON COLUMN wf_external_reference.object_type IS '对象类型，如 LEAVE_REQUEST';
COMMENT ON COLUMN wf_external_reference.object_id IS '外部对象 ID';
COMMENT ON COLUMN wf_external_reference.latest_status IS '外部最新状态';
COMMENT ON COLUMN wf_external_reference.updated_time IS '更新时间';

COMMENT ON TABLE wf_simulation_report IS '发布前模拟运行报告，未通过不得发布';
COMMENT ON COLUMN wf_simulation_report.id IS '报告 ID';
COMMENT ON COLUMN wf_simulation_report.version_id IS '版本 ID';
COMMENT ON COLUMN wf_simulation_report.content_hash IS '被模拟的内容哈希';
COMMENT ON COLUMN wf_simulation_report.status IS '模拟结果状态';
COMMENT ON COLUMN wf_simulation_report.report IS '报告正文';
COMMENT ON COLUMN wf_simulation_report.created_by IS '执行人';
COMMENT ON COLUMN wf_simulation_report.created_time IS '创建时间';

-- ---------------------------------------------------------------------------
-- MCP 工具目录
-- ---------------------------------------------------------------------------
COMMENT ON TABLE mcp_connector IS 'MCP 连接器。secret_ref 只存引用，不存真实 Token';
COMMENT ON COLUMN mcp_connector.tenant_id IS '租户 ID';
COMMENT ON COLUMN mcp_connector.connector_id IS '连接器 ID';
COMMENT ON COLUMN mcp_connector.display_name IS '展示名称';
COMMENT ON COLUMN mcp_connector.endpoint IS 'MCP HTTP 地址';
COMMENT ON COLUMN mcp_connector.secret_ref IS '密钥引用，如 vault:erp，由环境变量或 Vault 解析';
COMMENT ON COLUMN mcp_connector.status IS '连接器状态';
COMMENT ON COLUMN mcp_connector.updated_by IS '最后修改人';
COMMENT ON COLUMN mcp_connector.updated_at IS '更新时间';

COMMENT ON TABLE mcp_catalog_tool IS '从连接器发现的工具快照，需审核后才能发布给运行时';
COMMENT ON COLUMN mcp_catalog_tool.tenant_id IS '租户 ID';
COMMENT ON COLUMN mcp_catalog_tool.connector_id IS '连接器 ID';
COMMENT ON COLUMN mcp_catalog_tool.tool_id IS '工具 ID';
COMMENT ON COLUMN mcp_catalog_tool.title IS '标题';
COMMENT ON COLUMN mcp_catalog_tool.description IS '说明';
COMMENT ON COLUMN mcp_catalog_tool.review_status IS '审核状态';
COMMENT ON COLUMN mcp_catalog_tool.enabled IS '是否对运行时启用';
COMMENT ON COLUMN mcp_catalog_tool.discovered_at IS '发现时间';
COMMENT ON COLUMN mcp_catalog_tool.contract_version IS '契约版本，schema 变化必须重新审核';
COMMENT ON COLUMN mcp_catalog_tool.schema_hash IS '入参 schema 哈希';
COMMENT ON COLUMN mcp_catalog_tool.tool_schema IS 'JSON Schema';
COMMENT ON COLUMN mcp_catalog_tool.permission_code IS '调用所需权限码';
COMMENT ON COLUMN mcp_catalog_tool.read_only IS '是否只读工具';

COMMENT ON TABLE mcp_intent_binding IS '意图码绑定到已审核 MCP 工具';
COMMENT ON COLUMN mcp_intent_binding.tenant_id IS '租户 ID';
COMMENT ON COLUMN mcp_intent_binding.intent_code IS '意图码';
COMMENT ON COLUMN mcp_intent_binding.connector_id IS '连接器 ID';
COMMENT ON COLUMN mcp_intent_binding.tool_id IS '工具 ID';
COMMENT ON COLUMN mcp_intent_binding.status IS '绑定状态，仅已发布可被运行时加载';
COMMENT ON COLUMN mcp_intent_binding.created_by IS '创建人';
COMMENT ON COLUMN mcp_intent_binding.updated_at IS '更新时间';

COMMENT ON TABLE mcp_catalog_revision IS '每个租户的运行时工具目录版本，数据库为权威源，Redis 只做刷新通知';
COMMENT ON COLUMN mcp_catalog_revision.tenant_id IS '租户 ID';
COMMENT ON COLUMN mcp_catalog_revision.revision IS '单调递增版本号';
COMMENT ON COLUMN mcp_catalog_revision.updated_at IS '更新时间';
