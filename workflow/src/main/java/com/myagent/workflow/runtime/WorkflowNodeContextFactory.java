package com.myagent.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.model.WorkflowProcess;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 从实例绑定版本加载节点配置，禁止运行时采用前端或模型临时提交的配置。 */
@Component
public class WorkflowNodeContextFactory {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WorkflowNodeContextFactory(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public WorkflowNodeContext create(DelegateExecution execution) {
        long versionId = number(execution.getVariable("versionId"), "流程缺少发布版本");
        String instanceId = text(execution.getVariable("platformInstanceId"), "流程缺少平台实例标识");
        Map<String, Object> owner = jdbc.queryForMap(
                "SELECT tenant_id,user_id FROM wf_instance WHERE id=? AND version_id=?", instanceId, versionId);
        try {
            WorkflowProcess.Node node = findNode(versionId, execution.getCurrentActivityId());
            LoginUser identity = LoginUser.builder().tenantId(String.valueOf(owner.get("tenant_id")))
                    .userId(String.valueOf(owner.get("user_id"))).username(String.valueOf(owner.get("user_id"))).build();
            return new WorkflowNodeContext(instanceId, node.id(), versionId, node, identity,
                    Map.copyOf(execution.getVariables()), execution);
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClientException("无法读取动态流程节点配置");
        }
    }

    public WorkflowProcess.Node findNode(long versionId, String nodeId) {
        String processJson = jdbc.queryForObject("SELECT process_json FROM wf_version WHERE id=?", String.class, versionId);
        if (processJson == null || processJson.isBlank()) throw new ClientException("动态流程版本缺少 processJson");
        try {
            WorkflowProcess process = json.readValue(processJson, WorkflowProcess.class);
            return process.nodes().stream().filter(item -> item.id().equals(nodeId)).findFirst()
                    .orElseThrow(() -> new ClientException("发布版本中不存在当前节点"));
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ClientException("无法读取动态流程节点配置");
        }
    }

    private static long number(Object value, String message) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(value)); }
        catch (Exception ex) { throw new ClientException(message); }
    }
    private static String text(Object value, String message) {
        if (value == null || String.valueOf(value).isBlank()) throw new ClientException(message);
        return String.valueOf(value);
    }
}
