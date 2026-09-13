package com.myagent.agent.routing;

import com.myagent.framework.routing.RouteResult;
import com.myagent.rag.enums.IntentKind;

/** 一次真实聊天请求的不可变路由快照。 */
public record ChatRoutePlan(
        String intentCode,
        String intentName,
        IntentKind intentKind,
        RouteResult route) {
}
