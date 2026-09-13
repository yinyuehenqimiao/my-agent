package com.myagent.agent.routing;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.rag.enums.IntentKind;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 路由决策服务：固定业务走 Workflow，开放问题进入受控 ReAct。
 * <p>
 * 路由优先级：确认与审批 → 高置信固定流程 → 参数澄清 → 授权 ReAct → 系统回答 → 拒绝。
 * 高风险意图使用更高置信度阈值。Prompt 不承担权限判断，Tool Gateway 做执行时二次鉴权。
 * 模型不可用时，固定业务入口仍可运行工作流（路由不依赖模型自由规划）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoutingService {

    private final JdbcTemplate jdbc;
    private final PolicyDecisionService policyDecisionService;

    /** 固定流程置信阈值 */
    @Value("${myagent.routing.workflow.threshold:0.70}")
    private double workflowThreshold;

    /** ReAct 置信阈值 */
    @Value("${myagent.routing.react.threshold:0.60}")
    private double reactThreshold;

    /** 高风险意图 ReAct 阈信阈值（更高） */
    @Value("${myagent.routing.react.highRiskThreshold:0.85}")
    private double highRiskThreshold;

    /** 高风险意图码前缀（年假/审批/写操作） */
    @Value("${myagent.routing.highRiskPrefixes:leave-approval,leave,approval,payment,write}")
    private String highRiskPrefixesRaw;

    /** 当前仅有请假确认动作；扩展多流程时应由待办记录携带 defKey。 */
    @Value("${myagent.routing.pendingWorkflowDefKey:leave-approval}")
    private String pendingWorkflowDefKey;

    /**
     * 路由决策。
     *
     * @param intentCode      命中的意图码（可为空）
     * @param kind             意图类型
     * @param confidence       置信度 [0,1]
     * @param hasPendingAction  是否有待确认/审批动作（优先走 Workflow）
     * @param identity         执行身份（用于授权判断）
     */
    public RouteResult route(String intentCode, IntentKind kind, double confidence,
                             boolean hasPendingAction, ExecutionIdentity identity) {
        // 1. 待确认/审批优先走 Workflow（继续已有流程）
        if (hasPendingAction) {
            return RouteResult.of(RouteDecision.WORKFLOW, pendingWorkflowDefKey, "存在待确认/审批动作", confidence);
        }
        // 2. 查 wf_route 绑定
        Map<String, Object> binding = lookupRoute(intentCode);
        if (binding != null) {
            String workflowDefKey = (String) binding.get("workflow_def_key");
            String reactConfig = (String) binding.get("react_config");
            if (workflowDefKey != null && !workflowDefKey.isBlank()) {
                if (confidence >= workflowThreshold) {
                    return RouteResult.of(RouteDecision.WORKFLOW, workflowDefKey, "高置信固定流程", confidence);
                }
                return RouteResult.of(RouteDecision.CLARIFY, null, "固定流程置信不足", confidence);
            }
            if (reactConfig != null && !reactConfig.isBlank()) {
                return decideReact(intentCode, confidence, identity);
            }
        }
        // 3. 无绑定：按意图类型
        if (kind == IntentKind.KB) {
            return RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", confidence);
        }
        if (kind == IntentKind.SYSTEM) {
            return RouteResult.of(RouteDecision.SYSTEM, null, "系统回答", confidence);
        }
        if (kind == IntentKind.WORKFLOW) {
            // 工作流意图但无路由绑定 → 澄清
            return RouteResult.of(RouteDecision.CLARIFY, null, "工作流意图未绑定具体流程", confidence);
        }
        // 4. MCP/其他 → 默认尝试 ReAct（受授权约束）
        return decideReact(intentCode, confidence, identity);
    }

    /** ReAct 决策：授权 + 置信阈值；高风险用更高阈值；未授权 → 拒绝 */
    private RouteResult decideReact(String intentCode, double confidence, ExecutionIdentity identity) {
        double threshold = isHighRisk(intentCode) ? highRiskThreshold : reactThreshold;
        if (confidence < threshold) {
            return RouteResult.of(RouteDecision.CLARIFY, null, "ReAct 置信不足", confidence);
        }
        // 授权判断：ReAct 需要已认证身份；具体工具授权由 Tool Gateway 执行时二次鉴权
        if (identity == null || identity.getUserId() == null) {
            return RouteResult.of(RouteDecision.DENY, null, "缺少已认证身份", confidence);
        }
        // 高风险写操作需额外权限码（示例：workflow:submit）。此处仅做粗粒度；细粒度由 Tool Gateway。
        if (isHighRisk(intentCode) && !policyDecisionService.hasPermission(identity, "workflow:submit")) {
            return RouteResult.of(RouteDecision.DENY, null, "高风险操作未授权", confidence);
        }
        return RouteResult.of(RouteDecision.REACT, "default", "授权 ReAct", confidence);
    }

    private boolean isHighRisk(String intentCode) {
        if (intentCode == null) return false;
        for (String p : highRiskPrefixesRaw.split(",")) {
            if (!p.isBlank() && intentCode.toLowerCase().contains(p.trim().toLowerCase())) return true;
        }
        return false;
    }

    /** 查询 wf_route 绑定（按 intent_code） */
    private Map<String, Object> lookupRoute(String intentCode) {
        if (intentCode == null || intentCode.isBlank()) return null;
        try {
            return jdbc.queryForMap(
                    "SELECT workflow_def_key, react_config FROM wf_route WHERE intent_code=? AND tenant_id='default' LIMIT 1",
                    intentCode);
        } catch (EmptyResultDataAccessException e) {
            // 没有配置绑定时才按意图类型继续决策；数据库异常必须向上抛出，禁止故障时误入 ReAct。
            return null;
        }
    }
}
