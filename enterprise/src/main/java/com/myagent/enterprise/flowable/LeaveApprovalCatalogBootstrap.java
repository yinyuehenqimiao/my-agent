package com.myagent.enterprise.flowable;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动时：部署 leave-approval 到 Flowable，并同步到工作流中心（wf_definition）便于管理台查看。
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "myagent.enterprise.leave.engine", havingValue = "flowable")
public class LeaveApprovalCatalogBootstrap implements ApplicationRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LeaveApprovalCatalogBootstrap.class);

    private final LeaveApprovalFlowService flowService;
    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        flowService.ensureDeployed();
        Integer exists = jdbc.queryForObject(
                "SELECT count(*) FROM wf_definition WHERE def_key=?", Integer.class, LeaveApprovalFlowService.PROCESS_KEY);
        if (exists != null && exists > 0) {
            log.info("leave-approval already registered in workflow catalog");
            return;
        }
        jdbc.update(
                "INSERT INTO wf_definition(def_key,name,description,tenant_id,create_time,update_time) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                LeaveApprovalFlowService.PROCESS_KEY,
                "请假审批",
                "员工请假：校验→冻结→主管审批→人事审批→结算/驳回",
                "default");
        Long defId = jdbc.queryForObject(
                "SELECT id FROM wf_definition WHERE def_key=?", Long.class, LeaveApprovalFlowService.PROCESS_KEY);
        jdbc.update(
                "INSERT INTO wf_version(definition_id,version_number,bpmn_xml,status,created_by,created_time,updated_time,published_time,published_by) "
                        + "VALUES(?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",
                defId, 1, LeaveApprovalBpmn.BPMN, "PUBLISHED", "system", "system");
        Long versionId = jdbc.queryForObject(
                "SELECT id FROM wf_version WHERE definition_id=? AND version_number=1", Long.class, defId);
        jdbc.update(
                "UPDATE wf_definition SET current_published_version_id=?,update_time=CURRENT_TIMESTAMP WHERE id=?",
                versionId, defId);
        jdbc.update(
                "INSERT INTO operation_audit(operation,operator_id,target_type,target_id,tenant_id,detail,created_time) "
                        + "VALUES('WF_PUBLISH','system','wf_definition',?, 'default','bootstrap leave-approval',CURRENT_TIMESTAMP)",
                String.valueOf(defId));
        log.info("leave-approval registered in workflow catalog defId={} versionId={}", defId, versionId);
    }
}
