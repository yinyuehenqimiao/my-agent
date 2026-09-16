package com.myagent.workflow.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 可视化编排允许使用的业务积木目录。目录是平台安全边界，前端只展示这里声明的节点。
 */
@Component
public class WorkflowNodeCatalog {

    public List<NodeDefinition> list() {
        return List.of(
                node("start", "开始", "控制", "NONE", "LOW", false),
                node("collect", "收集信息", "交互", "USER", "LOW", false),
                node("tool.query", "查询工具", "工具", "NONE", "MEDIUM", false),
                node("knowledge", "知识问答", "问答", "NONE", "LOW", false),
                node("react", "智能处理", "智能", "NONE", "MEDIUM", false),
                node("condition", "条件分支", "控制", "NONE", "MEDIUM", false),
                node("confirm", "用户确认", "交互", "USER", "HIGH", true),
                node("tool.execute", "执行业务操作", "工具", "NONE", "HIGH", true),
                node("human.wait", "人工等待", "交互", "HUMAN", "MEDIUM", false),
                node("reply", "回复", "输出", "NONE", "LOW", false),
                node("end", "结束", "控制", "NONE", "LOW", false));
    }

    private static NodeDefinition node(String type, String name, String category, String waitCapability,
                                       String riskLevel, boolean requiresConfirmation) {
        return new NodeDefinition(type, 1, name, category,
                Map.of("type", "object", "additionalProperties", false),
                Map.of("type", "object"), Map.of("type", "object"),
                waitCapability, riskLevel, requiresConfirmation, new RetryPolicy(1, 0));
    }

    public record NodeDefinition(String type, int version, String name, String category,
                                 Map<String, Object> configSchema, Map<String, Object> inputSchema,
                                 Map<String, Object> outputSchema, String waitCapability,
                                 String riskLevel, boolean requiresConfirmation, RetryPolicy retryPolicy) {
    }

    public record RetryPolicy(int maxAttempts, long backoffMs) {
    }
}
