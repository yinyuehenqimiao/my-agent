package com.myagent.workflow.controller;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.service.WorkflowVersionService;
import com.myagent.workflow.service.AuditQueryService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 阶段 6 工作流管理后台控制器测试：权限码鉴权 + 委托服务层。 */
class WorkflowAdminControllerTest {

    private WorkflowVersionService versionService;
    private PolicyDecisionService policy;
    private JdbcTemplate jdbc;
    private AuditQueryService auditQueryService;
    private WorkflowAdminController controller;

    @BeforeEach
    void setUp() {
        versionService = mock(WorkflowVersionService.class);
        policy = mock(PolicyDecisionService.class);
        jdbc = mock(JdbcTemplate.class);
        auditQueryService = mock(AuditQueryService.class);
        controller = new WorkflowAdminController(versionService, policy, jdbc, auditQueryService);
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
        assertThatThrownBy(() -> controller.audit(1, 20, AuditQueryService.Category.ALL)).isInstanceOf(ClientException.class);
        verifyNoInteractions(auditQueryService);
    }

    @Test
    void auditDelegatesWithCurrentTenantAndCategory() {
        when(auditQueryService.page(any(), eq(AuditQueryService.Category.TOOL), eq(1L), eq(25L)))
                .thenReturn(new AuditQueryService.PageResult(List.of(), 0, 1, 25));
        controller.audit(1, 25, AuditQueryService.Category.TOOL);

        verify(auditQueryService).page(argThat(identity -> "default".equals(identity.getTenantId())),
                eq(AuditQueryService.Category.TOOL), eq(1L), eq(25L));
    }

    @Test
    void definitionDetailUsesFrontendFriendlyFieldsAndIncludesBpmnXml() {
        Map<String, Object> definition = new HashMap<>();
        definition.put("id", 7L);
        definition.put("def_key", "leave-approval");
        definition.put("name", "请假审批");
        definition.put("description", "两级审批");
        definition.put("current_published_version_id", 71L);
        definition.put("update_time", Timestamp.from(Instant.parse("2026-09-12T08:00:00Z")));
        Map<String, Object> version = new HashMap<>();
        version.put("id", 71L);
        version.put("version_number", 1);
        version.put("status", "PUBLISHED");
        version.put("created_by", "editor");
        version.put("created_time", Timestamp.from(Instant.parse("2026-09-12T07:00:00Z")));
        version.put("published_time", Timestamp.from(Instant.parse("2026-09-12T08:00:00Z")));
        version.put("published_by", "publisher");
        version.put("bpmn_xml", "<definitions/>");
        when(jdbc.queryForMap(anyString(), eq("leave-approval"))).thenReturn(definition);
        when(jdbc.queryForList(anyString(), eq(7L))).thenReturn(List.of(version));

        var result = controller.getDefinition("leave-approval");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        @SuppressWarnings("unchecked")
        Map<String, Object> mappedDefinition = (Map<String, Object>) data.get("definition");
        @SuppressWarnings("unchecked")
        Map<String, Object> mappedVersion = (Map<String, Object>) ((List<?>) data.get("versions")).get(0);

        assertThat(mappedDefinition).containsEntry("defKey", "leave-approval");
        assertThat(mappedDefinition).doesNotContainKey("def_key");
        assertThat(mappedVersion).containsEntry("versionNumber", 1).containsEntry("bpmnXml", "<definitions/>");
        verify(policy).requirePermission(any(), eq("workflow:view"));
    }
}
