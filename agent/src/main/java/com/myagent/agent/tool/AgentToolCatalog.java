/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.myagent.agent.tool;

import cn.hutool.core.util.StrUtil;
import com.myagent.agent.config.ConditionalOnAgentEngine;
import com.myagent.agent.service.AgentConversationService;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.IntentNodeRegistry;
import com.myagent.rag.core.mcp.McpToolExecutor;
import com.myagent.rag.core.mcp.McpToolRegistry;
import com.myagent.rag.core.prompt.AgentPromptResolver;
import com.myagent.rag.core.prompt.AgentPromptSlot;
import com.myagent.rag.service.KnowledgeSearchFacade;
import com.myagent.framework.tool.ToolDefinition.Source;
import io.agentscope.core.tool.Toolkit;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 主 Agent 工具目录：固定注册 search_knowledge，并按意图树配置挂载当前可用的 MCP 工具
 */
@Slf4j
@Component
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentToolCatalog {

    private static final java.util.Set<String> SUPPORTED_WORKFLOWS = java.util.Set.of("leave-approval");

    private final KnowledgeSearchFacade knowledgeSearchFacade;
    private final AgentConversationService conversationService;
    private final IntentNodeRegistry intentNodeRegistry;
    private final McpToolRegistry mcpToolRegistry;
    private final AgentPromptResolver agentPromptResolver;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EnterpriseTools enterpriseTools;

    @org.springframework.beans.factory.annotation.Autowired
    private com.myagent.enterprise.service.KnowledgeAccessService enterpriseAccess;
    @org.springframework.beans.factory.annotation.Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 把注册表与提示词解析一次并定格：同一次请求的指纹与 Toolkit 都从这份快照派生
     */
    public ResolvedCatalog resolve() {
        List<String> unavailableToolIds = new ArrayList<>();
        List<McpToolBinding> bindings = resolveMcpToolBindings(unavailableToolIds);
        return new ResolvedCatalog(resolveKnowledgeToolDescription(), bindings, unavailableToolIds,
                enterpriseTools == null ? Map.of() : EnterpriseTools.displayNames());
    }

    /**
     * 按快照构建全新 Toolkit：过程中不再回读注册表，结果与快照指纹必然一致
     */
    public Toolkit buildToolkit(ResolvedCatalog catalog) {
        Toolkit toolkit = new Toolkit();
        if (enterpriseTools != null) enterpriseTools.tools().forEach(toolkit::registerAgentTool);
        KnowledgeSearchTool knowledgeTool = new KnowledgeSearchTool(catalog.knowledgeToolDescription, knowledgeSearchFacade, conversationService);
        knowledgeTool.setEnterpriseAccess(enterpriseAccess);
        knowledgeTool.setObjectMapper(objectMapper);
        toolkit.registerAgentTool(knowledgeTool);
        catalog.bindings.forEach(binding -> toolkit.registerAgentTool(
                new McpToolBridge(binding.executor(), binding.description())));
        // 不可用只在重建这一刻报：解析每请求都走，放解析里会刷屏
        catalog.unavailableToolIds.forEach(toolId ->
                log.warn("意图树配置的 MCP 工具当前不可用, toolId: {}", toolId));
        return toolkit;
    }

    /** 固定流程展示快照不读取知识库提示词与 MCP 注册表，避免无关组件故障拖垮业务办理。 */
    public ResolvedCatalog resolveWorkflowCatalog() {
        return new ResolvedCatalog("固定流程不使用知识库", List.of(), List.of(), EnterpriseTools.displayNames());
    }

    /** 固定流程使用最小工具集，禁止把知识库和任意 MCP 工具带入业务办理链路。 */
    public Toolkit buildWorkflowToolkit(String workflowDefKey) {
        if (!supportsWorkflow(workflowDefKey)) {
            throw new IllegalArgumentException("未配置固定流程工具集：" + workflowDefKey);
        }
        if (enterpriseTools == null) {
            throw new IllegalStateException("企业工具未启用，无法执行固定流程");
        }
        Toolkit toolkit = new Toolkit();
        enterpriseTools.tools(Source.WORKFLOW).forEach(toolkit::registerAgentTool);
        return toolkit;
    }

    /** 当前已完成专用工具集接入、可以安全执行的固定流程。 */
    public boolean supportsWorkflow(String workflowDefKey) {
        return SUPPORTED_WORKFLOWS.contains(workflowDefKey);
    }

    /**
     * 意图树已配置且 MCP 注册表当前可用的工具数，meta 探活据此报告 MCP 配置状态
     * 不走整份解析：探活不该被知识库工具声明缺失连坐
     */
    public int mcpToolCount() {
        return resolveMcpToolBindings(new ArrayList<>()).size();
    }

    /** 当前意图树与 MCP 注册表共同确认可用的外部工具，用于面向用户的能力发现。 */
    public List<ConnectedTool> listAvailableMcpTools() {
        return resolveMcpToolBindings(new ArrayList<>()).stream()
                .map(binding -> new ConnectedTool(
                        binding.toolId(), binding.displayName(), binding.description()))
                .toList();
    }

    private String resolveKnowledgeToolDescription() {
        String description = agentPromptResolver.resolve(AgentPromptSlot.KNOWLEDGE_TOOL_DESCRIPTION);
        if (StrUtil.isBlank(description)) {
            throw new IllegalStateException("KNOWLEDGE_TOOL_DESCRIPTION 提示词不允许为空");
        }
        return description;
    }

    /**
     * 意图树配置与 MCP 注册表求交集，配了但当前没执行器的工具 ID 收进 unavailableToolIds
     */
    private List<McpToolBinding> resolveMcpToolBindings(List<String> unavailableToolIds) {
        Map<String, List<IntentNode>> nodesByToolId = intentNodeRegistry.listMcpToolNodes().stream()
                .collect(Collectors.groupingBy(
                        node -> node.getMcpToolId().trim(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, McpToolExecutor> executors = mcpToolRegistry.listAllExecutors().stream()
                .collect(Collectors.toMap(
                        McpToolExecutor::getToolId,
                        Function.identity(),
                        (left, right) -> right));

        List<McpToolBinding> bindings = new ArrayList<>();
        nodesByToolId.forEach((toolId, nodes) -> {
            McpToolExecutor executor = executors.get(toolId);
            if (executor == null) {
                unavailableToolIds.add(toolId);
                return;
            }
            bindings.add(toBinding(toolId, nodes, executor));
        });
        return bindings;
    }

    private McpToolBinding toBinding(String toolId, List<IntentNode> nodes, McpToolExecutor executor) {
        String displayName = nodes.stream()
                .map(IntentNode::getName)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(toolId);
        String description = nodes.stream()
                .map(IntentNode::getDescription)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.joining("\n"));
        return new McpToolBinding(toolId, displayName, description, executor);
    }

    public record McpToolBinding(
            String toolId,
            String displayName,
            String description,
            McpToolExecutor executor) {
    }

    public record ConnectedTool(String toolId, String displayName, String description) {
    }

    /**
     * 一次解析定格的工具目录，指纹与展示名在构造时算好
     * 快照随 Agent 实例一起缓存，重建之前所有请求看到的都是同一份
     */
    public static final class ResolvedCatalog {

        private final String knowledgeToolDescription;
        private final List<McpToolBinding> bindings;
        private final List<String> unavailableToolIds;
        private final Map<String, String> displayNames;
        private final ToolCatalogFingerprint fingerprint;

        public ResolvedCatalog(
                String knowledgeToolDescription,
                List<McpToolBinding> bindings,
                List<String> unavailableToolIds) {
            this(knowledgeToolDescription, bindings, unavailableToolIds, Map.of());
        }

        public ResolvedCatalog(
                String knowledgeToolDescription,
                List<McpToolBinding> bindings,
                List<String> unavailableToolIds,
                Map<String, String> additionalDisplayNames) {
            this.knowledgeToolDescription = knowledgeToolDescription;
            this.bindings = List.copyOf(bindings);
            this.unavailableToolIds = List.copyOf(unavailableToolIds);

            Map<String, String> names = new LinkedHashMap<>();
            names.put(KnowledgeSearchTool.TOOL_NAME, KnowledgeSearchTool.DISPLAY_NAME);
            this.bindings.forEach(binding -> names.put(binding.toolId(), binding.displayName()));
            names.putAll(additionalDisplayNames);
            this.displayNames = Map.copyOf(names);
            this.fingerprint = new ToolCatalogFingerprint(knowledgeToolDescription, this.bindings.stream()
                    .map(binding -> new McpToolFingerprint(
                            binding.toolId(),
                            binding.displayName(),
                            binding.description(),
                            binding.executor().getToolDefinition()))
                    .toList());
        }

        public ToolCatalogFingerprint fingerprint() {
            return fingerprint;
        }

        /**
         * SSE 工具进度事件的展示名，未收录的工具回落原始名
         */
        public String displayNameOf(String toolName) {
            return displayNames.getOrDefault(toolName, toolName);
        }
    }

    public record ToolCatalogFingerprint(
            String knowledgeToolDescription,
            List<McpToolFingerprint> mcpTools) {

        public ToolCatalogFingerprint {
            mcpTools = List.copyOf(mcpTools);
        }
    }

    public record McpToolFingerprint(
            String toolId,
            String displayName,
            String description,
            Tool definition) {
    }
}
