package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.dao.entity.WfDefinitionDO;
import com.myagent.workflow.dao.entity.WfVersionDO;
import com.myagent.workflow.service.WorkflowVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工作流管理后台接口：定义/版本 CRUD、提交审核、审核、发布、退休、回滚、审计查询。
 * <p>
 * 后端权限为最终边界：workflow:edit（编辑/提交）、workflow:review（审核）、workflow:publish（发布/回滚）、audit:view（审计）。
 * 编辑者不得审核自己提交（由服务层强制）。
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

    /** 列出工作流定义 */
    @GetMapping("/definitions")
    public Result<List<Map<String, Object>>> listDefinitions() {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        return Results.success(jdbc.queryForList(
                "SELECT id,def_key,name,description,current_published_version_id FROM wf_definition WHERE tenant_id='default' ORDER BY id"));
    }

    /** 获取定义与其版本列表 */
    @GetMapping("/definitions/{defKey}")
    public Result<Map<String, Object>> getDefinition(@PathVariable String defKey) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        Map<String, Object> def = jdbc.queryForMap(
                "SELECT id,def_key,name,description,current_published_version_id FROM wf_definition WHERE def_key=?", defKey);
        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT id,version_number,status,created_by,created_time,published_time,published_by FROM wf_version WHERE definition_id=? ORDER BY version_number",
                def.get("id"));
        return Results.success(Map.of("definition", def, "versions", versions));
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
        versionService.review(versionId, currentActor(), req.approve(), req.comment());
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

    /** 审计查询（最近 N 条） */
    @GetMapping("/audit")
    public Result<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "100") int limit) {
        policy.requirePermission(UserContext.requireIdentity(), "audit:view");
        return Results.success(jdbc.queryForList(
                "SELECT id,operation,operator_id,target_type,target_id,trace_id,created_time FROM operation_audit ORDER BY created_time DESC LIMIT ?", limit));
    }

    private String currentActor() {
        return UserContext.requireIdentity().getUserId();
    }

    // ===== 请求 DTO =====

    public record CreateDefinitionRequest(String defKey, String name, String description, String bpmnXml) {
    }

    public record NewVersionRequest(Long sourceVersionId, String bpmnXml) {
    }

    public record ReviewRequest(Boolean approve, String comment) {
    }
}
