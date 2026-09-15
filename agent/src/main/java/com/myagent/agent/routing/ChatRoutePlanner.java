package com.myagent.agent.routing;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.trace.RagTraceNode;
import com.myagent.rag.core.intent.IntentClassifier;
import com.myagent.rag.core.intent.IntentClassificationException;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
import com.myagent.rag.enums.IntentKind;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** 将意图分类与权限路由组合成聊天执行可直接消费的路由计划。 */
@Service
@RequiredArgsConstructor
public class ChatRoutePlanner {

    private final IntentClassifier intentClassifier;
    private final RoutingService routingService;
    @Qualifier("intentClassifyExecutor")
    private final Executor intentClassifyExecutor;

    /** Top1 与 Top2 小于该分差时不能凭排序先后直接执行。 */
    @Value("${myagent.routing.ambiguity.scoreGap:0.10}")
    private double ambiguityScoreGap = 0.10D;

    @RagTraceNode(name = "chat-route-plan", type = "ROUTING")
    public ChatRoutePlan plan(String question, boolean hasPendingAction, ExecutionIdentity identity) {
        return planBatch(question, hasPendingAction, identity).plan();
    }

    /** 活动工作流的短回答属于槽位输入，必须跳过顶层意图分类并续接原流程。 */
    public ChatRoutePlan plan(String question, WorkflowContinuation continuation, ExecutionIdentity identity) {
        if (continuation == null) {
            return plan(question, false, identity);
        }
        RouteResult route = RouteResult.of(RouteDecision.WORKFLOW, continuation.workflowDefKey(),
                "继续当前工作流：" + continuation.status().name(), 1D);
        return new ChatRoutePlan(continuation.workflowDefKey(), "进行中的业务流程", IntentKind.WORKFLOW, route);
    }

    /**
     * 对明确的并列问题逐个分类，并仅合并可安全共同执行的知识库查询。
     * 异构执行路径不做隐式排序或并行执行，必须由用户明确下一步。
     */
    public ChatRouteBatch planBatch(String question, boolean hasPendingAction, ExecutionIdentity identity) {
        if (hasPendingAction) {
            return new ChatRouteBatch(planSingle(question, true, identity), List.of(question));
        }
        List<String> units = RouteUnitSplitter.split(question);
        List<ChatRoutePlan> plans = units.stream()
                .map(unit -> CompletableFuture.supplyAsync(
                        () -> planSingle(unit, false, identity), intentClassifyExecutor))
                .map(CompletableFuture::join)
                .toList();
        if (plans.size() == 1 || plans.stream().allMatch(this::isKnowledgePlan)) {
            // 多条 KB 查询仍由一个 RAG 请求回答原句，保留上下文且避免丢失任一只读子问题。
            return new ChatRouteBatch(plans.get(0), units);
        }
        return new ChatRouteBatch(new ChatRoutePlan(null, null, null, RouteResult.of(
                RouteDecision.CLARIFY, null, "检测到多个不同业务请求，请分别说明查询或办理需求", 0D)), units);
    }

    private boolean isKnowledgePlan(ChatRoutePlan plan) {
        return plan.route().getDecision() == RouteDecision.SYSTEM && plan.intentKind() == IntentKind.KB;
    }

    private ChatRoutePlan planSingle(String question, boolean hasPendingAction, ExecutionIdentity identity) {
        // 已生成的确认动作必须能在分类模型不可用时继续；具体目标由服务端路由配置决定。
        if (hasPendingAction) {
            RouteResult route = routingService.route(null, IntentKind.WORKFLOW, 1D, true, identity);
            return new ChatRoutePlan(null, "待确认业务", IntentKind.WORKFLOW, route);
        }
        List<NodeScore> candidates;
        try {
            candidates = intentClassifier.classifyTargets(question);
        } catch (IntentClassificationException e) {
            return new ChatRoutePlan(null, null, null, RouteResult.of(
                    RouteDecision.CLARIFY, null, "暂时无法识别，请稍后重试", 0D));
        }
        List<NodeScore> validCandidates = candidates == null ? List.of() : candidates.stream()
                .filter(candidate -> candidate != null && candidate.getNode() != null)
                .sorted(Comparator.comparingDouble(NodeScore::getScore).reversed())
                .toList();
        NodeScore best = validCandidates.isEmpty() ? null : validCandidates.get(0);
        if (best == null) {
            return new ChatRoutePlan(null, null, null, RouteResult.of(
                    RouteDecision.CLARIFY, null, "未识别到明确意图，请补充业务对象或操作", 0D));
        }

        String ambiguityReason = ambiguityReason(validCandidates);
        if (ambiguityReason != null) {
            return new ChatRoutePlan(null, null, null, RouteResult.of(
                    RouteDecision.CLARIFY, null, ambiguityReason, best.getScore()));
        }

        IntentNode node = best.getNode();
        IntentKind kind = node.getKind() == null ? IntentKind.KB : node.getKind();
        RouteResult route = routingService.route(
                node.getId(), kind, best.getScore(), hasPendingAction, identity);
        return new ChatRoutePlan(node.getId(), node.getName(), kind, route);
    }

    private String ambiguityReason(List<NodeScore> candidates) {
        if (candidates.size() < 2) {
            return null;
        }
        NodeScore first = candidates.get(0);
        NodeScore second = candidates.get(1);
        IntentKind firstKind = kindOf(first);
        IntentKind secondKind = kindOf(second);
        if (firstKind != secondKind) {
            return "候选意图类型冲突，请明确希望查询、办理还是执行操作";
        }
        if (first.getScore() - second.getScore() < ambiguityScoreGap) {
            return "候选意图置信度接近，请补充业务对象或操作";
        }
        return null;
    }

    private IntentKind kindOf(NodeScore score) {
        return score.getNode().getKind() == null ? IntentKind.KB : score.getNode().getKind();
    }
}
