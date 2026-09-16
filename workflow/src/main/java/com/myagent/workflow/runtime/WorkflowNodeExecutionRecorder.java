package com.myagent.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** 持久化节点级执行结果，失败时仅保存安全错误码和脱敏后的变量摘要。 */
@Component
public class WorkflowNodeExecutionRecorder {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WorkflowNodeExecutionRecorder(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc; this.json = json;
    }

    public String start(DelegateExecution execution, String nodeType) {
        Object instanceId = execution.getVariable("platformInstanceId");
        if (instanceId == null) return null;
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO wf_node_execution(id,instance_id,node_instance_id,node_type,status,input_ref,trace_id) VALUES(?,?,?,?, 'RUNNING',?,?)",
                id, String.valueOf(instanceId), execution.getCurrentActivityId(), nodeType,
                safeJson(execution.getVariables()), String.valueOf(execution.getVariable("traceId")));
        return id;
    }

    public void success(String id, DelegateExecution execution) {
        if (id == null) return;
        jdbc.update("UPDATE wf_node_execution SET status='SUCCEEDED',output_ref=?,finished_time=CURRENT_TIMESTAMP WHERE id=?",
                safeJson(execution.getVariables()), id);
    }

    public void failure(String id, RuntimeException error) {
        if (id == null) return;
        jdbc.update("UPDATE wf_node_execution SET status='FINAL_FAILURE',error_code=?,finished_time=CURRENT_TIMESTAMP WHERE id=?",
                error.getClass().getSimpleName(), id);
    }

    private String safeJson(Map<String, Object> variables) {
        try {
            Map<String, Object> safe = new java.util.LinkedHashMap<>(variables);
            safe.keySet().removeIf(key -> key.toLowerCase().contains("secret") || key.toLowerCase().contains("token"));
            return json.writeValueAsString(safe);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
