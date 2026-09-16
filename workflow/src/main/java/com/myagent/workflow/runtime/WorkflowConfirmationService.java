package com.myagent.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/** 写操作确认记录：绑定执行人、工具契约和参数摘要，确认只能成功一次。 */
@Service
@RequiredArgsConstructor
public class WorkflowConfirmationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public Confirmation create(ExecutionIdentity identity, String instanceId, String nodeId, String toolId,
                               String toolVersion, Map<String,Object> parameters, Instant expiresAt) {
        String id = UUID.randomUUID().toString(); String payload = json(parameters); String hash = hash(payload);
        jdbc.update("INSERT INTO workflow_action(id,instance_id,user_id,action_type,payload,status,expires_at,created_at,dedup_key,tenant_id,node_instance_id,tool_id,tool_version,parameter_hash,lock_version) VALUES(?,?,?,'TOOL_EXECUTION',?,'PENDING',?,CURRENT_TIMESTAMP,?,?,?,?,?,?,0)",
                id, instanceId, identity.getUserId(), payload, Timestamp.from(expiresAt), identity.getTenantId() + ":" + instanceId + ":" + nodeId, identity.getTenantId(), nodeId, toolId, toolVersion, hash);
        return new Confirmation(id, instanceId, nodeId, toolId, toolVersion, hash, "PENDING", 0);
    }

    public Confirmation confirm(ExecutionIdentity identity, String actionId, long lockVersion, Map<String,Object> parameters) {
        String hash = hash(json(parameters));
        int changed = jdbc.update("UPDATE workflow_action SET status='CONFIRMED',confirmed_at=CURRENT_TIMESTAMP,lock_version=lock_version+1 WHERE id=? AND tenant_id=? AND user_id=? AND status='PENDING' AND expires_at>CURRENT_TIMESTAMP AND parameter_hash=? AND lock_version=?",
                actionId, identity.getTenantId(), identity.getUserId(), hash, lockVersion);
        if (changed != 1) throw new ClientException("确认已失效、参数已改变或已被处理");
        return get(identity, actionId);
    }

    public void invalidatePending(String instanceId, String nodeId) {
        jdbc.update("UPDATE workflow_action SET status='INVALIDATED',lock_version=lock_version+1 WHERE instance_id=? AND node_instance_id=? AND status IN ('PENDING','CONFIRMED')", instanceId, nodeId);
    }

    public Confirmation get(ExecutionIdentity identity, String id) {
        return jdbc.query("SELECT id,instance_id,node_instance_id,tool_id,tool_version,parameter_hash,status,lock_version FROM workflow_action WHERE id=? AND tenant_id=? AND user_id=?",
                (rs,n) -> new Confirmation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getLong(8)), id, identity.getTenantId(), identity.getUserId()).stream().findFirst().orElseThrow(() -> new ClientException("确认记录不存在或无权访问"));
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new ClientException("确认参数无法序列化"); } }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    public record Confirmation(String id,String instanceId,String nodeInstanceId,String toolId,String toolVersion,String parameterHash,String status,long lockVersion) { }
}
