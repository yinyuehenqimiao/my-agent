package com.myagent.agent.routing;

import com.myagent.agent.tool.AgentToolCatalog;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.rag.enums.IntentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** 管理意图到固定工作流/ReAct 的生产路由绑定。所有读写都以服务端身份中的租户为边界。 */
@Service
@RequiredArgsConstructor
public class RouteBindingService {

    private static final Pattern ROUTE_KEY = Pattern.compile("[a-z0-9][a-z0-9-]{0,127}");
    private static final String DEFAULT_REACT_CONFIG = "default";

    private final JdbcTemplate jdbc;
    private final ObjectProvider<AgentToolCatalog> toolCatalogProvider;

    public List<RouteBinding> list(ExecutionIdentity identity) {
        String tenantId = tenantOf(identity);
        return jdbc.query("""
                        SELECT r.route_key,r.intent_code,n.name AS intent_name,r.workflow_def_key,r.react_config,r.update_time
                        FROM wf_route r
                        LEFT JOIN t_intent_node n ON n.intent_code=r.intent_code AND n.deleted=0
                        WHERE r.tenant_id=? ORDER BY r.route_key
                        """,
                (rs, rowNum) -> new RouteBinding(
                        rs.getString("route_key"), rs.getString("intent_code"), rs.getString("intent_name"),
                        rs.getString("workflow_def_key") == null ? TargetType.REACT : TargetType.WORKFLOW,
                        rs.getString("workflow_def_key") == null
                                ? rs.getString("react_config") : rs.getString("workflow_def_key"),
                        rs.getTimestamp("update_time")), tenantId);
    }

    public RouteOptions options(ExecutionIdentity identity) {
        String tenantId = tenantOf(identity);
        List<Option> intents = jdbc.query("""
                        SELECT n.intent_code,n.name FROM t_intent_node n
                        WHERE n.enabled=1 AND n.deleted=0 AND n.kind IN (2,3)
                          AND NOT EXISTS (SELECT 1 FROM t_intent_node c
                                          WHERE c.parent_code=n.intent_code AND c.enabled=1 AND c.deleted=0)
                        ORDER BY n.name,n.intent_code
                        """,
                (rs, rowNum) -> new Option(rs.getString("intent_code"), rs.getString("name")));
        List<Option> workflows = jdbc.query("""
                        SELECT def_key,name FROM wf_definition
                        WHERE tenant_id=? AND current_published_version_id IS NOT NULL ORDER BY name,def_key
                        """,
                (rs, rowNum) -> new Option(rs.getString("def_key"), rs.getString("name")), tenantId).stream()
                .filter(option -> supportsWorkflow(option.value()))
                .toList();
        return new RouteOptions(intents, workflows,
                List.of(new Option(DEFAULT_REACT_CONFIG, "标准开放问答（受工具权限控制）")));
    }

