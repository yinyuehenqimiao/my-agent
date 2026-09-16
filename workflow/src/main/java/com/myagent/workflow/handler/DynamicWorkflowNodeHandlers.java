package com.myagent.workflow.handler;

import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolResult;
import com.myagent.framework.workflow.WorkflowReactPort;
import com.myagent.workflow.runtime.WorkflowKnowledgeService;
import com.myagent.workflow.runtime.WorkflowNodeContext;
import com.myagent.workflow.runtime.WorkflowNodeContextFactory;
import com.myagent.workflow.runtime.WorkflowToolExecutionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 注册动态业务积木的真实执行器。用户等待类节点由 Flowable userTask 原生持久化。 */
@Component
public class DynamicWorkflowNodeHandlers {
    private final WorkflowTaskHandlerRegistry registry;
    private final WorkflowNodeContextFactory contexts;
    private final WorkflowKnowledgeService knowledge;
    private final WorkflowToolExecutionService tools;
    private final ObjectProvider<WorkflowReactPort> reactPort;

    public DynamicWorkflowNodeHandlers(WorkflowTaskHandlerRegistry registry, WorkflowNodeContextFactory contexts,
                                       WorkflowKnowledgeService knowledge, WorkflowToolExecutionService tools,
                                       ObjectProvider<WorkflowReactPort> reactPort) {
        this.registry = registry; this.contexts = contexts; this.knowledge = knowledge;
        this.tools = tools; this.reactPort = reactPort;
    }

    @PostConstruct
    void register() {
        registry.register("tool.query", 1, execution -> executeTool(contexts.create(execution), false));
        registry.register("tool.execute", 1, execution -> executeTool(contexts.create(execution), true));
        registry.register("knowledge", 1, execution -> executeKnowledge(contexts.create(execution)));
        registry.register("react", 1, execution -> executeReact(contexts.create(execution)));
        registry.register("reply", 1, execution -> executeReply(contexts.create(execution)));
    }

    private void executeTool(WorkflowNodeContext context, boolean write) {
        String toolName = context.text("toolName", null);
        if (toolName == null) throw new ClientException("工具节点尚未选择工具");
        Map<String, Object> input = context.mappedInput();
        String confirmationId = write ? variable(context, context.text("confirmationIdVariable", "confirmationId")) : null;
        long confirmationVersion = write ? number(context.variables().get(
                context.text("confirmationVersionVariable", "confirmationLockVersion")), 0) : 0;
        ToolResult result = tools.execute(context.identity(), new WorkflowToolExecutionService.Command(
                context.instanceId(), context.nodeInstanceId(), context.integer("actionVersion", 1),
                toolName, input, confirmationId, confirmationVersion,
                string(context.variables().get("conversationId")), string(context.variables().get("traceId"))));
        if (result.getState() != ToolResult.State.SUCCESS) {
            throw new ClientException(result.getMessage() == null ? "工具执行失败" : result.getMessage());
        }
        writeOutput(context, result.getOutput());
    }

    private void executeKnowledge(WorkflowNodeContext context) {
        String question = resolveTemplate(context.text("questionTemplate", "${question}"), context.variables());
        var answer = knowledge.answer(context.identity(), question);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("status", answer.status());
        output.put("summary", answer.summary());
        output.put("sources", answer.sources());
        output.put("evidenceCount", answer.sources() == null ? 0 : answer.sources().size());
        writeOutput(context, output);
        context.execution().setVariable(context.nodeInstanceId() + "_hasEvidence", !"NO_EVIDENCE".equals(answer.status()));
    }

    @SuppressWarnings("unchecked")
    private void executeReact(WorkflowNodeContext context) {
        WorkflowReactPort port = reactPort.getIfAvailable();
        if (port == null) throw new ClientException("受限 ReAct 执行器未启用");
        List<String> whitelist = context.node().config().get("toolWhitelist") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        Map<String, Object> outputSchema = context.node().config().get("outputSchema") instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
        var result = port.execute(context.identity(), new WorkflowReactPort.Request(
                resolveTemplate(context.text("instruction", ""), context.variables()), context.mappedInput(), whitelist,
                context.integer("maxRounds", 5), context.integer("maxToolCalls", 8),
                context.integer("timeoutSeconds", 60), outputSchema));
        if (!"SUCCEEDED".equals(result.status())) throw new ClientException("智能处理节点执行失败：" + result.status());
        writeOutput(context, result.output());
    }

    private void executeReply(WorkflowNodeContext context) {
        String reply = resolveTemplate(context.text("template", "${answer}"), context.variables());
        context.execution().setVariable(context.text("outputVariable", "reply"), reply);
    }

    @SuppressWarnings("unchecked")
    private void writeOutput(WorkflowNodeContext context, Object output) {
        String outputVariable = context.text("outputVariable", context.nodeInstanceId() + "_result");
        context.execution().setVariable(outputVariable, output);
        Object mappings = context.node().config().get("outputMappings");
        if (output instanceof Map<?, ?> values && mappings instanceof Map<?, ?> map) {
            map.forEach((source, target) -> context.execution().setVariable(
                    String.valueOf(target), values.get(String.valueOf(source))));
        }
    }

    private static String resolveTemplate(String template, Map<String, Object> variables) {
        String result = template == null ? "" : template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return result;
    }
    private static String variable(WorkflowNodeContext context, String name) {
        Object value = context.variables().get(name);
        return value == null ? null : String.valueOf(value);
    }
    private static long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? fallback : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
    private static String string(Object value) { return value == null ? null : String.valueOf(value); }
}
