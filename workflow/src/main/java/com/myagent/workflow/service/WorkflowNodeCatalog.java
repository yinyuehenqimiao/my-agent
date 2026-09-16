package com.myagent.workflow.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 可视化编排允许使用的业务积木目录。目录是平台安全边界，前端只展示这里声明的节点。
 */
@Component
public class WorkflowNodeCatalog {

    public List<NodeDefinition> list() {
        return List.of(
                new NodeDefinition("start", "开始", "控制", false, "低"),
                new NodeDefinition("collect", "收集信息", "交互", false, "低"),
                new NodeDefinition("confirm", "确认卡片", "交互", false, "中"),
                new NodeDefinition("knowledge", "查询知识库", "问答", false, "低"),
                new NodeDefinition("tool", "调用业务工具", "工具", true, "高"),
                new NodeDefinition("condition", "条件分支", "控制", false, "中"),
                new NodeDefinition("end", "结束", "控制", false, "低"));
    }

    public record NodeDefinition(String type, String name, String category,
                                  boolean requiresPermission, String riskLevel) {
    }
}
