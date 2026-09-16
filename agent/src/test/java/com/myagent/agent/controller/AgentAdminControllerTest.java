package com.myagent.agent.controller;

import com.myagent.agent.routing.ChatRoutePlan;
import com.myagent.agent.routing.ChatRoutePlanner;
import com.myagent.agent.routing.RouteBindingService;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.rag.enums.IntentKind;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentAdminControllerTest {

    private ToolGateway gateway;
    private ChatRoutePlanner routePlanner;
    private RouteBindingService routeBindingService;
    private PolicyDecisionService policy;
    private AgentAdminController controller;

    @BeforeEach
    void setUp() {
        gateway = mock(ToolGateway.class);
        routePlanner = mock(ChatRoutePlanner.class);
        routeBindingService = mock(RouteBindingService.class);
        policy = mock(PolicyDecisionService.class);
        controller = new AgentAdminController(gateway, routePlanner, routeBindingService, policy);
        UserContext.set(LoginUser.builder().userId("u1").username("测试管理员").tenantId("default").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void toolCatalogRequiresDedicatedViewPermission() {
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("tool:view"));

        assertThatThrownBy(() -> controller.listTools(1, 20, null, ToolDefinition.Source.ADMIN))
                .isInstanceOf(ClientException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void routeSimulationUsesRealClassifierAndCurrentIdentity() {
        when(routePlanner.plan(eq("我想请年假"), eq(false), any())).thenReturn(new ChatRoutePlan(
                "leave-approval", "请假审批", IntentKind.WORKFLOW,
                RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 0.93)));

        var result = controller.simulate(new AgentAdminController.RouteSimulationRequest("我想请年假", false));
        var data = result.getData();

        assertThat(data.intentName()).isEqualTo("请假审批");
        assertThat(data.decision()).isEqualTo(RouteDecision.WORKFLOW);
        assertThat(data.target()).isEqualTo("leave-approval");
        verify(policy).requirePermission(any(), eq("route:simulate"));
        verify(routePlanner).plan(eq("我想请年假"), eq(false),
                argThat(identity -> "u1".equals(identity.getUserId())));
    }

    @Test
    void routeSimulationWithoutCandidatesReturnsClarificationInsteadOfInventingRoute() {
        when(routePlanner.plan(eq("无法识别的问题"), eq(false), any())).thenReturn(new ChatRoutePlan(
                null, null, null, RouteResult.of(RouteDecision.CLARIFY, null, "未识别到明确意图", 0D)));

        var data = controller.simulate(
                new AgentAdminController.RouteSimulationRequest("无法识别的问题", false)).getData();

        assertThat(data.decision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(data.reason()).contains("未识别");
    }

    @Test
    void routeReadsAndWritesUseSeparatePermissions() {
        when(routeBindingService.list(any())).thenReturn(List.of());
        controller.listRoutes(1, 20, null);
        controller.routeOptions();
        controller.saveRoute("leave-route", new RouteBindingService.SaveRequest(
                "leave-approval", RouteBindingService.TargetType.WORKFLOW, "leave-approval"));
        controller.deleteRoute("leave-route");

        verify(policy, times(2)).requirePermission(any(), eq("route:view"));
        verify(policy, times(2)).requirePermission(any(), eq("route:edit"));
        verify(routeBindingService).list(any());
        verify(routeBindingService).save(eq("leave-route"), any(),
                argThat(identity -> "default".equals(identity.getTenantId())));
        verify(routeBindingService).delete(eq("leave-route"),
                argThat(identity -> "default".equals(identity.getTenantId())));
    }
}
