package com.myagent.workflow.service;

import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.dao.entity.WfVersionDO;
import com.myagent.workflow.enums.VersionStatus;
import com.myagent.workflow.validation.BpmnValidator;
import com.myagent.workflow.validation.BpmnValidator.ValidationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

/**
 * 工作流版本生命周期服务：草稿 -> 审核 -> 发布 -> 退休，支持回滚。
 * 使用 JdbcTemplate + KeyHolder，DB 无关（H2/PostgreSQL 通用），便于测试。
 * 发布版本不可编辑；编辑者不得审核自己提交；发布前经 BPMN 安全校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowVersionService {

    private final JdbcTemplate jdbc;
    private final BpmnValidator bpmnValidator;

    @Transactional
    public WfVersionDO createDraft(String defKey, String name, String description, String bpmnXml, String creator) {
        if (defKey == null || defKey.isBlank()) throw new ClientException("defKey 不能为空");
        Integer exists = jdbc.queryForObject("SELECT count(*) FROM wf_definition WHERE def_key=?", Integer.class, defKey);
        if (exists != null && exists > 0) throw new ClientException("工作流键已存在：" + defKey);
        validateBpmn(bpmnXml);
        KeyHolder defKeyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            // 显式指定 id 列：PostgreSQL RETURN_GENERATED_KEYS 会返回整行，getKey() 会因多列失败
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO wf_definition(def_key,name,description,tenant_id,create_time,update_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    new String[]{"id"});
            ps.setString(1, defKey);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, "default");
            return ps;
        }, defKeyHolder);
        Long defId = generatedId(defKeyHolder);
        Long versionId = insertVersion(defId, 1, bpmnXml, creator, null);
        writeAudit("WF_CREATE", creator, "wf_definition", String.valueOf(defId),
                "defKey=" + defKey + ",versionId=" + versionId);
        return getVersion(versionId);
    }

    @Transactional
    public WfVersionDO newVersionFrom(Long sourceVersionId, String bpmnXml, String editor) {
        WfVersionDO source = getVersion(sourceVersionId);
        // 这里创建的是一条全新的草稿记录，并不会修改来源版本。
        // 已发布版本正是最常见的迭代基线，因此允许作为新版本来源。
        validateBpmn(bpmnXml);
        int next = nextVersionNumber(source.getDefinitionId());
        Long id = insertVersion(source.getDefinitionId(), next, bpmnXml, editor, source.getId());
        writeAudit("WF_NEW_VERSION", editor, "wf_version", String.valueOf(id),
                "fromVersionId=" + sourceVersionId + ",definitionId=" + source.getDefinitionId());
        return getVersion(id);
    }

    @Transactional
    public void submitForReview(Long versionId, String submitter) {
        WfVersionDO v = getVersion(versionId);
        requireStatus(v, VersionStatus.DRAFT, "仅草稿可提交审核");
        updateStatus(v, VersionStatus.IN_REVIEW);
        writeAudit("WF_SUBMIT_REVIEW", submitter, "wf_version", String.valueOf(versionId), null);
    }

    @Transactional
    public void review(Long versionId, String reviewer, boolean approve, String comment) {
        WfVersionDO v = getVersion(versionId);
        requireStatus(v, VersionStatus.IN_REVIEW, "仅审核中状态可审核");
        if (reviewer.equals(v.getCreatedBy()))
            throw new ClientException("编辑者不得审核自己提交的工作流");
        jdbc.update("INSERT INTO wf_review(version_id,reviewer_id,action,comment,created_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",
                versionId, reviewer, approve ? "APPROVE" : "REJECT", comment);
        updateStatus(v, approve ? VersionStatus.APPROVED : VersionStatus.REJECTED);
        writeAudit(approve ? "WF_APPROVE" : "WF_REJECT", reviewer, "wf_version", String.valueOf(versionId), comment);
    }

    @Transactional
    public void publish(Long versionId, String publisher) {
        WfVersionDO v = getVersion(versionId);
        requireStatus(v, VersionStatus.APPROVED, "仅已批准版本可发布");
        ValidationResult r = bpmnValidator.validateForPublish(v.getBpmnXml());
        if (!r.valid()) throw new ClientException("发布校验失败：" + r.reason());
        jdbc.update("UPDATE wf_version SET status=?,published_time=CURRENT_TIMESTAMP,published_by=?,updated_time=CURRENT_TIMESTAMP WHERE id=?",
                VersionStatus.PUBLISHED.name(), publisher, versionId);
        jdbc.update("UPDATE wf_definition SET current_published_version_id=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                versionId, v.getDefinitionId());
        writeAudit("WF_PUBLISH", publisher, "wf_version", String.valueOf(versionId),
                "definitionId=" + v.getDefinitionId());
    }

    @Transactional
    public void retire(Long versionId, String operator) {
        WfVersionDO v = getVersion(versionId);
        requireStatus(v, VersionStatus.PUBLISHED, "仅已发布版本可退休");
        jdbc.update("UPDATE wf_version SET status=?,updated_time=CURRENT_TIMESTAMP WHERE id=?", VersionStatus.RETIRED.name(), versionId);
        jdbc.update("UPDATE wf_definition SET current_published_version_id=NULL,update_time=CURRENT_TIMESTAMP WHERE current_published_version_id=?", versionId);
        writeAudit("WF_RETIRE", operator, "wf_version", String.valueOf(versionId), null);
    }

    @Transactional
    public void rollback(Long definitionId, Long targetVersionId, String operator) {
        Integer cnt = jdbc.queryForObject("SELECT count(*) FROM wf_version WHERE id=? AND definition_id=? AND status=?",
                Integer.class, targetVersionId, definitionId, VersionStatus.PUBLISHED.name());
        if (cnt == null || cnt == 0) throw new ClientException("回滚目标版本不存在或非已发布");
        jdbc.update("UPDATE wf_definition SET current_published_version_id=?,update_time=CURRENT_TIMESTAMP WHERE id=?", targetVersionId, definitionId);
        writeAudit("WF_ROLLBACK", operator, "wf_definition", String.valueOf(definitionId),
                "targetVersionId=" + targetVersionId);
    }

    // ===== 内部 =====

    private Long insertVersion(Long definitionId, int number, String bpmnXml, String creator, Long parentId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO wf_version(definition_id,version_number,bpmn_xml,status,created_by,created_time,updated_time,parent_version_id) "
                            + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                    new String[]{"id"});
            ps.setLong(1, definitionId);
            ps.setInt(2, number);
            ps.setString(3, bpmnXml);
            ps.setString(4, VersionStatus.DRAFT.name());
            ps.setString(5, creator);
            if (parentId == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, parentId);
            return ps;
        }, kh);
        return generatedId(kh);
    }

    /** 从 KeyHolder 取自增 id；兼容 H2 单列与 PostgreSQL 多列返回。 */
    private static Long generatedId(KeyHolder holder) {
        var keys = holder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object id = keys.containsKey("id") ? keys.get("id") : keys.values().iterator().next();
            if (id instanceof Number n) {
                return n.longValue();
            }
            throw new IllegalStateException("自增主键类型异常: " + id);
        }
        Number key = holder.getKey();
        if (key != null) {
            return key.longValue();
        }
        throw new IllegalStateException("未获取到自增主键");
    }

    private int nextVersionNumber(Long definitionId) {
        return jdbc.query("SELECT version_number FROM wf_version WHERE definition_id=? ORDER BY version_number DESC",
                (rs, i) -> rs.getInt("version_number"), definitionId).stream()
                .mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    private WfVersionDO getVersion(Long id) {
        return jdbc.queryForObject(
                "SELECT id,definition_id,version_number,status,created_by,parent_version_id,bpmn_xml FROM wf_version WHERE id=?",
                (rs, i) -> {
                    WfVersionDO v = new WfVersionDO();
                    v.setId(rs.getLong("id"));
                    v.setDefinitionId(rs.getLong("definition_id"));
                    v.setVersionNumber(rs.getInt("version_number"));
                    v.setStatus(rs.getString("status"));
                    v.setCreatedBy(rs.getString("created_by"));
                    Object p = rs.getObject("parent_version_id");
                    v.setParentVersionId(p == null ? null : ((Number) p).longValue());
                    v.setBpmnXml(rs.getString("bpmn_xml"));
                    return v;
                }, id);
    }

    private void validateBpmn(String bpmnXml) {
        ValidationResult r = bpmnValidator.validateSyntaxAndNodes(bpmnXml);
        if (!r.valid()) throw new ClientException("BPMN 校验失败：" + r.reason());
    }

    private void requireStatus(WfVersionDO v, VersionStatus expected, String msg) {
        if (!v.getStatus().equals(expected.name()))
            throw new ClientException(msg + "（当前状态：" + v.getStatus() + "）");
    }

    private void updateStatus(WfVersionDO v, VersionStatus status) {
        jdbc.update("UPDATE wf_version SET status=?,updated_time=CURRENT_TIMESTAMP WHERE id=?", status.name(), v.getId());
    }

    private void writeAudit(String operation, String operatorId, String targetType, String targetId, String detail) {
        jdbc.update(
                "INSERT INTO operation_audit(operation,operator_id,target_type,target_id,tenant_id,detail,created_time) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                operation, operatorId, targetType, targetId, "default", detail);
    }
}
