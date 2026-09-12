package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.dao.entity.WfVersionDO;
import com.myagent.workflow.service.WorkflowVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流管理后台接口：定义/版本 CRUD、提交审核、审核、发布、退休、回滚、审计查询。
 * <p>
 * 后端权限为最终边界：workflow:edit（编辑/提交）、workflow:review（审核）、workflow:publish（发布/回滚）、audit:view（审计）。
 * 编辑者不得审核自己提交（由服务层强制）。
 * <p>
 * 响应字段统一 camelCase，避免前端读不到 defKey 等字段。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow")
public class WorkflowAdminController {

    private final WorkflowVersionService versionService;
    private final PolicyDecisionService policy;
    private final JdbcTemplate jdbc;

    // ===== 定义/版本 =====

    /** 创建工作流定义并生成首个草稿版本 */
    @PostMapping("/definitions")
    public Result<Long> createDefinition(@RequestBody CreateDefinitionRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        WfVersionDO v = versionService.createDraft(req.defKey(), req.name(), req.description(), req.bpmnXml(), currentActor());
        return Results.success(v.getId());
    }

    /** 列出工作流定义（含最新版本状态、已发布版本号） */
    @GetMapping("/definitions")
    public Result<List<Map<String, Object>>> listDefinitions() {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT d.id,
                       d.def_key,
                       d.name,
                       d.description,
                       d.current_published_version_id,
                       d.update_time,
                       (SELECT v.version_number FROM wf_version v
                         WHERE v.id = d.current_published_version_id) AS published_version_number,
                       (SELECT v.version_number FROM wf_version v
                         WHERE v.definition_id = d.id
                         ORDER BY v.version_number DESC LIMIT 1) AS latest_version_number,
                       (SELECT v.status FROM wf_version v
                         WHERE v.definition_id = d.id
                         ORDER BY v.version_number DESC LIMIT 1) AS latest_status
                FROM wf_definition d
                WHERE d.tenant_id = 'default'
                ORDER BY d.id DESC
                """);
        return Results.success(rows.stream().map(this::toDefinitionListItem).toList());
    }

    /** 获取定义与其版本列表 */
    @GetMapping("/definitions/{defKey}")
    public Result<Map<String, Object>> getDefinition(@PathVariable String defKey) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        Map<String, Object> def;
        try {
            def = jdbc.queryForMap(
                    "SELECT id,def_key,name,description,current_published_version_id,update_time FROM wf_definition WHERE def_key=?",
                    defKey);
        } catch (EmptyResultDataAccessException ex) {
            throw new ClientException("工作流不存在：" + defKey);
        }
        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT id,version_number,status,created_by,created_time,published_time,published_by,bpmn_xml FROM wf_version WHERE definition_id=? ORDER BY version_number",
                def.get("id"));
        return Results.success(Map.of(
                "definition", toDefinition(def),
                "versions", versions.stream().map(this::toVersion).toList()));
    }

    /** 基于已有版本创建新草稿 */
    @PostMapping("/versions")
    public Result<Long> newVersion(@RequestBody NewVersionRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        WfVersionDO v = versionService.newVersionFrom(req.sourceVersionId(), req.bpmnXml(), currentActor());
        return Results.success(v.getId());
    }

    // ===== 生命周期 =====

    /** 提交审核 */
    @PostMapping("/versions/{versionId}/submit-review")
    public Result<Void> submitReview(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        versionService.submitForReview(versionId, currentActor());
        return Results.success();
    }

    /** 审核（approve/reject） */
    @PostMapping("/versions/{versionId}/review")
    public Result<Void> review(@PathVariable Long versionId, @RequestBody ReviewRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:review");
        versionService.review(versionId, currentActor(), Boolean.TRUE.equals(req.approve()), req.comment());
        return Results.success();
    }

    /** 发布 */
    @PostMapping("/versions/{versionId}/publish")
    public Result<Void> publish(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.publish(versionId, currentActor());
        return Results.success();
    }

    /** 退休 */
    @PostMapping("/versions/{versionId}/retire")
    public Result<Void> retire(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.retire(versionId, currentActor());
        return Results.success();
    }

    /** 回滚到指定已发布版本 */
    @PostMapping("/definitions/{defId}/rollback/{versionId}")
    public Result<Void> rollback(@PathVariable Long defId, @PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.rollback(defId, versionId, currentActor());
        return Results.success();
    }

    // ===== 审计 =====

    /**
     * 平台操作审计（operation_audit）：工作流生命周期与后续 Tool Gateway 等平台级操作。
     * 不含请假业务流水（ent_audit）与权限拒绝流水（sys_auth_audit）。
     */
    @GetMapping("/audit")
    public Result<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "100") int limit) {
        policy.requirePermission(UserContext.requireIdentity(), "audit:view");
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,operation,operator_id,target_type,target_id,trace_id,detail,created_time FROM operation_audit ORDER BY created_time DESC LIMIT ?",
                safeLimit);
        return Results.success(rows.stream().map(this::toAudit).toList());
    }

    private String currentActor() {
        return UserContext.requireIdentity().getUserId();
    }

    private Map<String, Object> toDefinitionListItem(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("defKey", row.get("def_key"));
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("currentPublishedVersionId", row.get("current_published_version_id"));
        m.put("publishedVersionNumber", row.get("published_version_number"));
        m.put("latestVersionNumber", row.get("latest_version_number"));
        m.put("latestStatus", row.get("latest_status"));
        m.put("updateTime", row.get("update_time"));
        return m;
    }

    private Map<String, Object> toDefinition(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("defKey", row.get("def_key"));
        m.put("name", row.get("name"));
        m.put("description", row.get("description"));
        m.put("currentPublishedVersionId", row.get("current_published_version_id"));
        m.put("updateTime", row.get("update_time"));
        return m;
    }

    private Map<String, Object> toVersion(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("versionNumber", row.get("version_number"));
        m.put("status", row.get("status"));
        m.put("createdBy", row.get("created_by"));
        m.put("createdTime", row.get("created_time"));
        m.put("publishedTime", row.get("published_time"));
        m.put("publishedBy", row.get("published_by"));
        m.put("bpmnXml", row.get("bpmn_xml"));
        return m;
    }

    private Map<String, Object> toAudit(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.get("id"));
        m.put("operation", row.get("operation"));
        m.put("operatorId", row.get("operator_id"));
        m.put("targetType", row.get("target_type"));
        m.put("targetId", row.get("target_id"));
        m.put("traceId", row.get("trace_id"));
        m.put("detail", row.get("detail"));
        m.put("createdTime", row.get("created_time"));
        return m;
    }

    // ===== 请求 DTO =====

    public record CreateDefinitionRequest(String defKey, String name, String description, String bpmnXml) {
    }

    public record NewVersionRequest(Long sourceVersionId, String bpmnXml) {
    }

    public record ReviewRequest(Boolean approve, String comment) {
    }
}
