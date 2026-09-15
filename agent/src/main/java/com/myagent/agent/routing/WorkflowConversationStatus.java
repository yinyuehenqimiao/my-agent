package com.myagent.agent.routing;

/** 会话级工作流状态，用于区分参数收集、确认等待与终态。 */
public enum WorkflowConversationStatus {
    COLLECTING_PARAMETERS,
    AWAITING_CONFIRMATION,
    SUBMITTED,
    PENDING_MANAGER,
    PENDING_HR,
    PAUSED,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    ERROR;

    public boolean isContinuable() {
        return this == COLLECTING_PARAMETERS || this == AWAITING_CONFIRMATION;
    }
}
