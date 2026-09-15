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
        if (!isLikelyWorkflowSlotAnswer(question)) {
            ChatRoutePlan independentPlan = planSingle(question, false, identity);
            RouteDecision independentDecision = independentPlan.route().getDecision();
            boolean sameWorkflow = independentDecision == RouteDecision.WORKFLOW
                    && continuation.workflowDefKey().equals(independentPlan.route().getTarget());
            if (independentDecision != RouteDecision.CLARIFY && !sameWorkflow) {
                // 临时查询不改变持久化的工作流状态；下一轮仍可继续补充原流程参数。
                return independentPlan;
            }
        }
        RouteResult route = RouteResult.of(RouteDecision.WORKFLOW, continuation.workflowDefKey(),
                "继续当前工作流：" + continuation.status().name(), 1D);
        return new ChatRoutePlan(continuation.workflowDefKey(), "进行中的业务流程", IntentKind.WORKFLOW, route);
    }

    /** 最近非活动流程只参与省略业务对象的追问，不抢占正常的新问题。 */
    public ChatRoutePlan plan(String question, RecentWorkflowContext recentContext, ExecutionIdentity identity) {
        if (recentContext != null
                && "leave-approval".equals(recentContext.workflowDefKey())
                && isModificationFollowUp(question)) {
            return new ChatRoutePlan("sys-leave-modification", "请假修改说明", IntentKind.SYSTEM,
                    RouteResult.of(RouteDecision.SYSTEM, null, "关联最近完成的请假流程", 1D));
        }
        return plan(question, false, identity);
    }

    /**
     * 工作流收集阶段的自由文本通常是短槽位值。出现明确查询/疑问信号时才调用完整意图分类，
     * 避免把“旅游”这类理由误当成新业务，也允许用户在填表途中查询制度、余额或权限。
     */
    private boolean isLikelyWorkflowSlotAnswer(String question) {
        if (question == null || question.isBlank()) {
            return true;
        }
        String normalized = question.replaceAll("[\\p{P}\\p{Z}]", "");
        if (question.contains("?") || question.contains("？")) {
            return false;
        }
        return java.util.stream.Stream.of(
                        "什么", "多少", "怎么", "如何", "哪些", "哪里", "哪天", "是否", "能否", "可以吗",
                        "查询", "查看", "制度", "政策", "规定", "规则", "余额", "剩余", "记录", "历史",
                        "状态", "待办", "权限", "入职", "知识库", "文档")
                .noneMatch(normalized::contains);
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
        if (isCurrentTimeQuestion(question)) {
            return new ChatRoutePlan("sys-current-time", "当前时间", IntentKind.SYSTEM,
                    RouteResult.of(RouteDecision.SYSTEM, null, "服务端标准时间", 1D));
        }
        if (isCapabilityDiscovery(question)) {
            return new ChatRoutePlan("sys-about-bot", "关于助手", IntentKind.SYSTEM,
                    RouteResult.of(RouteDecision.SYSTEM, null, "运行时能力说明", 1D));
        }
        if (isExplicitLeaveApplication(question)) {
            RouteResult route = routingService.route(
                    "leave-approval", IntentKind.WORKFLOW, 1D, false, identity);
            return new ChatRoutePlan("leave-approval", "请假申请", IntentKind.WORKFLOW, route);
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

    /**
     * 能力发现是本系统自身元数据查询，不应依赖外部分类模型，否则模型故障时用户连“能做什么”都无法获知。
     */
    private boolean isCapabilityDiscovery(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}]", "");
        boolean mentionsAssistant = normalized.contains("你")
                || normalized.contains("助手")
                || normalized.contains("智能体")
                || normalized.contains("agent")
                || normalized.contains("ai");
        return normalized.contains("你是谁")
                || normalized.contains("能用你干什么")
                || (mentionsAssistant && (normalized.contains("能做什么")
                || normalized.contains("能干什么")
                || normalized.contains("有什么工具")
                || normalized.contains("哪些工具")
                || normalized.contains("工具可用")
                || normalized.contains("连接哪些系统")
                || normalized.contains("接入哪些系统")
                || normalized.contains("连接系统")));
    }

    /** 当前日期时间属于确定性系统事实，不交给模型推测。 */
    private boolean isCurrentTimeQuestion(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}]", "");
        boolean asksTimeValue = normalized.contains("时间")
                || normalized.contains("几点")
                || normalized.contains("日期")
                || normalized.contains("几号")
                || normalized.contains("星期几")
                || normalized.contains("周几");
        boolean meansNow = normalized.contains("当前")
                || normalized.contains("现在")
                || normalized.contains("此刻")
                || normalized.contains("今天")
                || normalized.contains("北京时间");
        return asksTimeValue && meansNow;
    }

    private boolean isModificationFollowUp(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.replaceAll("[\\p{P}\\p{Z}]", "");
        return java.util.stream.Stream.of(
                        "能修改吗", "可以修改吗", "还能修改吗", "怎么修改", "如何修改", "能改吗", "可以改吗")
                .anyMatch(normalized::contains);
    }

    /**
     * 明确的请假办理入口属于已配置固定流程，在分类模型不可用时仍需可达。
     * 制度、余额和历史状态问题保留给知识库或只读工具，避免把查询误识别成写流程。
     */
    private boolean isExplicitLeaveApplication(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalized = question.replaceAll("[\\p{P}\\p{Z}]", "");
        if (!normalized.contains("请假") && !normalized.contains("休假")) {
            return false;
        }
        boolean isReadOnlyQuestion = java.util.stream.Stream.of(
                        "制度", "政策", "规定", "规则", "余额", "剩余", "记录", "历史", "状态", "查询", "查看")
                .anyMatch(normalized::contains);
        boolean isNegativeOrApprovalRequest = java.util.stream.Stream.of(
                        "不想", "不要", "不用", "无需", "取消", "撤回", "批准", "审批", "同意", "驳回")
                .anyMatch(normalized::contains);
        if (isReadOnlyQuestion || isNegativeOrApprovalRequest) {
            return false;
        }
        boolean explicitAction = java.util.stream.Stream.of(
                        "我要请假", "我想请假", "我要休假", "我想休假", "申请请假", "申请休假",
                        "发起请假", "发起休假", "帮我请假", "帮我休假", "提交请假", "提交休假")
                .anyMatch(normalized::contains);
        boolean containsBusinessDate = normalized.matches(".*(?:\\d{1,4}年|\\d{1,2}月|\\d{1,2}[日号]|\\d{1,2}[-/]\\d{1,2}).*");
        return explicitAction || containsBusinessDate;
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
