package com.myagent.workflow.runtime;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 动态流程公开运行入口：固定注入身份，持久化实例与等待游标，并提供幂等恢复。 */
@Service
@RequiredArgsConstructor
public class DynamicWorkflowRuntimeService {
    private static final java.util.Set<String> RESERVED = java.util.Set.of("tenantId", "initiatorId", "traceId", "versionId");
    private final JdbcTemplate jdbc;
    private final WorkflowRuntimeService workflowRuntime;
    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public InstanceView start(ExecutionIdentity identity, StartCommand command) {
        requireIdentity(identity); requireText(command.defKey(), "缺少流程定义"); requireText(command.idempotencyKey(), "缺少幂等键");
        List<InstanceView> existing = findByRequest(identity, command.idempotencyKey());
        if (!existing.isEmpty()) return existing.get(0);
        Map<String, Object> published = jdbc.queryForMap("SELECT d.current_published_version_id FROM wf_definition d JOIN wf_version v ON v.id=d.current_published_version_id WHERE d.def_key=? AND d.tenant_id=? AND v.status='PUBLISHED'",
                command.defKey(), identity.getTenantId());
        long versionId = ((Number) published.get("current_published_version_id")).longValue();
        String id = UUID.randomUUID().toString(); String traceId = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO wf_instance(id,tenant_id,user_id,conversation_id,definition_key,version_id,status,business_key,trace_id,idempotency_key) VALUES(?,?,?,?,?,?,'RUNNING',?,?,?)",
                    id, identity.getTenantId(), identity.getUserId(), command.conversationId(), command.defKey(), versionId, command.businessKey(), traceId, command.idempotencyKey());
        } catch (DuplicateKeyException ex) { return findByRequest(identity, command.idempotencyKey()).get(0); }
        try {
            Map<String, Object> input = sanitize(command.input()); input.put("platformInstanceId", id); input.put("tenantId", identity.getTenantId());
            String flowableId = workflowRuntime.startInstance(command.defKey(), versionId, identity.getUserId(), id, traceId, input);
            jdbc.update("UPDATE wf_instance SET flowable_instance_id=?,updated_time=CURRENT_TIMESTAMP WHERE id=?", flowableId, id);
            syncWait(id, flowableId, identity, command.conversationId());
            return get(identity, id);
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE wf_instance SET status='FAILED',updated_time=CURRENT_TIMESTAMP WHERE id=?", id); throw ex;
        }
    }

    public InstanceView resume(ExecutionIdentity identity, String waitId, long lockVersion, Map<String, Object> input) {
        var wait = jdbc.queryForMap("SELECT instance_id,flowable_task_id,conversation_id FROM wf_wait_state WHERE id=? AND tenant_id=? AND user_id=? AND status='WAITING'",
                waitId, identity.getTenantId(), identity.getUserId());
        int changed = jdbc.update("UPDATE wf_wait_state SET status='RESUMING',lock_version=lock_version+1,updated_time=CURRENT_TIMESTAMP WHERE id=? AND lock_version=? AND status='WAITING'", waitId, lockVersion);
        if (changed != 1) throw new ClientException("等待任务已处理，请刷新后重试");
        String instanceId = String.valueOf(wait.get("instance_id"));
        taskService.complete(String.valueOf(wait.get("flowable_task_id")), sanitize(input));
        jdbc.update("UPDATE wf_wait_state SET status='COMPLETED',updated_time=CURRENT_TIMESTAMP WHERE id=?", waitId);
        InstanceView view = get(identity, instanceId);
        if (view.flowableInstanceId() != null && runtimeService.createProcessInstanceQuery().processInstanceId(view.flowableInstanceId()).singleResult() != null) {
            syncWait(instanceId, view.flowableInstanceId(), identity, (String) wait.get("conversation_id"));
        } else jdbc.update("UPDATE wf_instance SET status='COMPLETED',updated_time=CURRENT_TIMESTAMP WHERE id=?", instanceId);
        return get(identity, instanceId);
    }

    public InstanceView cancel(ExecutionIdentity identity, String instanceId) {
        InstanceView view = get(identity, instanceId);
        if (view.flowableInstanceId() != null && runtimeService.createProcessInstanceQuery().processInstanceId(view.flowableInstanceId()).singleResult() != null)
            runtimeService.deleteProcessInstance(view.flowableInstanceId(), "用户取消");
        jdbc.update("UPDATE wf_instance SET status='CANCELLED',lock_version=lock_version+1,updated_time=CURRENT_TIMESTAMP WHERE id=?", instanceId);
        jdbc.update("UPDATE wf_wait_state SET status='CANCELLED',updated_time=CURRENT_TIMESTAMP WHERE instance_id=? AND status='WAITING'", instanceId);
        return get(identity, instanceId);
    }

    public InstanceView get(ExecutionIdentity identity, String id) {
        requireIdentity(identity);
        List<InstanceView> rows = jdbc.query("SELECT id,flowable_instance_id,definition_key,version_id,status,business_key,trace_id,lock_version FROM wf_instance WHERE id=? AND tenant_id=? AND user_id=?",
                (rs, n) -> new InstanceView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8)), id, identity.getTenantId(), identity.getUserId());
        if (rows.isEmpty()) throw new ClientException("流程实例不存在或无权访问"); return rows.get(0);
    }

    public List<WaitView> waits(ExecutionIdentity identity) {
        requireIdentity(identity);
        return jdbc.query("SELECT id,instance_id,node_instance_id,wait_type,lock_version FROM wf_wait_state WHERE tenant_id=? AND user_id=? AND status='WAITING' ORDER BY created_time",
                (rs, n) -> new WaitView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getLong(5)), identity.getTenantId(), identity.getUserId());
    }

    private void syncWait(String instanceId, String flowableId, ExecutionIdentity identity, String conversationId) {
        taskService.createTaskQuery().processInstanceId(flowableId).active().list().forEach(task -> {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM wf_wait_state WHERE flowable_task_id=?", Integer.class, task.getId());
            if (count != null && count > 0) return;
            jdbc.update("INSERT INTO wf_wait_state(id,instance_id,flowable_task_id,tenant_id,user_id,conversation_id,node_instance_id,wait_type,status) VALUES(?,?,?,?,?,?,?,'USER','WAITING')",
                    UUID.randomUUID().toString(), instanceId, task.getId(), identity.getTenantId(), identity.getUserId(), conversationId, task.getTaskDefinitionKey());
            jdbc.update("UPDATE wf_instance SET status='WAITING_USER',updated_time=CURRENT_TIMESTAMP WHERE id=?", instanceId);
        });
    }

    private List<InstanceView> findByRequest(ExecutionIdentity identity, String key) {
        return jdbc.query("SELECT id,flowable_instance_id,definition_key,version_id,status,business_key,trace_id,lock_version FROM wf_instance WHERE tenant_id=? AND user_id=? AND idempotency_key=?",
                (rs, n) -> new InstanceView(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8)), identity.getTenantId(), identity.getUserId(), key);
    }
    private static Map<String, Object> sanitize(Map<String, Object> source) { Map<String, Object> result = new HashMap<>(); if (source != null) source.forEach((k,v) -> { if (!RESERVED.contains(k)) result.put(k,v); }); return result; }
    private static void requireIdentity(ExecutionIdentity identity) { if (identity == null || blank(identity.getTenantId()) || blank(identity.getUserId())) throw new ClientException("缺少执行身份"); }
    private static void requireText(String value, String message) { if (blank(value)) throw new ClientException(message); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record StartCommand(String defKey, String conversationId, String businessKey, String idempotencyKey, Map<String,Object> input) { }
    public record InstanceView(String id, String flowableInstanceId, String definitionKey, long versionId, String status, String businessKey, String traceId, long lockVersion) { }
    public record WaitView(String id, String instanceId, String nodeInstanceId, String waitType, long lockVersion) { }
}
