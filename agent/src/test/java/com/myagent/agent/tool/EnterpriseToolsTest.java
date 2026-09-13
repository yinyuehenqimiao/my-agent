package com.myagent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.agent.tool.gateway.ToolGatewayImpl;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnterpriseToolsTest {
    private final EnterpriseService service = mock(EnterpriseService.class);
    private final PolicyDecisionService policy = mock(PolicyDecisionService.class);
    private final ToolGatewayImpl gateway = new ToolGatewayImpl(policy, new ObjectMapper().findAndRegisterModules());
    private final EnterpriseTools tools = new EnterpriseTools(service, new ObjectMapper().findAndRegisterModules(),
            Clock.system(ZoneId.of("Asia/Shanghai")), gateway);

    EnterpriseToolsTest() {
        // @PostConstruct 不会在 new 时触发，手动注册工具到网关
        tools.registerTools();
    }

    private AgentTool tool(String name) {
        return tools.tools().stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }

    private ToolCallParam param(Map<String, Object> input) {
        return ToolCallParam.builder().input(input)
                .runtimeContext(RuntimeContext.builder().userId("employee").sessionId("session").build()).build();
    }

    @Test
    void missingRuntimeIdentityCannotInvokeBusinessService() {
        var r = tool("get_my_profile").callAsync(ToolCallParam.builder().input(Map.of()).build()).block();
        assertEquals(ToolResultState.ERROR, r.getState());
        verifyNoInteractions(service);
    }

    @Test
    void inputCannotImpersonateAnotherEmployee() {
        when(service.profile("employee")).thenReturn(new Employee("employee", "员工", "研发", "EMPLOYEE", "m", "h", true));
        var r = tool("get_my_profile").callAsync(param(Map.of("userId", "admin"))).block();
        // 模型传入的 userId 被忽略，使用服务端签发身份 employee
        assertEquals(ToolResultState.SUCCESS, r.getState());
        verify(service).profile("employee");
        verify(service, never()).profile("admin");
    }

    @Test
    void missingLeaveParametersDoNotCreateDraft() {
        var r = tool("prepare_leave_request").callAsync(param(Map.of("leaveType", "ANNUAL"))).block();
        assertEquals(ToolResultState.ERROR, r.getState());
        verifyNoInteractions(service);
    }

    @Test
    void preparationNeverCallsConfirmation() {
        when(service.prepareLeave(eq("employee"), any())).thenReturn(new Action("a", "employee", "session", "SUBMIT", "r", "{}", "PENDING", null, LocalDateTime.now().plusMinutes(15), LocalDateTime.now(), "key"));
        var r = tool("prepare_leave_request").callAsync(param(Map.of("leaveType", "ANNUAL", "startDate", "2026-10-12", "endDate", "2026-10-13", "reason", "休息"))).block();
        assertTrue(((TextBlock) r.getOutput().get(0)).getText().contains("AWAITING_CONFIRMATION"));
        verify(service, never()).confirm(anyString(), anyString());
    }

    @Test
    void serviceFailureCannotBeReportedAsSuccess() {
        when(service.balance(anyString(), anyInt())).thenThrow(new ClientException("余额账户不可用"));
        var r = tool("get_leave_balance").callAsync(param(Map.of())).block();
        assertEquals(ToolResultState.ERROR, r.getState());
        assertTrue(((TextBlock) r.getOutput().get(0)).getText().contains("VALIDATION_OR_PERMISSION"));
    }

    @Test
    void internalErrorsDoNotExposeSensitiveExceptionText() {
        when(service.profile(anyString())).thenThrow(new IllegalStateException("password=secret"));
        var r = tool("get_my_profile").callAsync(param(Map.of())).block();
        assertEquals(ToolResultState.ERROR, r.getState());
        assertFalse(((TextBlock) r.getOutput().get(0)).getText().contains("secret"));
    }

    @Test
    void agentToolMustExposeRegisteredJsonSchemaInsteadOfPermissiveEmptySchema() {
        AgentTool tool = tool("prepare_leave_request");

        assertEquals("生成请假确认卡，尚未提交。缺少类型、日期或原因应先询问；用户点击卡片后才提交。", tool.getDescription());
        assertEquals(false, tool.getParameters().get("additionalProperties"));
        assertEquals(java.util.List.of("leaveType", "startDate", "endDate", "reason"),
                tool.getParameters().get("required"));
    }
}
