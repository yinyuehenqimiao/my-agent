package com.myagent.framework.workflow;

import com.myagent.framework.context.ExecutionIdentity;

import java.util.List;
import java.util.Map;

/** 工作流受限 ReAct 节点端口；实现由 Agent 模块提供，工作流模块不依赖具体模型 SDK。 */
public interface WorkflowReactPort {
    Result execute(ExecutionIdentity identity, Request request);

    record Request(String instruction, Map<String, Object> input, List<String> toolWhitelist,
                   int maxRounds, int maxToolCalls, int timeoutSeconds,
                   Map<String, Object> outputSchema) { }

    record Result(String status, Map<String, Object> output, int rounds, int toolCalls) { }
}
