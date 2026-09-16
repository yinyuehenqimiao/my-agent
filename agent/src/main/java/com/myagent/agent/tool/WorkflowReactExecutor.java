package com.myagent.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.agent.config.ConditionalOnAgentEngine;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.workflow.WorkflowReactPort;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 工作流受限 ReAct 执行器：仅装载节点白名单中的只读工具，并强制轮次、调用和超时预算。 */
@Component
@ConditionalOnAgentEngine
public class WorkflowReactExecutor implements WorkflowReactPort {
    private final ToolGateway gateway;
    private final OpenAIChatModel model;
    private final ObjectMapper json;

    public WorkflowReactExecutor(ToolGateway gateway, OpenAIChatModel model, ObjectMapper json) {
        this.gateway = gateway; this.model = model; this.json = json;
    }

    @Override
    public Result execute(ExecutionIdentity identity, Request request) {
        validateBudget(request);
        Map<String, ToolDefinition> available = new LinkedHashMap<>();
        gateway.listDefinitions(identity, ToolDefinition.Source.WORKFLOW).stream()
                .filter(ToolDefinition::isReadOnly).forEach(tool -> available.put(tool.getName(), tool));
        for (String name : request.toolWhitelist()) {
            if (!available.containsKey(name)) throw new ClientException("ReAct 白名单工具不存在或不是只读工具：" + name);
        }
        AtomicInteger calls = new AtomicInteger();
        Toolkit toolkit = new Toolkit();
        request.toolWhitelist().forEach(name -> {
            ToolDefinition definition = available.get(name);
            toolkit.registerAgentTool(new McpToolBridge(new GatewayMcpToolExecutor(
                    gateway, definition, identity, json, calls, request.maxToolCalls()), definition.getDescription()));
        });
        String system = "你是工作流中的受限智能处理节点。只能使用已提供的只读工具。"
                + "不得修改身份、确认状态或流程控制变量。最终只输出符合要求的 JSON 对象，不要输出 Markdown。";
        ReActAgent agent = ReActAgent.builder().name("workflow_react_" + UUID.randomUUID())
                .sysPrompt(system).model(model).toolkit(toolkit).maxIters(request.maxRounds()).build();
        String prompt;
        try {
            prompt = request.instruction() + "\n输入：" + json.writeValueAsString(request.input())
                    + "\n输出 JSON Schema：" + json.writeValueAsString(request.outputSchema());
            var message = agent.call(prompt, RuntimeContext.builder().userId(identity.getUserId())
                    .sessionId(UUID.randomUUID().toString()).build())
                    .block(Duration.ofSeconds(request.timeoutSeconds()));
            if (message == null) return new Result("FINAL_FAILURE", Map.of(), request.maxRounds(), calls.get());
            Map<String, Object> output = parseObject(message.getTextContent());
            validateOutput(request.outputSchema(), output);
            return new Result("SUCCEEDED", output, request.maxRounds(), calls.get());
        } catch (ClientException ex) {
            throw ex;
        } catch (Exception ex) {
            return new Result("FINAL_FAILURE", Map.of("message", "智能处理执行失败"), request.maxRounds(), calls.get());
        } finally {
            agent.close();
        }
    }

    private Map<String, Object> parseObject(String text) throws Exception {
        String value = text == null ? "" : text.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        return json.readValue(value, new TypeReference<>() { });
    }

    @SuppressWarnings("unchecked")
    private void validateOutput(Map<String, Object> schema, Map<String, Object> output) {
        Object required = schema == null ? null : schema.get("required");
        if (required instanceof List<?> fields) {
            for (Object field : fields) if (!output.containsKey(String.valueOf(field)))
                throw new ClientException("ReAct 输出缺少字段：" + field);
        }
        Object properties = schema == null ? null : schema.get("properties");
        if (properties instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                Object actual = output.get(String.valueOf(key));
                if (actual == null || !(value instanceof Map<?, ?> rule)) return;
                Object expected = rule.get("type");
                boolean valid = switch (String.valueOf(expected)) {
                    case "string" -> actual instanceof String;
                    case "number", "integer" -> actual instanceof Number;
                    case "boolean" -> actual instanceof Boolean;
                    case "object" -> actual instanceof Map;
                    case "array" -> actual instanceof List;
                    default -> true;
                };
                if (!valid) throw new ClientException("ReAct 输出字段类型错误：" + key);
            });
        }
    }

    private void validateBudget(Request request) {
        if (request.maxRounds() < 1 || request.maxRounds() > 5
                || request.maxToolCalls() < 1 || request.maxToolCalls() > 8
                || request.timeoutSeconds() < 1 || request.timeoutSeconds() > 60)
            throw new ClientException("智能处理预算超过部署上限");
    }
}
