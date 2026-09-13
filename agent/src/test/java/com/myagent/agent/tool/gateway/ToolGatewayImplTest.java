package com.myagent.agent.tool.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolDefinition.RiskLevel;
import com.myagent.framework.tool.ToolDefinition.Source;
import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 阶段 2 Tool Gateway 安全管道测试。 */
class ToolGatewayImplTest {

    private PolicyDecisionService policy;
    private ToolExecutionAuditService audit;
    private ToolGatewayImpl gateway;

    @BeforeEach
    void setup() {
        policy = mock(PolicyDecisionService.class);
        audit = mock(ToolExecutionAuditService.class);
        when(audit.record(any(), anyString(), anyString(), anyString(), anyLong())).thenReturn(true);
        gateway = new ToolGatewayImpl(policy, new ObjectMapper().findAndRegisterModules(), audit);
    }

    private LoginUser identity(String userId) {
        return LoginUser.builder().userId(userId).username(userId).build();
    }

    private ToolDefinition readOnlyDef(String name, String perm) {
        return ToolDefinition.builder()
                .name(name).description("test " + name)
                .inputSchema(Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string")), "required", List.of("q"), "additionalProperties", false))
                .readOnly(true).riskLevel(RiskLevel.LOW).timeoutMs(5_000L).maxRetries(0)
                .idempotent(false).requiresConfirmation(false)
                .sensitiveFields(List.of()).allowedSources(List.of(Source.REACT, Source.WORKFLOW)).permissionCode(perm).enabled(true)
                .build();
    }

    private ToolDefinition writeDef(String name) {
        return ToolDefinition.builder()
                .name(name).description("write " + name)
                .inputSchema(Map.of("type", "object", "properties", Map.of("reason", Map.of("type", "string")), "required", List.of("reason"), "additionalProperties", false))
                .readOnly(false).riskLevel(RiskLevel.MEDIUM).timeoutMs(5_000L).maxRetries(0)
                .idempotent(true).requiresConfirmation(true)
                .sensitiveFields(List.of("reason")).allowedSources(List.of(Source.REACT, Source.WORKFLOW)).permissionCode(null).enabled(true)
                .build();
    }

    @Test
    void unregisteredToolReturnsNotFound() {
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("nope").identity(identity("u")).source(Source.REACT).build());
        assertEquals(ToolResult.State.ERROR, r.getState());
        assertEquals("TOOL_NOT_FOUND", r.getErrorCode());
    }

    @Test
    void disabledToolReturnsDisabled() {
        ToolDefinition def = readOnlyDef("disabled_tool", null);
        def.setEnabled(false);
        gateway.register(def, inv -> ToolResult.success("ok", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("disabled_tool").identity(identity("u")).source(Source.REACT).build());
        assertEquals("TOOL_DISABLED", r.getErrorCode());
    }

    @Test
    void missingIdentityFailsClosed() {
        gateway.register(readOnlyDef("ro", null), inv -> ToolResult.success("ok", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(null).source(Source.REACT).build());
        assertEquals("UNAUTHENTICATED", r.getErrorCode());
    }

    @Test
    void sourceNotAllowedRejected() {
        gateway.register(readOnlyDef("ro", null), inv -> ToolResult.success("ok", inv.getTraceId()));
        // def 允许 REACT/WORKFLOW，用 MCP 应被拒
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.MCP).build());
        assertEquals("SOURCE_NOT_ALLOWED", r.getErrorCode());
    }

