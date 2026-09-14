package com.myagent.agent.routing;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.rag.core.intent.IntentClassifier;
import com.myagent.rag.core.intent.IntentClassificationException;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
import com.myagent.rag.enums.IntentKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatRoutePlannerTest {

    private final IntentClassifier classifier = mock(IntentClassifier.class);
    private final RoutingService routingService = mock(RoutingService.class);
    private final ChatRoutePlanner planner = new ChatRoutePlanner(classifier, routingService, Runnable::run);
    private final LoginUser identity = LoginUser.builder().userId("u-1").username("tester").build();

    @Test
    void shouldChooseHighestValidCandidateAndPreserveIntentKind() {
        NodeScore lower = score("leave-query", IntentKind.WORKFLOW, 0.71);
        NodeScore higher = score("leave-approval", IntentKind.WORKFLOW, 0.94);
        when(classifier.classifyTargets("我要请年假")).thenReturn(List.of(lower, higher));
        when(routingService.route("leave-approval", IntentKind.WORKFLOW, 0.94, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 0.94));

        ChatRoutePlan plan = planner.plan("我要请年假", false, identity);

        assertThat(plan.intentCode()).isEqualTo("leave-approval");
        assertThat(plan.intentKind()).isEqualTo(IntentKind.WORKFLOW);
        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.WORKFLOW);
        verify(routingService).route("leave-approval", IntentKind.WORKFLOW, 0.94, false, identity);
    }

    @Test
    void shouldClarifyWithoutCallingRoutingServiceWhenClassifierHasNoCandidate() {
        when(classifier.classifyTargets("不明确的问题")).thenReturn(List.of());

        ChatRoutePlan plan = planner.plan("不明确的问题", false, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(plan.route().getReason()).contains("未识别到明确意图");
    }

    @Test
    void shouldReportClassifierServiceFailureInsteadOfTreatingItAsSemanticAmbiguity() {
        when(classifier.classifyTargets("查询我的年假"))
                .thenThrow(new IntentClassificationException("意图分类服务不可用"));

        ChatRoutePlan plan = planner.plan("查询我的年假", false, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(plan.route().getReason()).contains("暂时无法识别，请稍后重试");
        verifyNoInteractions(routingService);
    }

    @Test
    void shouldClarifyWhenTopCandidatesAreTooClose() {
        when(classifier.classifyTargets("查询年假还是发起请假"))
                .thenReturn(List.of(
                        score("leave-query", IntentKind.MCP, 0.91),
                        score("attendance-query", IntentKind.MCP, 0.85)));

        ChatRoutePlan plan = planner.plan("查询年假还是发起请假", false, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(plan.route().getReason()).contains("候选意图置信度接近");
        verifyNoInteractions(routingService);
    }

    @Test
    void shouldClarifyWhenTopCandidatesHaveDifferentKinds() {
        when(classifier.classifyTargets("请帮我处理年假"))
                .thenReturn(List.of(
                        score("leave-policy", IntentKind.KB, 0.96),
                        score("leave-approval", IntentKind.WORKFLOW, 0.70)));

        ChatRoutePlan plan = planner.plan("请帮我处理年假", false, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(plan.route().getReason()).contains("候选意图类型冲突");
        verifyNoInteractions(routingService);
    }

    @Test
    void shouldMergeMultipleKnowledgeRouteUnitsIntoOneRagPlan() {
        when(classifier.classifyTargets("查询年假制度"))
                .thenReturn(List.of(score("leave-policy", IntentKind.KB, 0.95)));
        when(classifier.classifyTargets("查询远程办公制度"))
                .thenReturn(List.of(score("remote-policy", IntentKind.KB, 0.94)));
        when(routingService.route("leave-policy", IntentKind.KB, 0.95, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.95));
        when(routingService.route("remote-policy", IntentKind.KB, 0.94, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.94));

        ChatRouteBatch batch = planner.planBatch("查询年假制度，并且查询远程办公制度", false, identity);

        assertThat(batch.routeUnits()).containsExactly("查询年假制度", "查询远程办公制度");
        assertThat(batch.plan().route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        assertThat(batch.plan().intentKind()).isEqualTo(IntentKind.KB);
    }

    @Test
    void shouldClarifyInsteadOfUnorderedExecutionForHeterogeneousRouteUnits() {
        when(classifier.classifyTargets("查询年假制度"))
                .thenReturn(List.of(score("leave-policy", IntentKind.KB, 0.95)));
        when(classifier.classifyTargets("我要申请年假"))
                .thenReturn(List.of(score("leave-approval", IntentKind.WORKFLOW, 0.96)));
        when(routingService.route("leave-policy", IntentKind.KB, 0.95, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.95));
        when(routingService.route("leave-approval", IntentKind.WORKFLOW, 0.96, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 0.96));

        ChatRouteBatch batch = planner.planBatch("查询年假制度；我要申请年假", false, identity);

        assertThat(batch.plan().route().getDecision()).isEqualTo(RouteDecision.CLARIFY);
        assertThat(batch.plan().route().getReason()).contains("多个不同业务请求");
    }

    @Test
    void shouldContinuePendingWorkflowWithoutDependingOnIntentClassifier() {
        when(routingService.route(null, IntentKind.WORKFLOW, 1D, true, identity))
                .thenReturn(RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "存在待确认/审批动作", 1D));

        ChatRoutePlan plan = planner.plan("确认", true, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.WORKFLOW);
        assertThat(plan.route().getTarget()).isEqualTo("leave-approval");
        verifyNoInteractions(classifier);
    }

    private NodeScore score(String id, IntentKind kind, double score) {
        return NodeScore.builder()
                .node(IntentNode.builder().id(id).name(id).kind(kind).build())
                .score(score)
                .build();
    }
}
