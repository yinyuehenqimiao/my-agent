package com.myagent.agent.routing;

/** 最近非活动的固定流程，只用于理解省略了业务对象的紧邻追问。 */
public record RecentWorkflowContext(String workflowDefKey, WorkflowConversationStatus status) {

    public RecentWorkflowContext {
        if (workflowDefKey == null || workflowDefKey.isBlank()) {
            throw new IllegalArgumentException("最近工作流目标不能为空");
        }
        if (status == null || status.isContinuable()) {
            throw new IllegalArgumentException("最近工作流上下文不能处于参数收集或确认状态");
        }
    }
}
