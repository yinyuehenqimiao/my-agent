package com.myagent.agent.service;

import com.myagent.agent.tool.AgentToolCatalog;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCapabilityServiceTest {

    @Test
    void describesOnlyConnectedAndAuthorizedToolsWithTheirSystemNames() {
        ToolGateway gateway = mock(ToolGateway.class);
        AgentToolCatalog catalog = mock(AgentToolCatalog.class);
        PolicyDecisionService policy = mock(PolicyDecisionService.class);
        LoginUser identity = LoginUser.builder().userId("1").tenantId("default").build();
        when(gateway.listDefinitions()).thenReturn(List.of(
                definition("get_leave_balance", true, null, true),
                definition("prepare_leave_request", false, "workflow:submit", true),
                definition("admin_secret", true, "admin:secret", true),
                definition("disabled_tool", true, null, false)));
        when(policy.hasPermission(identity, "workflow:submit")).thenReturn(true);
        when(policy.hasPermission(identity, "admin:secret")).thenReturn(false);
        when(catalog.listAvailableMcpTools()).thenReturn(List.of(
                new AgentToolCatalog.ConnectedTool("sales_query", "销售数据统计", "查询销售汇总")));
        when(catalog.supportsWorkflow("leave-approval")).thenReturn(true);

        String answer = new AgentCapabilityService(gateway, catalog, policy).describe(identity);

        assertThat(answer)
                .contains("公司知识库查询")
                .contains("我的年假余额（get_leave_balance）")
                .contains("生成请假确认卡（prepare_leave_request）")
                .contains("销售数据统计（sales_query）")
                .contains("请假申请")
                .contains("写操作只生成确认卡")
                .doesNotContain("admin_secret")
                .doesNotContain("disabled_tool");
    }

    private ToolDefinition definition(String name, boolean readOnly, String permissionCode, boolean enabled) {
        return ToolDefinition.builder()
                .name(name).description(name).readOnly(readOnly).permissionCode(permissionCode).enabled(enabled)
                .allowedSources(List.of(ToolDefinition.Source.REACT, ToolDefinition.Source.WORKFLOW))
                .build();
    }
}
