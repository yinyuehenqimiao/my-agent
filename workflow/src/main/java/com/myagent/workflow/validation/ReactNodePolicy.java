package com.myagent.workflow.validation;

import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ReactNodePolicy {
    public static final int MAX_ROUNDS = 5, MAX_TOOL_CALLS = 8, MAX_SECONDS = 60;
    private final ToolGateway gateway;
    public Config validate(Config config) {
        if (config == null || config.maxRounds() < 1 || config.maxRounds() > MAX_ROUNDS || config.maxToolCalls() < 1 || config.maxToolCalls() > MAX_TOOL_CALLS || config.timeoutSeconds() < 1 || config.timeoutSeconds() > MAX_SECONDS)
            throw new ClientException("智能处理预算超过部署上限");
        var definitions = gateway.listDefinitions();
        for (String name : config.toolWhitelist()) {
            ToolDefinition definition = definitions.stream().filter(item -> item.getName().equals(name)).findFirst().orElseThrow(() -> new ClientException("白名单工具不存在：" + name));
            if (!definition.isEnabled() || !definition.isReadOnly()) throw new ClientException("智能处理只允许已启用的只读工具：" + name);
        }
        return config;
    }
    public record Config(List<String> toolWhitelist,int maxRounds,int maxToolCalls,int timeoutSeconds) { }
}
