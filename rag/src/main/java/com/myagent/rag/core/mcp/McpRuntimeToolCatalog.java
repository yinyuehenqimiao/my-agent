package com.myagent.rag.core.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.tool.RuntimeToolCatalog;
import com.myagent.framework.tool.ToolDefinition;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 数据库驱动的 ERP/MCP 运行时目录。发现不等于可用，只有审核并启用的工具才能解析。 */
@Component
public class McpRuntimeToolCatalog implements RuntimeToolCatalog {
    public static final String PREFIX = "mcp:";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final McpRuntimeInvoker invoker;
    private final McpCatalogRevisionService revisions;

    public McpRuntimeToolCatalog(JdbcTemplate jdbc, ObjectMapper json, McpRuntimeInvoker invoker,
                                 McpCatalogRevisionService revisions) {
        this.jdbc = jdbc;
        this.json = json;
        this.invoker = invoker;
        this.revisions = revisions;
    }

    @Override
    public Optional<ResolvedTool> resolve(ExecutionIdentity identity, String toolName, ToolDefinition.Source source) {
        ToolKey key = ToolKey.parse(toolName);
        if (identity == null || key == null) return Optional.empty();
        return query(identity.getTenantId(), source, key.connectorId(), key.toolId()).stream()
                .findFirst().map(this::resolved);
    }

    @Override
    public List<ToolDefinition> list(ExecutionIdentity identity, ToolDefinition.Source source) {
        if (identity == null || identity.getTenantId() == null) return List.of();
        return query(identity.getTenantId(), source, null, null).stream().map(this::definition).toList();
    }

    @Override
    public long revision(ExecutionIdentity identity) {
        return identity == null ? 0L : revisions.current(identity.getTenantId());
    }

    private List<Row> query(String tenantId, ToolDefinition.Source source, String connectorId, String toolId) {
        StringBuilder sql = new StringBuilder("""
                SELECT t.connector_id,t.tool_id,t.title,t.description,t.tool_schema,t.permission_code,
                       t.read_only,t.contract_version,t.schema_hash,c.endpoint,c.secret_ref
                  FROM mcp_catalog_tool t
                  JOIN mcp_connector c ON c.tenant_id=t.tenant_id AND c.connector_id=t.connector_id
                 WHERE t.tenant_id=? AND t.review_status='APPROVED' AND t.enabled=TRUE
                   AND c.status='DISCOVERED'
                """);
        java.util.ArrayList<Object> args = new java.util.ArrayList<>();
        args.add(tenantId);
        if (source == ToolDefinition.Source.REACT) {
            sql.append(" AND EXISTS (SELECT 1 FROM mcp_intent_binding b WHERE b.tenant_id=t.tenant_id AND b.connector_id=t.connector_id AND b.tool_id=t.tool_id AND b.status='PUBLISHED')");
        }
        if (connectorId != null) {
            sql.append(" AND t.connector_id=? AND t.tool_id=?");
            args.add(connectorId);
            args.add(toolId);
        }
        sql.append(" ORDER BY t.connector_id,t.tool_id");
        return jdbc.query(sql.toString(), (rs, rowNum) -> new Row(
                rs.getString("connector_id"), rs.getString("tool_id"), rs.getString("title"),
                rs.getString("description"), rs.getString("tool_schema"), rs.getString("permission_code"),
                rs.getBoolean("read_only"), rs.getInt("contract_version"), rs.getString("schema_hash"),
                rs.getString("endpoint"), rs.getString("secret_ref")), args.toArray());
    }

    private ResolvedTool resolved(Row row) {
        ToolDefinition definition = definition(row);
        return new ResolvedTool(definition, invocation -> invoker.invoke(
                new McpRuntimeInvoker.RuntimeConnection(row.endpoint(), row.secretRef(), row.toolId()), invocation));
    }

    @SuppressWarnings("unchecked")
    private ToolDefinition definition(Row row) {
        Map<String, Object> inputSchema = Map.of("type", "object", "properties", Map.of());
        if (row.toolSchema() != null && !row.toolSchema().isBlank()) {
            try {
                Tool tool = json.readValue(row.toolSchema(), Tool.class);
                if (tool.inputSchema() != null) {
                    inputSchema = json.convertValue(tool.inputSchema(), new TypeReference<>() { });
                }
            } catch (Exception ex) {
                throw new IllegalStateException("无法读取已审核工具 Schema：" + namespaced(row), ex);
            }
        }
        boolean readOnly = row.readOnly();
        List<ToolDefinition.Source> sources = readOnly
                ? List.of(ToolDefinition.Source.REACT, ToolDefinition.Source.WORKFLOW,
                ToolDefinition.Source.MCP, ToolDefinition.Source.ADMIN)
                : List.of(ToolDefinition.Source.WORKFLOW, ToolDefinition.Source.MCP, ToolDefinition.Source.ADMIN);
        return ToolDefinition.builder()
                .name(namespaced(row))
                .description(row.description() == null ? row.title() : row.description())
                .inputSchema(inputSchema)
                .outputSchema(Map.of())
                .readOnly(readOnly)
                .riskLevel(readOnly ? ToolDefinition.RiskLevel.LOW : ToolDefinition.RiskLevel.HIGH)
                .timeoutMs(60_000)
                .maxRetries(0)
                .idempotent(!readOnly)
                .requiresConfirmation(!readOnly)
                .sensitiveFields(List.of())
                .allowedSources(sources)
                .permissionCode(row.permissionCode())
                .enabled(true)
                .build();
    }

    public static String namespaced(String connectorId, String toolId) {
        return PREFIX + connectorId + ":" + toolId;
    }

    private String namespaced(Row row) {
        return namespaced(row.connectorId(), row.toolId());
    }

    private record ToolKey(String connectorId, String toolId) {
        private static ToolKey parse(String name) {
            if (name == null || !name.startsWith(PREFIX)) return null;
            String value = name.substring(PREFIX.length());
            int separator = value.indexOf(':');
            if (separator < 1 || separator == value.length() - 1) return null;
            return new ToolKey(value.substring(0, separator), value.substring(separator + 1));
        }
    }

    private record Row(String connectorId, String toolId, String title, String description,
                       String toolSchema, String permissionCode, boolean readOnly, int contractVersion,
                       String schemaHash, String endpoint, String secretRef) { }
}
