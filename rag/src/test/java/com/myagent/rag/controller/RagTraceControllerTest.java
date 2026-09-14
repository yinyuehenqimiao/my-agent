package com.myagent.rag.controller;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.rag.controller.request.RagTraceRunPageRequest;
import com.myagent.rag.trace.RagTraceAuditService;
import com.myagent.system.auth.service.PolicyDecisionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RagTraceControllerTest {

    private RagTraceAuditService auditService;
    private PolicyDecisionService policy;
    private RagTraceController controller;

    @BeforeEach
    void setUp() {
        auditService = mock(RagTraceAuditService.class);
        policy = mock(PolicyDecisionService.class);
        controller = new RagTraceController(auditService, policy);
        UserContext.set(LoginUser.builder().userId("auditor").tenantId("tenant-a").build());
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void listRequiresAuditPermissionAndDelegatesWithServerIdentity() {
        RagTraceRunPageRequest request = new RagTraceRunPageRequest();

        controller.pageRuns(request);

        verify(policy).requirePermission(any(), eq("audit:view"));
        verify(auditService).page(org.mockito.ArgumentMatchers.argThat(
                identity -> "tenant-a".equals(identity.getTenantId())), eq(request));
    }

    @Test
    void deniedDetailNeverQueriesTraceStore() {
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("audit:view"));

        assertThatThrownBy(() -> controller.detail("trace-1")).isInstanceOf(ClientException.class);

        verifyNoInteractions(auditService);
    }
}
