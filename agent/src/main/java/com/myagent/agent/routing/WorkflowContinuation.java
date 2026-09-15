package com.myagent.agent.routing;

/** 当前会话需要续接的固定工作流；目标只能来自服务端持久化状态。 */
public record WorkflowContinuation(String workflowDefKey, WorkflowConversationStatus status) {

    public WorkflowContinuation {
        if (workflowDefKey == null || workflowDefKey.isBlank()) {
            throw new IllegalArgumentException("工作流续接目标不能为空");
        }
        if (status == null || !status.isContinuable()) {
            throw new IllegalArgumentException("当前工作流状态不可续接");
        }
    }
}
