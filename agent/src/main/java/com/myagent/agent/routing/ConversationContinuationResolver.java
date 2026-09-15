package com.myagent.agent.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** 按服务端状态判断当前消息是否应续接固定工作流。 */
@Component
public class ConversationContinuationResolver {

    private final ConversationWorkflowStateService stateService;
    private final EnterprisePendingActionResolver pendingActionResolver;
    private final String pendingWorkflowDefKey;

    public ConversationContinuationResolver(
            ConversationWorkflowStateService stateService,
            EnterprisePendingActionResolver pendingActionResolver,
            @Value("${myagent.routing.pendingWorkflowDefKey:leave-approval}") String pendingWorkflowDefKey) {
        this.stateService = stateService;
        this.pendingActionResolver = pendingActionResolver;
        this.pendingWorkflowDefKey = pendingWorkflowDefKey;
    }

    public Optional<WorkflowContinuation> resolve(String tenantId, String userId, String conversationId) {
        Optional<WorkflowContinuation> active = stateService.findContinuation(tenantId, userId, conversationId);
        if (active.isPresent()) {
            WorkflowContinuation continuation = active.get();
            boolean hasPendingAction = pendingActionResolver.hasPendingAction(userId, conversationId);
            if (continuation.status() == WorkflowConversationStatus.AWAITING_CONFIRMATION) {
                if (hasPendingAction) {
                    return active;
                }
                // 确认卡已确认、取消或过期，旧流程不再拦截新的用户意图。
                stateService.complete(tenantId, userId, conversationId);
                return Optional.empty();
            }
            if (hasPendingAction) {
                stateService.markAwaitingConfirmation(tenantId, userId, conversationId);
                return Optional.of(new WorkflowContinuation(
                        continuation.workflowDefKey(), WorkflowConversationStatus.AWAITING_CONFIRMATION));
            }
            // 参数收集阶段尚未产生确认卡，仍必须优先续接原工作流。
            return active;
        }
        if (pendingActionResolver.hasPendingAction(userId, conversationId)) {
            return Optional.of(new WorkflowContinuation(
                    pendingWorkflowDefKey, WorkflowConversationStatus.AWAITING_CONFIRMATION));
        }
        return Optional.empty();
    }

    /**
     * 工作流一轮执行完成后同步“待确认”边界。参数未收齐时不改变 COLLECTING 状态；
     * 一旦工具生成确认卡，下一轮就只允许继续确认流程。
     */
    public void synchronizeAfterWorkflowTurn(String tenantId, String userId, String conversationId) {
        Optional<WorkflowContinuation> active = stateService.findContinuation(tenantId, userId, conversationId);
        if (active.filter(value -> value.status() == WorkflowConversationStatus.COLLECTING_PARAMETERS).isPresent()
                && pendingActionResolver.hasPendingAction(userId, conversationId)) {
            stateService.markAwaitingConfirmation(tenantId, userId, conversationId);
        }
    }
}
