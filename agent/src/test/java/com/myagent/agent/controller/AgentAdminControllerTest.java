package com.myagent.agent.controller;

import com.myagent.agent.routing.RoutingService;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.rag.core.intent.IntentClassifier;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
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
    private IntentClassifier classifier;
    private RoutingService routingService;
    private PolicyDecisionService policy;
    private AgentAdminController controller;

    @BeforeEach
    void setUp() {
        gateway = mock(ToolGateway.class);
        classifier = mock(IntentClassifier.class);
        routingService = mock(RoutingService.class);
        policy = mock(PolicyDecisionService.class);
        controller = new AgentAdminController(gateway, classifier, routingService, policy);
        UserContext.set(LoginUser.builder().userId("u1").username("测试管理员").tenantId("default").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void toolCatalogRequiresDedicatedViewPermission() {
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("tool:view"));

        assertThatThrownBy(controller::listTools).isInstanceOf(ClientException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void routeSimulationUsesRealClassifierAndCurrentIdentity() {
        IntentNode node = IntentNode.builder()
                .id("leave-approval")
                .name("请假审批")
                .kind(IntentKind.WORKFLOW)
                .build();
        when(classifier.classifyTargets("我想请年假")).thenReturn(List.of(NodeScore.builder().node(node).score(0.93).build()));
        when(routingService.route(eq("leave-approval"), eq(IntentKind.WORKFLOW), eq(0.93), eq(false), any()))
                .thenReturn(RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 0.93));

        var result = controller.simulate(new AgentAdminController.RouteSimulationRequest("我想请年假", false));
        var data = result.getData();

        assertThat(data.intentName()).isEqualTo("请假审批");
        assertThat(data.decision()).isEqualTo(RouteDecision.WORKFLOW);
        assertThat(data.target()).isEqualTo("leave-approval");
        verify(policy).requirePermission(any(), eq("route:simulate"));
        verify(routingService).route(eq("leave-approval"), eq(IntentKind.WORKFLOW), eq(0.93), eq(false),
                argThat(identity -> "u1".equals(identity.getUserId())));
    }

    @Test
    void routeSimulationWithoutCandidatesReturnsClarificationInsteadOfInventingRoute() {
        when(classifier.classifyTargets("无法识别的问题")).thenReturn(List.of());

        var data = controller.simulate(
                new AgentAdminController.RouteSimulationRequest("无法识别的问题", false)).getData();

        assertThat(data.decision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(data.reason()).contains("未识别");
        verifyNoInteractions(routingService);
    }
}
