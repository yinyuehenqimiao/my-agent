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
import static org.mockito.Mockito.never;
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
    void shouldRecognizeCapabilityDiscoveryWithoutCallingTheIntentModel() {
        ChatRoutePlan plan = planner.plan(
                "我能用你干什么，就是直接使用的工具，连接系统的", false, identity);

        assertThat(plan.intentCode()).isEqualTo("sys-about-bot");
        assertThat(plan.intentKind()).isEqualTo(IntentKind.SYSTEM);
        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        verifyNoInteractions(classifier, routingService);
    }

    @Test
    void shouldRouteCurrentTimeQuestionWithoutCallingTheIntentModel() {
        ChatRoutePlan plan = planner.plan("当前时间是什么时候", false, identity);

        assertThat(plan.intentCode()).isEqualTo("sys-current-time");
        assertThat(plan.intentKind()).isEqualTo(IntentKind.SYSTEM);
        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        verifyNoInteractions(classifier, routingService);
    }

    @Test
    void shouldRouteExplicitLeaveApplicationWithoutCallingTheIntentModel() {
        when(routingService.route("leave-approval", IntentKind.WORKFLOW, 1D, false, identity))
                .thenReturn(RouteResult.of(
                        RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 1D));

        ChatRoutePlan plan = planner.plan("请假26年9月20日-22日", false, identity);

        assertThat(plan.intentCode()).isEqualTo("leave-approval");
        assertThat(plan.intentKind()).isEqualTo(IntentKind.WORKFLOW);
        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.WORKFLOW);
        verifyNoInteractions(classifier);
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

    @Test
    void shouldContinueActiveWorkflowWithShortSlotAnswerWithoutClassifyingItAsANewIntent() {
        WorkflowContinuation continuation = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);

        ChatRoutePlan plan = planner.plan("旅游", continuation, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.WORKFLOW);
        assertThat(plan.route().getTarget()).isEqualTo("leave-approval");
        assertThat(plan.route().getReason()).contains("继续当前工作流");
        verifyNoInteractions(classifier, routingService);
    }

    @Test
    void shouldTemporarilyAnswerKnowledgeQuestionWithoutDiscardingActiveWorkflow() {
        WorkflowContinuation continuation = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);
        when(classifier.classifyTargets("公司的请假制度是什么"))
                .thenReturn(List.of(score("group-hr-policy", IntentKind.KB, 0.95)));
        when(routingService.route("group-hr-policy", IntentKind.KB, 0.95, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.95));

        ChatRoutePlan plan = planner.plan("公司的请假制度是什么", continuation, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        assertThat(plan.intentCode()).isEqualTo("group-hr-policy");
        assertThat(plan.intentKind()).isEqualTo(IntentKind.KB);
    }

    @Test
    void shouldResolveModificationFollowUpFromRecentlyCompletedLeaveWorkflow() {
        RecentWorkflowContext completedLeave = new RecentWorkflowContext(
                "leave-approval", WorkflowConversationStatus.COMPLETED);

        ChatRoutePlan plan = planner.plan("能修改吗", completedLeave, identity);

        assertThat(plan.route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        assertThat(plan.intentCode()).isEqualTo("sys-leave-modification");
        assertThat(plan.intentName()).isEqualTo("请假修改说明");
        verifyNoInteractions(classifier, routingService);
    }

    @Test
    void shouldDetourToRagAndReactThenResumeTheSameWorkflowSlot() {
        WorkflowContinuation collecting = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);
        when(classifier.classifyTargets("公司的请假制度是什么"))
                .thenReturn(List.of(score("group-hr-policy", IntentKind.KB, 0.95)));
        when(routingService.route("group-hr-policy", IntentKind.KB, 0.95, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.95));
        when(classifier.classifyTargets("我的年假还有多少"))
                .thenReturn(List.of(score("employee-self", IntentKind.MCP, 0.96)));
        when(routingService.route("employee-self", IntentKind.MCP, 0.96, false, identity))
                .thenReturn(RouteResult.of(RouteDecision.REACT, "default", "授权 ReAct", 0.96));

        ChatRoutePlan policy = planner.plan("公司的请假制度是什么", collecting, identity);
        ChatRoutePlan balance = planner.plan("我的年假还有多少", collecting, identity);
        ChatRoutePlan reason = planner.plan("我请假的理由是旅游", collecting, identity);

        assertThat(policy.route().getDecision()).isEqualTo(RouteDecision.SYSTEM);
        assertThat(policy.intentName()).isEqualTo("group-hr-policy");
        assertThat(balance.route().getDecision()).isEqualTo(RouteDecision.REACT);
        assertThat(balance.intentName()).isEqualTo("employee-self");
        assertThat(reason.route().getDecision()).isEqualTo(RouteDecision.WORKFLOW);
        assertThat(reason.route().getTarget()).isEqualTo("leave-approval");
        verify(classifier, never()).classifyTargets("我请假的理由是旅游");
    }

    private NodeScore score(String id, IntentKind kind, double score) {
        return NodeScore.builder()
                .node(IntentNode.builder().id(id).name(id).kind(kind).build())
                .score(score)
                .build();
    }
}
