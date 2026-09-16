package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.dao.entity.WfVersionDO;
import com.myagent.workflow.service.AuditQueryService;
import com.myagent.workflow.service.WorkflowVersionService;
import com.myagent.workflow.service.WorkflowNodeCatalog;
import com.myagent.workflow.service.WorkflowProcessCompiler;
import com.myagent.workflow.model.WorkflowProcess;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作流管理后台接口：定义/版本 CRUD、提交审核、审核、发布、退休、回滚、审计查询。
 * <p>
 * 后端权限为最终边界：workflow:edit（编辑/提交）、workflow:review（审核）、workflow:publish（发布/回滚）、audit:view（审计）。
 * 编辑者不得审核自己提交（由服务层强制）。
 * <p>
 * 响应字段统一 camelCase，避免前端读不到 defKey 等字段。
 */
@RestController
@RequestMapping("/api/workflow")
public class WorkflowAdminController {

    private final WorkflowVersionService versionService;
    private final PolicyDecisionService policy;
    private final JdbcTemplate jdbc;
    private final AuditQueryService auditQueryService;
    private final WorkflowNodeCatalog nodeCatalog;
    private final WorkflowProcessCompiler processCompiler;

    public WorkflowAdminController(WorkflowVersionService versionService, PolicyDecisionService policy,
                                   JdbcTemplate jdbc, AuditQueryService auditQueryService) {
        this(versionService, policy, jdbc, auditQueryService, new WorkflowNodeCatalog());
    }

    public WorkflowAdminController(WorkflowVersionService versionService, PolicyDecisionService policy,
                                   JdbcTemplate jdbc, AuditQueryService auditQueryService,
                                   WorkflowNodeCatalog nodeCatalog) {
        this.versionService = versionService;
        this.policy = policy;
        this.jdbc = jdbc;
        this.auditQueryService = auditQueryService;
        this.nodeCatalog = nodeCatalog;
        this.processCompiler = new WorkflowProcessCompiler();
    }

    // ===== 定义/版本 =====

    /** 返回可视化编排器允许使用的安全节点目录。 */
    @GetMapping("/node-catalog")
    public Result<List<WorkflowNodeCatalog.NodeDefinition>> nodeCatalog() {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        return Results.success(nodeCatalog.list());
    }

    /** 在保存或提交审核前，由服务端校验并编译画布流程。 */
    @PostMapping("/compile")
    public Result<WorkflowProcessCompiler.CompiledProcess> compile(@RequestBody CompileRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        return Results.success(processCompiler.compile(req.defKey(), req.process()));
    }

    /** 创建工作流定义并生成首个草稿版本 */
    @PostMapping("/definitions")
    public Result<Long> createDefinition(@RequestBody CreateDefinitionRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        WfVersionDO v = versionService.createDraft(req.defKey(), req.name(), req.description(), req.bpmnXml(), currentActor());
        return Results.success(v.getId());
    }

    /** 分页列出工作流定义（含最新版本状态、已发布版本号） */
    @GetMapping("/definitions")
    public Result<Map<String, Object>> listDefinitions(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:view");
        long safeCurrent = Math.max(1L, current);
        long safeSize = Math.min(Math.max(size, 1L), 200L);
        String where = " WHERE d.tenant_id = 'default'";
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where += " AND (LOWER(d.def_key) LIKE ? OR LOWER(d.name) LIKE ? OR LOWER(COALESCE(d.description,'')) LIKE ?)";
            String like = "%" + keyword.trim().toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wf_definition d" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safeCurrent - 1L) * safeSize);
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
                         ORDER BY v.version_number DESC LIMIT 1) AS latest_status,
                       (SELECT v.created_by FROM wf_version v
                         WHERE v.definition_id = d.id
                         ORDER BY v.version_number DESC LIMIT 1) AS latest_created_by
                FROM wf_definition d
                """ + where + " ORDER BY d.id DESC LIMIT ? OFFSET ?", pageParams.toArray());
        List<Map<String, Object>> records = rows.stream().map(this::toDefinitionListItem).toList();
        enrichActorNames(records, "latestCreatedBy", "latestCreatedByName");
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("records", records);
        page.put("total", total == null ? 0L : total);
        page.put("current", safeCurrent);
        page.put("size", safeSize);
        return Results.success(page);
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
        List<Map<String, Object>> versionViews = versions.stream().map(this::toVersion).toList();
        enrichActorNames(versionViews, "createdBy", "createdByName");
        enrichActorNames(versionViews, "publishedBy", "publishedByName");
        return Results.success(Map.of(
                "definition", toDefinition(def),
                "versions", versionViews));
    }

    /** 基于已有版本创建新草稿 */
    @PostMapping("/versions")
    public Result<Long> newVersion(@RequestBody NewVersionRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        WfVersionDO v = versionService.newVersionFrom(req.sourceVersionId(), req.bpmnXml(), currentActor());
        return Results.success(v.getId());
    }

    // ===== 生命周期 =====

    @PostMapping("/versions/{versionId}/submit-review")
    public Result<Void> submitReview(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        versionService.submitForReview(versionId, currentActor());
        return Results.success();
    }

    @PostMapping("/versions/{versionId}/review")
    public Result<Void> review(@PathVariable Long versionId, @RequestBody ReviewRequest req) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:review");
        versionService.review(versionId, currentActor(), Boolean.TRUE.equals(req.approve()), req.comment());
        return Results.success();
    }

    @PostMapping("/versions/{versionId}/publish")
    public Result<Void> publish(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.publish(versionId, currentActor());
        return Results.success();
    }

    @PostMapping("/versions/{versionId}/retire")
    public Result<Void> retire(@PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.retire(versionId, currentActor());
        return Results.success();
    }

    @PostMapping("/definitions/{defId}/rollback/{versionId}")
    public Result<Void> rollback(@PathVariable Long defId, @PathVariable Long versionId) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:publish");
        versionService.rollback(defId, versionId, currentActor());
        return Results.success();
    }

    // ===== 审计 =====

    /** 统一审计分页查询；操作人返回用户名。 */
    @GetMapping("/audit")
    public Result<Map<String, Object>> audit(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(defaultValue = "ALL") AuditQueryService.Category category) {
        var identity = UserContext.requireIdentity();
        policy.requirePermission(identity, "audit:view");
        return Results.success(auditQueryService.page(identity, category, current, size).toMap());
    }

    private String currentActor() {
        return UserContext.requireIdentity().getUserId();
    }

    private void enrichActorNames(List<Map<String, Object>> rows, String idKey, String nameKey) {
        Set<String> ids = rows.stream()
                .map(row -> row.get(idKey) == null ? null : String.valueOf(row.get(idKey)))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank() && !"null".equalsIgnoreCase(s))
                .collect(Collectors.toSet());
        Map<String, String> names = auditQueryService.resolveUsernames(ids);
        for (Map<String, Object> row : rows) {
            Object raw = row.get(idKey);
            if (raw == null) {
                continue;
            }
            String id = String.valueOf(raw);
            String name = names.get(id);
            if (name != null) {
                row.put(nameKey, name);
            }
        }
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
        m.put("latestCreatedBy", row.get("latest_created_by"));
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

    public record CreateDefinitionRequest(String defKey, String name, String description, String bpmnXml) {
    }

    public record NewVersionRequest(Long sourceVersionId, String bpmnXml) {
    }

    public record ReviewRequest(Boolean approve, String comment) {
    }

    public record CompileRequest(String defKey, WorkflowProcess process) {
    }
}
