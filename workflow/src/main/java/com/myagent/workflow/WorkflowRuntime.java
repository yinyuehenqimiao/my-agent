package com.myagent.workflow;

/**
 * 工作流运行时模块入口标记。
 * <p>
 * 嵌入式 Flowable：使用当前 PostgreSQL，ACT_* 表与业务表隔离；
 * 提供可持久化、可版本化、可恢复的固定业务流程能力。
 */
public final class WorkflowRuntime {
    private WorkflowRuntime() {
    }
}
