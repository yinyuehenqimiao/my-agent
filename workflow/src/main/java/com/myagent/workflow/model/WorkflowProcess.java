package com.myagent.workflow.model;

import java.util.List;
import java.util.Map;

/** 画布保存格式；它是可编辑源，BPMN 只能由服务端编译生成。 */
public record WorkflowProcess(List<Node> nodes, List<Edge> edges) {

    public record Node(String id, String type, Integer version, Map<String, Object> config) {
    }

    public record Edge(String id, String source, String target, String outlet, Condition condition) {
    }

    /** 结构化条件，避免接受脚本、SpEL 或任意 Flowable 表达式。 */
    public record Condition(String field, String operator, String value) {
    }
}
