package com.myagent.framework.routing;

/**
 * 路由决策结果。固定业务走 Workflow，开放问题进入受控 ReAct。
 * <p>
 * 路由优先级：确认与审批 → 高置信固定流程 → 参数澄清 → 授权 ReAct → 系统回答 → 拒绝。
 * Prompt 不承担权限判断，Tool Gateway 做执行时二次鉴权。
 */
public enum RouteDecision {
    /** 固定业务流程，走 Flowable */
    WORKFLOW,
    /** 受控开放式 Agent */
    REACT,
    /** 系统回答（问候、介绍等） */
    SYSTEM,
    /** 参数不足，追问澄清 */
    CLARIFY,
    /** 拒绝（越权、不安全） */
    DENY
}
