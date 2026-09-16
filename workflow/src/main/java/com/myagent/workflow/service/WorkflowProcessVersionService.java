package com.myagent.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.model.WorkflowProcess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

/** 动态流程 JSON 草稿与版本服务；BPMN 仅由受控编译器生成。 */
@Service
@RequiredArgsConstructor
public class WorkflowProcessVersionService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowProcessCompiler compiler;

    @Transactional
    public VersionResult create(String defKey, String name, String description, WorkflowProcess process, String creator) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM wf_definition WHERE def_key=?", Integer.class, defKey);
        if (count != null && count > 0) throw new ClientException("工作流键已存在：" + defKey);
        var compiled = compiler.compile(defKey, process);
        GeneratedKeyHolder definitionKey = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wf_definition(def_key,name,description,tenant_id,create_time,update_time) VALUES(?,?,?,'default',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", new String[]{"id"});
            statement.setString(1, defKey); statement.setString(2, name); statement.setString(3, description); return statement;
        }, definitionKey);
        long definitionId = key(definitionKey);
        return insert(definitionId, 1, process, compiled, creator, null);
    }

    @Transactional
    public VersionResult updateDraft(long versionId, long expectedLockVersion, WorkflowProcess process, String editor) {
        var row = jdbc.queryForMap("SELECT d.def_key,v.status,v.lock_version FROM wf_version v JOIN wf_definition d ON d.id=v.definition_id WHERE v.id=?", versionId);
        if (!"DRAFT".equals(row.get("status"))) throw new ClientException("仅草稿版本可以保存");
        var compiled = compiler.compile(String.valueOf(row.get("def_key")), process);
        int changed = jdbc.update("UPDATE wf_version SET process_json=?,bpmn_xml=?,content_hash=?,dependency_snapshot=?,lock_version=lock_version+1,tested_content_hash=NULL,reviewed_content_hash=NULL,updated_time=CURRENT_TIMESTAMP WHERE id=? AND status='DRAFT' AND lock_version=?",
                json(process), compiled.bpmnXml(), compiled.contentHash(), json(process.dependencies()), versionId, expectedLockVersion);
        if (changed != 1) throw new ClientException("草稿已被他人修改，请刷新后重试");
        return new VersionResult(versionId, expectedLockVersion + 1, compiled.contentHash());
    }

    @Transactional
    public VersionResult copy(long sourceVersionId, WorkflowProcess process, String editor) {
        var row = jdbc.queryForMap("SELECT d.id,d.def_key FROM wf_version v JOIN wf_definition d ON d.id=v.definition_id WHERE v.id=?", sourceVersionId);
        long definitionId = ((Number) row.get("id")).longValue();
        Integer next = jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0)+1 FROM wf_version WHERE definition_id=?", Integer.class, definitionId);
        var compiled = compiler.compile(String.valueOf(row.get("def_key")), process);
        return insert(definitionId, next == null ? 1 : next, process, compiled, editor, sourceVersionId);
    }

    private VersionResult insert(long definitionId, int versionNumber, WorkflowProcess process,
                                 WorkflowProcessCompiler.CompiledProcess compiled, String creator, Long parentId) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO wf_version(definition_id,version_number,bpmn_xml,process_json,content_hash,dependency_snapshot,source_type,lock_version,status,created_by,created_time,updated_time,parent_version_id) VALUES(?,?,?,?,?,?,'PROCESS_JSON',0,'DRAFT',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)", new String[]{"id"});
            statement.setLong(1, definitionId); statement.setInt(2, versionNumber); statement.setString(3, compiled.bpmnXml());
            statement.setString(4, json(process)); statement.setString(5, compiled.contentHash()); statement.setString(6, json(process.dependencies()));
            statement.setString(7, creator); if (parentId == null) statement.setNull(8, java.sql.Types.BIGINT); else statement.setLong(8, parentId); return statement;
        }, holder);
        return new VersionResult(key(holder), 0, compiled.contentHash());
    }

    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new ClientException("流程 JSON 序列化失败"); } }
    private static long key(GeneratedKeyHolder holder) {
        var keys = holder.getKeys();
        Object value = keys == null ? null : (keys.containsKey("id") ? keys.get("id") : keys.values().stream().findFirst().orElse(null));
        if (value instanceof Number number) return number.longValue();
        Number single = holder.getKey();
        if (single != null) return single.longValue();
        throw new IllegalStateException("未获取到自增主键");
    }

    public record VersionResult(long versionId, long lockVersion, String contentHash) { }
}
