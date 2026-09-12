package com.myagent.framework.routing;

import lombok.Builder;
import lombok.Data;

/** 路由决策结果：决策 + 目标 + 原因。 */
@Data
@Builder
public class RouteResult {
    /** 路由决策 */
    private RouteDecision decision;
    /** 目标：WORKFLOW 时为工作流定义键；REACT 时为 ReAct 配置；其余为空 */
    private String target;
    /** 决策原因（供 SSE route_decision 事件展示） */
    private String reason;
    /** 置信度（用于阈值判断与可观测） */
    private double confidence;

    public static RouteResult of(RouteDecision d, String target, String reason, double confidence) {
        return RouteResult.builder().decision(d).target(target).reason(reason).confidence(confidence).build();
    }
}
