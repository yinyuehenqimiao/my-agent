package com.myagent.agent.controller;

import com.myagent.agent.routing.RoutingService;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.web.Results;
import com.myagent.rag.core.intent.IntentClassifier;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
import com.myagent.rag.enums.IntentKind;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 工作流后台使用的真实工具目录和路由模拟接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/admin")
public class AgentAdminController {

    private final ToolGateway toolGateway;
    private final IntentClassifier intentClassifier;
    private final RoutingService routingService;
    private final PolicyDecisionService policyDecisionService;

    /** 仅返回工具元数据；工具处理器和执行入口不会暴露给管理端。 */
    @GetMapping("/tools")
    public Result<List<ToolDefinition>> listTools() {
        policyDecisionService.requirePermission(UserContext.requireIdentity(), "tool:view");
        return Results.success(toolGateway.listDefinitions());
    }

    /**
     * 使用当前登录人的真实权限快照执行模拟，禁止客户端伪造用户、租户或角色。
     */
    @PostMapping("/routes/simulate")
    public Result<RouteSimulationResponse> simulate(@RequestBody RouteSimulationRequest request) {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "route:simulate");
        String question = request == null ? null : request.question();
        if (question == null || question.isBlank()) {
            throw new ClientException("请输入要模拟的问题");
        }
        if (question.length() > 2_000) {
            throw new ClientException("模拟问题不能超过 2000 个字符");
        }

        List<NodeScore> candidates = intentClassifier.classifyTargets(question.trim());
        NodeScore best = candidates == null ? null : candidates.stream()
                .filter(score -> score != null && score.getNode() != null)
                .findFirst()
                .orElse(null);
        if (best == null) {
            return Results.success(new RouteSimulationResponse(
                    null, null, null, 0D, RouteDecision.CLARIFY, null, "未识别到明确意图，请补充业务对象或操作"));
        }

        IntentNode node = best.getNode();
        IntentKind kind = node.getKind() == null ? IntentKind.KB : node.getKind();
        RouteResult route = routingService.route(
                node.getId(), kind, best.getScore(), Boolean.TRUE.equals(request.hasPendingAction()), identity);
        return Results.success(new RouteSimulationResponse(
                node.getId(), node.getName(), kind, best.getScore(), route.getDecision(), route.getTarget(), route.getReason()));
    }

    public record RouteSimulationRequest(String question, Boolean hasPendingAction) {
    }

    public record RouteSimulationResponse(
            String intentCode,
            String intentName,
            IntentKind intentKind,
            double confidence,
            RouteDecision decision,
            String target,
            String reason) {
    }
}
