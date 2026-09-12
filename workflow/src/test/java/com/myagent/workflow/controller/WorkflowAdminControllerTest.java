package com.myagent.workflow.controller;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.service.WorkflowVersionService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 阶段 6 工作流管理后台控制器测试：权限码鉴权 + 委托服务层。 */
class WorkflowAdminControllerTest {

    private WorkflowVersionService versionService;
    private PolicyDecisionService policy;
    private JdbcTemplate jdbc;
    private WorkflowAdminController controller;

    @BeforeEach
    void setUp() {
        versionService = mock(WorkflowVersionService.class);
        policy = mock(PolicyDecisionService.class);
        jdbc = mock(JdbcTemplate.class);
        controller = new WorkflowAdminController(versionService, policy, jdbc);
        UserContext.set(LoginUser.builder().userId("u1").username("u1").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createDefinitionRequiresWorkflowEditPermission() {
        // 权限拒绝 → 抛异常，不调用服务
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("workflow:edit"));
        assertThatThrownBy(() -> controller.createDefinition(
                new WorkflowAdminController.CreateDefinitionRequest("k", "n", null, "<xml/>")))
                .isInstanceOf(ClientException.class);
        verifyNoInteractions(versionService);
    }

    @Test
    void createDefinitionDelegatesToServiceWhenPermitted() {
        com.myagent.workflow.dao.entity.WfVersionDO stub = new com.myagent.workflow.dao.entity.WfVersionDO();
        stub.setId(1L);
        when(versionService.createDraft(anyString(), anyString(), any(), anyString(), anyString())).thenReturn(stub);
        var result = controller.createDefinition(
                new WorkflowAdminController.CreateDefinitionRequest("k", "n", null, "<xml/>"));
        assertThat(result.getCode()).isEqualTo("0");
        assertThat(result.getData()).isEqualTo(1L);
        verify(versionService).createDraft(eq("k"), eq("n"), org.mockito.ArgumentMatchers.nullable(String.class), eq("<xml/>"), eq("u1"));
    }

    @Test
    void publishRequiresWorkflowPublishPermission() {
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("workflow:publish"));
        assertThatThrownBy(() -> controller.publish(1L)).isInstanceOf(ClientException.class);
        verifyNoInteractions(versionService);
    }

    @Test
    void reviewRequiresWorkflowReviewPermission() {
        org.mockito.Mockito.doNothing().when(versionService).review(anyLong(), anyString(), anyBoolean(), any());
        var result = controller.review(1L, new WorkflowAdminController.ReviewRequest(true, "ok"));
        assertThat(result.getCode()).isEqualTo("0");
        verify(policy).requirePermission(any(), eq("workflow:review"));
    }

    @Test
    void auditRequiresAuditViewPermission() {
        doThrow(new ClientException("无权限")).when(policy).requirePermission(any(), eq("audit:view"));
        assertThatThrownBy(() -> controller.audit(100)).isInstanceOf(ClientException.class);
    }
}
