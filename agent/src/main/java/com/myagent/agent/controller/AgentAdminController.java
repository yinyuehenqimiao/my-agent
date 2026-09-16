package com.myagent.agent.controller;

import com.myagent.agent.routing.ChatRoutePlan;
import com.myagent.agent.routing.ChatRoutePlanner;
import com.myagent.agent.routing.RouteBindingService;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.web.Results;
import com.myagent.rag.enums.IntentKind;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 工作流后台使用的真实工具目录和路由模拟接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent/admin")
public class AgentAdminController {

    private final ToolGateway toolGateway;
    private final ChatRoutePlanner routePlanner;
    private final RouteBindingService routeBindingService;
    private final PolicyDecisionService policyDecisionService;

    /** 分页返回工具元数据；工具处理器和执行入口不会暴露给管理端。 */
    @GetMapping("/tools")
    public Result<Map<String, Object>> listTools(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "ADMIN") ToolDefinition.Source source) {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "tool:view");
        List<ToolDefinition> all = toolGateway.listDefinitions(identity, source);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<ToolDefinition> filtered = kw.isEmpty() ? all : all.stream()
                .filter(tool -> containsIgnoreCase(tool.getName(), kw)
                        || containsIgnoreCase(tool.getDescription(), kw)
                        || containsIgnoreCase(tool.getPermissionCode(), kw))
                .toList();
        Map<String, Object> page = paginate(filtered, current, size);
        page.put("catalogRevision", toolGateway.catalogRevision(identity));
        page.put("source", source.name());
        return Results.success(page);
    }

    @GetMapping("/routes")
    public Result<Map<String, Object>> listRoutes(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "route:view");
        List<RouteBindingService.RouteBinding> all = routeBindingService.list(identity);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<RouteBindingService.RouteBinding> filtered = kw.isEmpty() ? all : all.stream()
                .filter(route -> containsIgnoreCase(route.routeKey(), kw)
                        || containsIgnoreCase(route.intentCode(), kw)
                        || containsIgnoreCase(route.intentName(), kw)
                        || containsIgnoreCase(route.target(), kw))
                .toList();
        return Results.success(paginate(filtered, current, size));
    }

    @GetMapping("/routes/options")
    public Result<RouteBindingService.RouteOptions> routeOptions() {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "route:view");
        return Results.success(routeBindingService.options(identity));
    }

    @PutMapping("/routes/{routeKey}")
    public Result<RouteBindingService.RouteBinding> saveRoute(
            @PathVariable String routeKey, @RequestBody RouteBindingService.SaveRequest request) {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "route:edit");
        return Results.success(routeBindingService.save(routeKey, request, identity));
    }

    @DeleteMapping("/routes/{routeKey}")
    public Result<Void> deleteRoute(@PathVariable String routeKey) {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policyDecisionService.requirePermission(identity, "route:edit");
        routeBindingService.delete(routeKey, identity);
        return Results.success();
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

        ChatRoutePlan plan = routePlanner.plan(
                question.trim(), Boolean.TRUE.equals(request.hasPendingAction()), identity);
        RouteResult route = plan.route();
        return Results.success(new RouteSimulationResponse(
                plan.intentCode(), plan.intentName(), plan.intentKind(), route.getConfidence(),
                route.getDecision(), route.getTarget(), route.getReason()));
    }

    private static boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static <T> Map<String, Object> paginate(List<T> source, long current, long size) {
        long safeCurrent = Math.max(1L, current);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        long total = source.size();
        int from = (int) Math.min((safeCurrent - 1L) * safeSize, total);
        int to = (int) Math.min(from + safeSize, total);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", source.subList(from, to));
        page.put("total", total);
        page.put("current", safeCurrent);
        page.put("size", safeSize);
        return page;
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
