package com.myagent.agent.tool.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.tool.ToolHandler;
import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Tool Gateway 实现：统一安全管道。
 * <p>
 * 固定管道：执行身份获取 → 定义加载 → 启用检查 → 来源允许 → 权限判断 →
 * 输入校验 → 执行（超时） → 脱敏 → 审计。
 * 模型传入的 userId/tenantId/角色一律不可信，身份以 {@link ExecutionIdentity} 为准。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolGatewayImpl implements ToolGateway {

    private final PolicyDecisionService policyDecisionService;
    private final ObjectMapper objectMapper;

    private final Map<String, ToolEntry> registry = new ConcurrentHashMap<>();
    private final ExecutorService executionPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "tool-gateway-exec");
        t.setDaemon(true);
        return t;
    });

    private record ToolEntry(ToolDefinition definition, ToolHandler handler) {}

    @Override
    public void register(ToolDefinition definition, ToolHandler handler) {
        if (definition == null || definition.getName() == null) {
            throw new IllegalArgumentException("工具定义缺少名称");
        }
        registry.put(definition.getName(), new ToolEntry(definition, handler));
    }

    @Override
    public boolean isAvailable(String toolName) {
        ToolEntry e = registry.get(toolName);
        return e != null && e.definition().isEnabled();
    }

    @Override
    public ToolResult invoke(ToolInvocation invocation) {
        String traceId = ensureTrace(invocation);
        // 1. 执行身份获取（fail-closed）
        ExecutionIdentity identity = invocation.getIdentity();
        if (identity == null || identity.getUserId() == null) {
            audit(invocation, traceId, "DENY", "缺少已认证的执行身份");
            return ToolResult.error("UNAUTHENTICATED", "缺少已认证的执行身份", traceId);
        }
        // 2. 定义加载
        ToolEntry entry = registry.get(invocation.getToolName());
        if (entry == null) {
            audit(invocation, traceId, "DENY", "未注册工具: " + invocation.getToolName());
            return ToolResult.error("TOOL_NOT_FOUND", "未注册工具", traceId);
        }
        ToolDefinition def = entry.definition();
        // 3. 启用检查
        if (!def.isEnabled()) {
            audit(invocation, traceId, "DENY", "工具已停用: " + invocation.getToolName());
            return ToolResult.error("TOOL_DISABLED", "工具已停用", traceId);
        }
        // 4. 来源允许
        if (def.getAllowedSources() != null && !def.getAllowedSources().isEmpty()
                && invocation.getSource() != null && !def.getAllowedSources().contains(invocation.getSource())) {
            audit(invocation, traceId, "DENY", "来源不被允许: " + invocation.getSource());
            return ToolResult.error("SOURCE_NOT_ALLOWED", "调用来源不被允许", traceId);
        }
        // 5. 权限判断（默认拒绝）
        if (def.getPermissionCode() != null && !def.getPermissionCode().isBlank()) {
            if (!policyDecisionService.hasPermission(identity, def.getPermissionCode())) {
                try {
                    policyDecisionService.requirePermission(identity, def.getPermissionCode());
                } catch (ClientException ex) {
                    audit(invocation, traceId, "DENY", "权限拒绝: " + def.getPermissionCode());
                    return ToolResult.error("FORBIDDEN", "无权限", traceId);
                }
                return ToolResult.error("FORBIDDEN", "无权限", traceId);
            }
        }
        // 6. 输入校验
        try {
            validateInput(def, invocation.getInput());
        } catch (ClientException ex) {
            audit(invocation, traceId, "DENY", "输入校验失败: " + ex.getMessage());
            return ToolResult.error("VALIDATION_ERROR", ex.getMessage(), traceId);
        }
        // 7. 写工具幂等键必填
        if (!def.isReadOnly() && def.isIdempotent()) {
            if (invocation.getIdempotencyKey() == null || invocation.getIdempotencyKey().isBlank()) {
                audit(invocation, traceId, "DENY", "写工具缺少幂等键");
                return ToolResult.error("MISSING_IDEMPOTENCY_KEY", "写操作缺少幂等键", traceId);
            }
        }
        // 8. 执行（超时 + 重试）
        ToolResult result = executeWithTimeoutAndRetry(entry, invocation, traceId);
        // 9. 脱敏（输出中敏感字段）
        ToolResult masked = maskResult(def, result, traceId);
        // 10. 审计
        audit(invocation, traceId, masked.getState().name(), "tool=" + invocation.getToolName() + " state=" + masked.getState());
        return masked;
    }

    private ToolResult executeWithTimeoutAndRetry(ToolEntry entry, ToolInvocation invocation, String traceId) {
        ToolDefinition def = entry.definition();
        int attempts = Math.max(1, def.getMaxRetries() + 1);
        Exception last = null;
        for (int i = 0; i < attempts; i++) {
            Future<ToolResult> future = executionPool.submit(() -> entry.handler().handle(invocation));
            try {
                return future.get(def.getTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (Exception ex) {
                future.cancel(true);
                Throwable cause = (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null)
                        ? ex.getCause() : ex;
                // 业务可预期异常（参数/权限）→ VALIDATION_OR_PERMISSION，暴露安全消息
                if (cause instanceof ClientException ce) {
                    audit(invocation, traceId, "DENY", "业务校验失败: " + ce.getMessage());
                    return ToolResult.error("VALIDATION_OR_PERMISSION", ce.getMessage(), traceId);
                }
                last = new Exception(cause);
                if (i < attempts - 1 && isTransient(ex)) {
                    log.warn("工具执行失败将重试 tool={} attempt={} err={}", invocation.getToolName(), i + 1, cause.getMessage());
                    continue;
                }
            }
        }
        log.error("工具执行最终失败 tool={} traceId={} err={}", invocation.getToolName(), traceId, last == null ? "" : last.getMessage());
        return ToolResult.error("INTERNAL_ERROR", "业务工具暂不可用，操作未确认时不会提交", traceId);
    }

    private boolean isTransient(Throwable ex) {
        if (ex instanceof java.util.concurrent.TimeoutException) return true;
        if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null) return isTransient(ex.getCause());
        return false;
    }

    /** 轻量输入校验：必填、类型。多余字段不硬拒（schema 声明 additionalProperties=false 仅引导模型，运行时忽略多余字段以防伪造身份被误用） */
    @SuppressWarnings("unchecked")
    private void validateInput(ToolDefinition def, Map<String, Object> input) {
        Map<String, Object> schema = def.getInputSchema();
        if (schema == null) return;
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
        List<String> required = (List<String>) schema.getOrDefault("required", List.of());
        Map<String, Object> in = input == null ? Map.of() : input;
        for (String req : required) {
            Object v = in.get(req);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new ClientException("缺少参数：" + req);
            }
        }
        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            Object val = in.get(prop.getKey());
            if (val == null) continue;
            Map<String, Object> propSchema = (Map<String, Object>) prop.getValue();
            String type = (String) propSchema.get("type");
            validateType(prop.getKey(), val, type);
        }
    }

    private void validateType(String key, Object val, String type) {
        if (type == null) return;
        boolean ok = switch (type) {
            case "string" -> val instanceof String;
            case "integer" -> val instanceof Integer || val instanceof Long || (val instanceof String s && s.matches("-?\\d+"));
            case "number" -> val instanceof Number;
            case "boolean" -> val instanceof Boolean;
            default -> true;
        };
        if (!ok) throw new ClientException("参数类型错误：" + key + " 应为 " + type);
    }

    /** 脱敏：将敏感字段值替换为 *** */
    private ToolResult maskResult(ToolDefinition def, ToolResult result, String traceId) {
        if (result.getOutput() == null || def.getSensitiveFields() == null || def.getSensitiveFields().isEmpty()) {
            return result;
        }
        try {
            Object masked = maskObject(result.getOutput(), def.getSensitiveFields());
            return ToolResult.builder().state(result.getState()).output(masked)
                    .errorCode(result.getErrorCode()).message(result.getMessage()).traceId(traceId).build();
        } catch (Exception e) {
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private Object maskObject(Object output, List<String> sensitiveFields) {
        if (output instanceof Map) {
            Map<String, Object> copy = new HashMap<>((Map<String, Object>) output);
            for (String f : sensitiveFields) {
                if (copy.containsKey(f)) copy.put(f, "***");
            }
            return copy;
        }
        return output;
    }

    private String ensureTrace(ToolInvocation invocation) {
        if (invocation.getTraceId() != null && !invocation.getTraceId().isBlank()) return invocation.getTraceId();
        return UUID.randomUUID().toString();
    }

    private void audit(ToolInvocation invocation, String traceId, String action, String reason) {
        // 日志脱敏：不打印完整输入参数
        log.info("tool-gateway action={} tool={} user={} traceId={} reason={}",
                action, invocation.getToolName(),
                invocation.getIdentity() == null ? null : invocation.getIdentity().getUserId(),
                traceId, reason);
    }
}