    @Test
    void permissionDeniedReturnsForbidden() {
        gateway.register(readOnlyDef("ro", "workflow:view"), inv -> ToolResult.success("ok", inv.getTraceId()));
        when(policy.hasPermission(any(), eq("workflow:view"))).thenReturn(false);
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("workflow:view"));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.REACT).input(Map.of("q", "x")).build());
        assertEquals("FORBIDDEN", r.getErrorCode());
    }

    @Test
    void permissionGrantedExecutes() {
        gateway.register(readOnlyDef("ro", "workflow:view"), inv -> ToolResult.success("data", inv.getTraceId()));
        when(policy.hasPermission(any(), eq("workflow:view"))).thenReturn(true);
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.REACT).input(Map.of("q", "x")).build());
        assertEquals(ToolResult.State.SUCCESS, r.getState());
        assertEquals("data", r.getOutput());
    }

    @Test
    void missingRequiredInputRejected() {
        gateway.register(readOnlyDef("ro", null), inv -> ToolResult.success("ok", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.REACT).input(Map.of()).build());
        assertEquals("VALIDATION_ERROR", r.getErrorCode());
        assertTrue(r.getMessage().contains("缺少参数"));
    }

    @Test
    void wrongTypeInputRejected() {
        gateway.register(readOnlyDef("ro", null), inv -> ToolResult.success("ok", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.REACT).input(Map.of("q", 123)).build());
        assertEquals("VALIDATION_ERROR", r.getErrorCode());
        assertTrue(r.getMessage().contains("参数类型错误"));
    }

    @Test
    void writeToolRequiresIdempotencyKey() {
        gateway.register(writeDef("write_tool"), inv -> ToolResult.success("done", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("write_tool").identity(identity("u")).source(Source.REACT)
                .input(Map.of("reason", "test")).build());
        assertEquals("MISSING_IDEMPOTENCY_KEY", r.getErrorCode());
    }

    @Test
    void writeToolSucceedsWithIdempotencyKey() {
        gateway.register(writeDef("write_tool"), inv -> ToolResult.success("done", inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("write_tool").identity(identity("u")).source(Source.REACT)
                .input(Map.of("reason", "test")).idempotencyKey("key-1").traceId("t1").build());
        assertEquals(ToolResult.State.SUCCESS, r.getState());
    }

    @Test
    void sensitiveFieldsMaskedInOutput() {
        ToolDefinition def = writeDef("write_tool");
        gateway.register(def, inv -> ToolResult.success(Map.of("reason", "secret-reason", "other", "visible"), inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("write_tool").identity(identity("u")).source(Source.REACT)
                .input(Map.of("reason", "test")).idempotencyKey("k").traceId("t").build());
        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) r.getOutput();
        assertEquals("***", out.get("reason"));
        assertEquals("visible", out.get("other"));
    }

    @Test
    void modelSuppliedUserIdIgnoredByHandler() {
        // 模型伪造身份：input 传 userId=admin，但 handler 使用服务端签发身份 u
        gateway.register(readOnlyDef("ro", null), inv -> ToolResult.success(inv.getIdentity().getUserId(), inv.getTraceId()));
        ToolResult r = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u")).source(Source.REACT)
                .input(Map.of("userId", "admin", "q", "x")).build());
        assertEquals(ToolResult.State.SUCCESS, r.getState());
        assertEquals("u", r.getOutput());
    }

    @Test
    void isAvailableReflectsRegistrationAndEnabled() {
        ToolDefinition def = readOnlyDef("ro", null);
        gateway.register(def, inv -> ToolResult.success("ok", inv.getTraceId()));
        assertTrue(gateway.isAvailable("ro"));
        assertFalse(gateway.isAvailable("nope"));
        def.setEnabled(false);
        assertFalse(gateway.isAvailable("ro"));
    }

    @Test
    void catalogSnapshotIsStableAndSortedForAdministration() {
        gateway.register(readOnlyDef("z_tool", null), inv -> ToolResult.success("z", inv.getTraceId()));
        gateway.register(readOnlyDef("a_tool", null), inv -> ToolResult.success("a", inv.getTraceId()));

        List<ToolDefinition> definitions = gateway.listDefinitions();

        assertEquals(List.of("a_tool", "z_tool"), definitions.stream().map(ToolDefinition::getName).toList());
        assertThrows(UnsupportedOperationException.class, () -> definitions.add(readOnlyDef("bad", null)));
    }

    @Test
    void writeToolDoesNotExecuteWhenDurablePreAuditIsUnavailable() {
        var handler = mock(com.myagent.framework.tool.ToolHandler.class);
        gateway.register(writeDef("write_tool"), handler);
        when(audit.record(any(), anyString(), eq("BEFORE_EXECUTION"), eq("READY"), anyLong())).thenReturn(false);

        ToolResult result = gateway.invoke(ToolInvocation.builder().toolName("write_tool")
                .identity(identity("u")).source(Source.REACT).input(Map.of("reason", "test"))
                .idempotencyKey("key-1").build());

        assertEquals("AUDIT_UNAVAILABLE", result.getErrorCode());
        verifyNoInteractions(handler);
    }

    @Test
    void readOnlyToolMayDegradeWhenAuditStorageIsUnavailable() {
        gateway.register(readOnlyDef("ro", null), invocation -> ToolResult.success("ok", invocation.getTraceId()));
        when(audit.record(any(), anyString(), anyString(), anyString(), anyLong())).thenReturn(false);

        ToolResult result = gateway.invoke(ToolInvocation.builder().toolName("ro").identity(identity("u"))
                .source(Source.REACT).input(Map.of("q", "x")).build());

        assertEquals(ToolResult.State.SUCCESS, result.getState());
    }

    @Test
    void writeToolReturnsUncertainResultWhenCompletionAuditFails() throws Exception {
        var handler = mock(com.myagent.framework.tool.ToolHandler.class);
        when(handler.handle(any())).thenAnswer(invocation ->
                ToolResult.success("done", invocation.<ToolInvocation>getArgument(0).getTraceId()));
        gateway.register(writeDef("write_tool"), handler);
        when(audit.record(any(), anyString(), eq("BEFORE_EXECUTION"), eq("READY"), anyLong())).thenReturn(true);
        when(audit.record(any(), anyString(), eq("COMPLETED"), eq("SUCCESS"), anyLong())).thenReturn(false);

        ToolResult result = gateway.invoke(ToolInvocation.builder().toolName("write_tool")
                .identity(identity("u")).source(Source.REACT).input(Map.of("reason", "test"))
                .idempotencyKey("key-1").build());

        assertEquals("AUDIT_INCOMPLETE", result.getErrorCode());
        assertTrue(result.getMessage().contains("请勿重复提交"));
        verify(handler, times(1)).handle(any());
    }
}
