package com.myagent.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.model.WorkflowProcess;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 无外部副作用的发布前模拟：重新解析编辑源、编译并绑定内容摘要。 */
@Service
@RequiredArgsConstructor
public class WorkflowReleaseGateService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final WorkflowProcessCompiler compiler;

    @Transactional
    public Report simulate(long versionId, String actor) {
        var row = jdbc.queryForMap("SELECT d.def_key,v.process_json,v.content_hash,v.source_type FROM wf_version v JOIN wf_definition d ON d.id=v.definition_id WHERE v.id=?", versionId);
        if (!"PROCESS_JSON".equals(row.get("source_type"))) throw new ClientException("旧 BPMN 流程不使用动态模拟门禁");
        try {
            WorkflowProcess process = objectMapper.readValue(String.valueOf(row.get("process_json")), WorkflowProcess.class);
            var compiled = compiler.compile(String.valueOf(row.get("def_key")), process);
            if (!compiled.contentHash().equals(row.get("content_hash"))) throw new ClientException("草稿内容摘要不一致，请重新保存");
            jdbc.update("INSERT INTO wf_simulation_report(version_id,content_hash,status,report,created_by) VALUES(?,?,'PASSED','未执行外部写入；结构与发布门禁通过',?)", versionId, compiled.contentHash(), actor);
            jdbc.update("UPDATE wf_version SET tested_content_hash=?,updated_time=CURRENT_TIMESTAMP WHERE id=?", compiled.contentHash(), versionId);
            return new Report("PASSED", compiled.contentHash(), "模拟未调用外部写工具");
        } catch (ClientException ex) { throw ex; }
        catch (Exception ex) { throw new ClientException("模拟运行失败：" + ex.getMessage()); }
    }
    public record Report(String status,String contentHash,String message) { }
}
