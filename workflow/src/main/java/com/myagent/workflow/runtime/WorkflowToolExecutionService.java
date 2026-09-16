package com.myagent.workflow.runtime;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/** 工作流工具节点适配器；所有执行仍由 Tool Gateway 做最终鉴权与审计。 */
@Service
@RequiredArgsConstructor
public class WorkflowToolExecutionService {
    private final ToolGateway gateway;
    private final WorkflowConfirmationService confirmations;

    public ToolResult execute(ExecutionIdentity identity, Command command) {
        ToolDefinition definition = gateway.listDefinitions().stream().filter(item -> item.getName().equals(command.toolName())).findFirst()
                .orElseThrow(() -> new ClientException("工具不存在或未发布"));
        String actionId = command.confirmationId();
        if (!definition.isReadOnly()) {
            if (actionId == null || actionId.isBlank()) throw new ClientException("写工具必须先由用户确认");
            confirmations.consumeConfirmed(identity, actionId, command.confirmationLockVersion(), command.input());
        }
        String idempotencyKey = identity.getTenantId() + ":" + command.instanceId() + ":" + command.nodeInstanceId() + ":" + command.actionVersion();
        ToolResult result = gateway.invoke(ToolInvocation.builder().toolName(command.toolName()).input(command.input())
                .identity(identity).source(ToolDefinition.Source.WORKFLOW).traceId(command.traceId())
                .conversationId(command.conversationId()).idempotencyKey(idempotencyKey).build());
        if (!definition.isReadOnly()) confirmations.finish(actionId,
                result.getState() == ToolResult.State.SUCCESS ? "COMPLETED" : "RESULT_UNCERTAIN", result);
        return result;
    }

    public record Command(String instanceId,String nodeInstanceId,long actionVersion,String toolName,
                          Map<String,Object> input,String confirmationId,long confirmationLockVersion,
                          String conversationId,String traceId) { }
}
