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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 管理面：登记、发现、审核工具。运行时注册必须读取已审核且启用的数据。 */
@Service
public class McpConnectorAdminService {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final JdbcTemplate jdbc;
    private final PolicyDecisionService policy;
    private final McpCatalogDiscoveryService catalog;
    private final McpDiscoveryTokenProvider tokenProvider;

    public McpConnectorAdminService(JdbcTemplate jdbc, PolicyDecisionService policy, McpCatalogDiscoveryService catalog,
            McpDiscoveryTokenProvider tokenProvider) {
        this.jdbc = jdbc; this.policy = policy; this.catalog = catalog; this.tokenProvider = tokenProvider;
    }

    @Transactional
    public void save(ExecutionIdentity identity, ConnectorInput input) {
        authorize(identity); validate(input);
        jdbc.update("DELETE FROM mcp_connector WHERE tenant_id=? AND connector_id=?", identity.getTenantId(), input.connectorId());
        jdbc.update("INSERT INTO mcp_connector(tenant_id,connector_id,display_name,endpoint,secret_ref,status,updated_by,updated_at) VALUES(?,?,?,?,?,'DRAFT',?,?)",
                identity.getTenantId(), input.connectorId(), input.displayName(), input.endpoint(), input.secretRef(), identity.getUserId(), Timestamp.from(Instant.now()));
    }

    @Transactional
    public List<ToolView> discover(ExecutionIdentity identity, String connectorId) {
        authorize(identity);
        ConnectorRow connector = connector(identity.getTenantId(), connectorId);
        McpCatalogSnapshot snapshot = catalog.refresh(new McpConnectorDefinition(connector.tenantId(), connector.connectorId(), connector.endpoint(), connector.secretRef()),
                tokenProvider.resolve(connector.secretRef()));
        for (McpCatalogTool tool : snapshot.tools()) {
            saveDiscoveredTool(connector, snapshot, tool);
        }
        jdbc.update("UPDATE mcp_connector SET status='DISCOVERED',updated_by=?,updated_at=? WHERE tenant_id=? AND connector_id=?",
                identity.getUserId(), Timestamp.from(Instant.now()), connector.tenantId(), connector.connectorId());
        return tools(identity, connectorId);
    }

    @Transactional
    public void review(ExecutionIdentity identity, String connectorId, String toolId, ReviewInput input) {
        authorize(identity);
        if (input == null || !("APPROVED".equals(input.reviewStatus()) || "DISABLED".equals(input.reviewStatus()))) {
            throw new ClientException("工具审核状态必须为 APPROVED 或 DISABLED");
        }
        int changed = jdbc.update("UPDATE mcp_catalog_tool SET review_status=?,enabled=? WHERE tenant_id=? AND connector_id=? AND tool_id=?",
                input.reviewStatus(), "APPROVED".equals(input.reviewStatus()) && input.enabled(), identity.getTenantId(), connectorId, toolId);
        if (changed != 1) throw new ClientException("工具不存在或不属于当前租户");
    }

    public List<ConnectorView> connectors(ExecutionIdentity identity) {
        authorize(identity);
        return jdbc.query("SELECT tenant_id,connector_id,display_name,endpoint,status,updated_at FROM mcp_connector WHERE tenant_id=? ORDER BY connector_id",
                new DataClassRowMapper<>(ConnectorView.class), identity.getTenantId());
    }

    public List<ToolView> tools(ExecutionIdentity identity, String connectorId) {
        authorize(identity);
        return jdbc.query("SELECT tool_id,title,description,review_status,enabled,discovered_at,contract_version,schema_hash FROM mcp_catalog_tool WHERE tenant_id=? AND connector_id=? ORDER BY tool_id",
                new DataClassRowMapper<>(ToolView.class), identity.getTenantId(), connectorId);
    }

    private ConnectorRow connector(String tenantId, String connectorId) {
        List<ConnectorRow> rows = jdbc.query("SELECT tenant_id,connector_id,display_name,endpoint,secret_ref,status,updated_by,updated_at FROM mcp_connector WHERE tenant_id=? AND connector_id=?",
                new DataClassRowMapper<>(ConnectorRow.class), tenantId, connectorId);
        if (rows.isEmpty()) throw new ClientException("连接器不存在或不属于当前租户");
        return rows.get(0);
    }

    private void saveDiscoveredTool(ConnectorRow connector, McpCatalogSnapshot snapshot, McpCatalogTool tool) {
        String schema;
        try { schema = JSON.writeValueAsString(tool.definition()); }
        catch (Exception ex) { throw new ClientException("工具 Schema 无法序列化：" + tool.toolId()); }
        String hash = sha256(schema);
        List<ToolContractRow> existing = jdbc.query("SELECT contract_version,schema_hash FROM mcp_catalog_tool WHERE tenant_id=? AND connector_id=? AND tool_id=?",
                new DataClassRowMapper<>(ToolContractRow.class), connector.tenantId(), connector.connectorId(), tool.toolId());
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO mcp_catalog_tool(tenant_id,connector_id,tool_id,title,description,review_status,enabled,discovered_at,contract_version,schema_hash,tool_schema) VALUES(?,?,?,?,?,'PENDING_REVIEW',FALSE,?,1,?,?)",
                    connector.tenantId(), connector.connectorId(), tool.toolId(), tool.title(), tool.description(), Timestamp.from(snapshot.discoveredAt()), hash, schema);
            return;
        }
        ToolContractRow old = existing.get(0);
        boolean changed = !hash.equals(old.schemaHash());
        jdbc.update("UPDATE mcp_catalog_tool SET title=?,description=?,discovered_at=?,contract_version=?,schema_hash=?,tool_schema=?,review_status=CASE WHEN ? THEN 'PENDING_REVIEW' ELSE review_status END,enabled=CASE WHEN ? THEN FALSE ELSE enabled END WHERE tenant_id=? AND connector_id=? AND tool_id=?",
                tool.title(), tool.description(), Timestamp.from(snapshot.discoveredAt()), changed ? old.contractVersion() + 1 : old.contractVersion(), hash, schema,
                changed, changed, connector.tenantId(), connector.connectorId(), tool.toolId());
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private void authorize(ExecutionIdentity identity) { policy.requirePermission(identity, "knowledge:authorize"); }
    private static void validate(ConnectorInput input) {
        if (input == null || blank(input.connectorId()) || blank(input.displayName()) || blank(input.secretRef()) || blank(input.endpoint())
                || !(input.endpoint().startsWith("https://") || input.endpoint().startsWith("http://"))) throw new ClientException("连接器配置不合法");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    public record ConnectorInput(String connectorId, String displayName, String endpoint, String secretRef) { }
    public record ReviewInput(String reviewStatus, boolean enabled) { }
    public record ConnectorRow(String tenantId, String connectorId, String displayName, String endpoint, String secretRef, String status, String updatedBy, Timestamp updatedAt) { }
    public record ConnectorView(String tenantId, String connectorId, String displayName, String endpoint, String status, Timestamp updatedAt) { }
    public record ToolView(String toolId, String title, String description, String reviewStatus, boolean enabled, Timestamp discoveredAt, int contractVersion, String schemaHash) { }
    private record ToolContractRow(int contractVersion, String schemaHash) { }
}