    @Transactional
    public RouteBinding save(String routeKey, SaveRequest request, ExecutionIdentity identity) {
        String tenantId = tenantOf(identity);
        validateRouteKey(routeKey);
        if (request == null || request.intentCode() == null || request.intentCode().isBlank()) {
            throw new ClientException("请选择业务意图");
        }
        if (request.targetType() == null || request.target() == null || request.target().isBlank()) {
            throw new ClientException("请选择执行方式和目标");
        }
        String intentCode = request.intentCode().trim();
        int intentKind = requireSelectableIntent(intentCode);
        String target = request.target().trim();
        if (request.targetType() == TargetType.WORKFLOW) {
            if (intentKind != IntentKind.WORKFLOW.getCode()) {
                throw new ClientException("只有工作流意图可以绑定固定流程");
            }
            requirePublishedSupportedWorkflow(target, tenantId);
        } else {
            if (intentKind != IntentKind.MCP.getCode()) {
                throw new ClientException("只有开放工具意图可以绑定 ReAct");
            }
            if (!DEFAULT_REACT_CONFIG.equals(target)) {
                throw new ClientException("不支持的 ReAct 配置");
            }
        }

        Integer conflict = jdbc.queryForObject(
                "SELECT count(*) FROM wf_route WHERE tenant_id=? AND intent_code=? AND route_key<>?",
                Integer.class, tenantId, intentCode, routeKey);
        if (conflict != null && conflict > 0) {
            throw new ClientException("该业务意图已绑定执行管线");
        }
        Integer existing = jdbc.queryForObject(
                "SELECT count(*) FROM wf_route WHERE tenant_id=? AND route_key=?", Integer.class, tenantId, routeKey);
        try {
            if (existing != null && existing > 0) {
                jdbc.update("""
                                UPDATE wf_route SET intent_code=?,workflow_def_key=?,react_config=?,update_time=CURRENT_TIMESTAMP
                                WHERE tenant_id=? AND route_key=?
                                """,
                        intentCode, request.targetType() == TargetType.WORKFLOW ? target : null,
                        request.targetType() == TargetType.REACT ? target : null, tenantId, routeKey);
                writeAudit("ROUTE_UPDATE", routeKey, identity, intentCode, request.targetType(), target);
            } else {
                jdbc.update("""
                                INSERT INTO wf_route(route_key,intent_code,workflow_def_key,react_config,tenant_id,create_time,update_time)
                                VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                                """,
                        routeKey, intentCode, request.targetType() == TargetType.WORKFLOW ? target : null,
                        request.targetType() == TargetType.REACT ? target : null, tenantId);
                writeAudit("ROUTE_CREATE", routeKey, identity, intentCode, request.targetType(), target);
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ClientException("路由键或业务意图已绑定，请刷新后重试");
        }
        return list(identity).stream().filter(route -> route.routeKey().equals(routeKey)).findFirst().orElseThrow();
    }

    @Transactional
    public void delete(String routeKey, ExecutionIdentity identity) {
        String tenantId = tenantOf(identity);
        validateRouteKey(routeKey);
        int changed = jdbc.update("DELETE FROM wf_route WHERE tenant_id=? AND route_key=?", tenantId, routeKey);
        if (changed == 0) {
            throw new ClientException("路由绑定不存在或不属于当前租户");
        }
        writeAudit("ROUTE_DELETE", routeKey, identity, null, null, null);
    }

    private int requireSelectableIntent(String intentCode) {
        List<Integer> kinds = jdbc.query("""
                        SELECT n.kind FROM t_intent_node n
                        WHERE n.intent_code=? AND n.enabled=1 AND n.deleted=0
                          AND NOT EXISTS (SELECT 1 FROM t_intent_node c
                                          WHERE c.parent_code=n.intent_code AND c.enabled=1 AND c.deleted=0)
                        """, (rs, rowNum) -> rs.getInt(1), intentCode);
        if (kinds.isEmpty() || (kinds.get(0) != IntentKind.WORKFLOW.getCode()
                && kinds.get(0) != IntentKind.MCP.getCode())) {
            throw new ClientException("业务意图不存在、已停用或不是可绑定的末级意图");
        }
        return kinds.get(0);
    }

    private void requirePublishedSupportedWorkflow(String defKey, String tenantId) {
        Integer count = jdbc.queryForObject("""
                        SELECT count(*) FROM wf_definition
                        WHERE tenant_id=? AND def_key=? AND current_published_version_id IS NOT NULL
                        """, Integer.class, tenantId, defKey);
        if (count == null || count == 0) {
            throw new ClientException("只能绑定当前租户中已发布的工作流");
        }
        if (!supportsWorkflow(defKey)) {
            throw new ClientException("该固定流程尚未接入运行时工具集");
        }
    }

    private boolean supportsWorkflow(String defKey) {
        AgentToolCatalog catalog = toolCatalogProvider.getIfAvailable();
        return catalog != null && catalog.supportsWorkflow(defKey);
    }

    private void writeAudit(String operation, String routeKey, ExecutionIdentity identity,
                            String intentCode, TargetType targetType, String target) {
        String detail = intentCode == null ? null
                : "intentCode=" + intentCode + ",targetType=" + targetType + ",target=" + target;
        jdbc.update("""
                        INSERT INTO operation_audit(operation,operator_id,target_type,target_id,tenant_id,detail,created_time)
                        VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)
                        """, operation, identity.getUserId(), "wf_route", routeKey, tenantOf(identity), detail);
    }

    private static void validateRouteKey(String routeKey) {
        if (routeKey == null || !ROUTE_KEY.matcher(routeKey).matches()) {
            throw new ClientException("路由键仅支持小写字母、数字和短横线，最长 128 个字符");
        }
    }

    private static String tenantOf(ExecutionIdentity identity) {
        if (identity == null || identity.getTenantId() == null || identity.getTenantId().isBlank()) {
            throw new ClientException("登录身份缺少租户信息");
        }
        return identity.getTenantId();
    }

    public enum TargetType { WORKFLOW, REACT }

    public record SaveRequest(String intentCode, TargetType targetType, String target) { }

    public record RouteBinding(String routeKey, String intentCode, String intentName,
                               TargetType targetType, String target, java.sql.Timestamp updateTime) { }

    public record Option(String value, String label) { }

    public record RouteOptions(List<Option> intents, List<Option> workflows, List<Option> reactConfigs) { }
}
