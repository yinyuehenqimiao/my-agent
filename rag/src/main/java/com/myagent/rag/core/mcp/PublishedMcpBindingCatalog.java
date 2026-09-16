package com.myagent.rag.core.mcp;

import com.myagent.framework.context.ExecutionIdentity;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/** 运行时只读取已发布、已审核且启用的绑定；草稿和停用项不可见。 */
@Component
public class PublishedMcpBindingCatalog {
    private final JdbcTemplate jdbc;
    public PublishedMcpBindingCatalog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<PublishedBinding> list(ExecutionIdentity identity) {
        return jdbc.query("""
                SELECT b.intent_code,b.connector_id,b.tool_id,c.endpoint,c.secret_ref
                  FROM mcp_intent_binding b JOIN mcp_catalog_tool t
                    ON t.tenant_id=b.tenant_id AND t.connector_id=b.connector_id AND t.tool_id=b.tool_id
                  JOIN mcp_connector c ON c.tenant_id=b.tenant_id AND c.connector_id=b.connector_id
                 WHERE b.tenant_id=? AND b.status='PUBLISHED'
                   AND t.review_status='APPROVED' AND t.enabled=TRUE
                 ORDER BY b.intent_code
                """, new DataClassRowMapper<>(PublishedBinding.class), identity.getTenantId());
    }

    /** secretRef 仅供服务端令牌签发器使用，不对前端或模型返回。 */
    public record PublishedBinding(String intentCode, String connectorId, String toolId, String endpoint, String secretRef) { }
}
