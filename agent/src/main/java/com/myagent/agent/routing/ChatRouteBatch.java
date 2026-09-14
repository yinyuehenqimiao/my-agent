package com.myagent.agent.routing;

import java.util.List;

/** 一次聊天请求拆出的路由单元及其合并后的安全执行计划。 */
public record ChatRouteBatch(ChatRoutePlan plan, List<String> routeUnits) {

    public ChatRouteBatch {
        routeUnits = List.copyOf(routeUnits);
    }
}
