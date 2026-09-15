package com.myagent.agent.controller;

import com.myagent.agent.tool.gateway.ToolExecutionAuditService;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.rag.trace.RagTraceAuditService;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditHealthControllerTest {

    @AfterEach void clear() { UserContext.clear(); }

    @Test
    void requiresAuditPermissionAndReportsDegradedCounters() {
        RagTraceAuditService reasoning = mock(RagTraceAuditService.class);
        ToolExecutionAuditService tools = mock(ToolExecutionAuditService.class);
        PolicyDecisionService policy = mock(PolicyDecisionService.class);
        when(reasoning.writeFailureCount()).thenReturn(2L);
        when(tools.writeFailureCount()).thenReturn(1L);
        when(policy.auditWriteFailureCount()).thenReturn(3L);
        UserContext.set(LoginUser.builder().userId("auditor").tenantId("tenant-a").build());

        var result = new AuditHealthController(reasoning, tools, policy).health();

        assertThat(result.getData().status()).isEqualTo("DEGRADED");
        assertThat(result.getData().reasoningTraceWriteFailures()).isEqualTo(2L);
        assertThat(result.getData().toolAuditWriteFailures()).isEqualTo(1L);
        assertThat(result.getData().securityAuditWriteFailures()).isEqualTo(3L);
        verify(policy).requirePermission(any(), eq("audit:view"));
    }
}
