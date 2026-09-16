package com.myagent.workflow.runtime;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.workflow.model.WorkflowProcess;
import org.flowable.engine.delegate.DelegateExecution;

import java.util.LinkedHashMap;
import java.util.Map;

/** 动态节点执行所需的不可变发布配置、可信身份和当前变量快照。 */
public record WorkflowNodeContext(String instanceId, String nodeInstanceId, long versionId,
                                  WorkflowProcess.Node node, ExecutionIdentity identity,
                                  Map<String, Object> variables, DelegateExecution execution) {

    public Map<String, Object> mappedInput() {
        Map<String, Object> result = new LinkedHashMap<>();
        Object constants = node.config().get("constants");
        if (constants instanceof Map<?, ?> map) map.forEach((key, value) -> result.put(String.valueOf(key), value));
        Object mappings = node.config().get("inputMappings");
        if (mappings instanceof Map<?, ?> map) {
            map.forEach((target, source) -> result.put(String.valueOf(target), variables.get(String.valueOf(source))));
        }
        return result;
    }

    public String text(String key, String fallback) {
        Object value = node.config().get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    public int integer(String key, int fallback) {
        Object value = node.config().get(key);
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
