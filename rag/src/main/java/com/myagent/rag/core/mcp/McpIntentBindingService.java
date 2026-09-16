package com.myagent.rag.core.mcp;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 将已审核工具显式关联到用户意图；运行时发布另行执行，避免编辑即生效。 */
@Service
public class McpIntentBindingService {
    private final JdbcTemplate jdbc;
    private final PolicyDecisionService policy;
    public McpIntentBindingService(JdbcTemplate jdbc, PolicyDecisionService policy) { this.jdbc = jdbc; this.policy = policy; }

    @Transactional
    public void bind(ExecutionIdentity identity, BindingInput input) {
        policy.requirePermission(identity, "knowledge:authorize");
        if (input == null || blank(input.intentCode()) || blank(input.connectorId()) || blank(input.toolId())) throw new ClientException("意图与工具绑定参数不完整");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM mcp_catalog_tool WHERE tenant_id=? AND connector_id=? AND tool_id=? AND review_status='APPROVED' AND enabled=TRUE",
                Integer.class, identity.getTenantId(), input.connectorId(), input.toolId());
        if (count == null || count != 1) throw new ClientException("只能绑定已审核并启用的 MCP 工具");
        jdbc.update("DELETE FROM mcp_intent_binding WHERE tenant_id=? AND intent_code=?", identity.getTenantId(), input.intentCode());
        jdbc.update("INSERT INTO mcp_intent_binding(tenant_id,intent_code,connector_id,tool_id,status,created_by,updated_at) VALUES(?,?,?,?, 'DRAFT', ?, ?)",
                identity.getTenantId(), input.intentCode(), input.connectorId(), input.toolId(), identity.getUserId(), Timestamp.from(Instant.now()));
    }
    @Transactional
    public void publish(ExecutionIdentity identity, String intentCode) {
        policy.requirePermission(identity, "knowledge:authorize");
        int changed = jdbc.update("UPDATE mcp_intent_binding SET status='PUBLISHED',updated_at=? WHERE tenant_id=? AND intent_code=?", Timestamp.from(Instant.now()), identity.getTenantId(), intentCode);
        if (changed != 1) throw new ClientException("意图工具绑定不存在");
    }
    public List<BindingView> list(ExecutionIdentity identity) {
        policy.requirePermission(identity, "knowledge:authorize");
        return jdbc.query("SELECT intent_code,connector_id,tool_id,status,created_by,updated_at FROM mcp_intent_binding WHERE tenant_id=? ORDER BY intent_code", new DataClassRowMapper<>(BindingView.class), identity.getTenantId());
    }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
    public record BindingInput(String intentCode, String connectorId, String toolId) { }
    public record BindingView(String intentCode, String connectorId, String toolId, String status, String createdBy, Timestamp updatedAt) { }
}
