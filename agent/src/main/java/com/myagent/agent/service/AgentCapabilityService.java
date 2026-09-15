package com.myagent.agent.service;

import com.myagent.agent.tool.AgentToolCatalog;
import com.myagent.agent.tool.EnterpriseTools;
import com.myagent.agent.config.ConditionalOnAgentEngine;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 基于运行时工具注册表生成能力说明，不维护一份容易失真的静态文案。 */
@Service
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentCapabilityService {

    private final ToolGateway toolGateway;
    private final AgentToolCatalog toolCatalog;
    private final PolicyDecisionService policyDecisionService;

    public String describe(ExecutionIdentity identity) {
        List<ToolDefinition> connectedTools = toolGateway.listDefinitions().stream()
                .filter(ToolDefinition::isEnabled)
                .filter(this::isChatTool)
                .filter(definition -> isAuthorized(identity, definition))
                .toList();
        List<ToolDefinition> readTools = connectedTools.stream().filter(ToolDefinition::isReadOnly).toList();
        List<ToolDefinition> writeTools = connectedTools.stream().filter(tool -> !tool.isReadOnly()).toList();
        List<AgentToolCatalog.ConnectedTool> mcpTools = toolCatalog.listAvailableMcpTools();

        StringBuilder answer = new StringBuilder("我当前能连接并使用这些能力（最终结果仍按你的登录身份和数据范围校验）：\n")
                .append("- 公司知识库查询：公司制度、流程文档等，返回内容受文档权限控制。\n");
        appendDefinitions(answer, "ERP 只读工具", readTools);
        appendDefinitions(answer, "ERP 操作工具", writeTools);
        if (!mcpTools.isEmpty()) {
            answer.append("- 开放系统工具：")
                    .append(mcpTools.stream().map(tool -> display(tool.displayName(), tool.toolId()))
                            .reduce((left, right) -> left + "、" + right).orElse(""))
                    .append("。\n");
        }
        if (toolCatalog.supportsWorkflow("leave-approval")
                && connectedTools.stream().anyMatch(tool -> "prepare_leave_request".equals(tool.getName()))) {
            answer.append("- 固定流程：请假申请（收集参数 → 生成确认卡 → 用户确认后提交）。\n");
        }
        if (!writeTools.isEmpty()) {
            answer.append("写操作只生成确认卡，不会由模型直接提交；你确认后才会进入业务系统。");
        } else {
            answer.append("你可以直接描述要查询的对象或问题，我会选择相应的只读能力。");
        }
        return answer.toString();
    }

    private void appendDefinitions(StringBuilder answer, String title, List<ToolDefinition> definitions) {
        if (definitions.isEmpty()) {
            return;
        }
        answer.append("- ").append(title).append("：")
                .append(definitions.stream()
                        .map(definition -> display(
                                EnterpriseTools.displayNames().getOrDefault(
                                        definition.getName(), definition.getDescription()),
                                definition.getName()))
                        .reduce((left, right) -> left + "、" + right).orElse(""))
                .append("。\n");
    }

    private boolean isChatTool(ToolDefinition definition) {
        List<ToolDefinition.Source> sources = definition.getAllowedSources();
        return sources == null || sources.isEmpty()
                || sources.contains(ToolDefinition.Source.REACT)
                || sources.contains(ToolDefinition.Source.WORKFLOW);
    }

    private boolean isAuthorized(ExecutionIdentity identity, ToolDefinition definition) {
        String permissionCode = definition.getPermissionCode();
        return permissionCode == null || permissionCode.isBlank()
                || policyDecisionService.hasPermission(identity, permissionCode);
    }

    private String display(String displayName, String toolId) {
        String name = displayName == null || displayName.isBlank() ? toolId : displayName;
        return name + "（" + toolId + "）";
    }
}
