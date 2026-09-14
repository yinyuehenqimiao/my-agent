package com.myagent.agent.routing;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 聊天路由的轻量分句器，只识别明确的并列业务请求，不能承担工具规划或参数拆解。
 */
final class RouteUnitSplitter {

    private static final Pattern EXPLICIT_BOUNDARY = Pattern.compile("[；;。！？!?\\n]+|，(?:并且|同时|然后)");

    private RouteUnitSplitter() {
    }

    static List<String> split(String question) {
        if (question == null || question.isBlank()) {
            return List.of("");
        }
        // “不是 A，而是 B”属于纠错后的单一意图，交给一次分类避免把旧意图误执行。
        if (question.contains("不是") && question.contains("而是")) {
            return List.of(question.trim());
        }
        List<String> units = EXPLICIT_BOUNDARY.splitAsStream(question)
                .map(String::trim)
                .filter(unit -> !unit.isBlank())
                .toList();
        return units.isEmpty() ? List.of(question.trim()) : units;
    }
}
