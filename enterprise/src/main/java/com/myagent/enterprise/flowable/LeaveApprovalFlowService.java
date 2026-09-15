package com.myagent.enterprise.flowable;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.LeaveRequest;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 请假审批 Flowable 流程服务：启动实例、完成人工审批、撤回、一致性检查。
 * <p>
 * - 灰度开关 myagent.enterprise.leave.engine=flowable 时启用。
 * - 业务事实保留在 ent_* 表；Flowable 管理流程状态与人工任务。
 * - 实例 businessKey = 请假申请 id；携带 initiatorId/managerId/hrId/traceId。
 * - 一致性检查：流程状态与业务状态不一致时告警。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "myagent.enterprise.leave.engine", havingValue = "flowable")
public class LeaveApprovalFlowService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LeaveApprovalFlowService.class);

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final EnterpriseMapper db;
    private final JdbcTemplate jdbc;

    public static final String PROCESS_KEY = "leave-approval";

    /** 部署 BPMN（幂等） */
    public void ensureDeployed() {
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(PROCESS_KEY).latestVersion().singleResult();
        if (pd != null) return;
        repositoryService.createDeployment()
                .name("leave-approval-v1")
                .key("leave-approval-deploy")
                .addString(PROCESS_KEY + ".bpmn20.xml", LeaveApprovalBpmn.BPMN)
                .deploy();
        log.info("leave-approval process deployed");
    }

    /** 启动流程实例（确认提交后调用） */
    public String start(String requestId, String initiatorId, String traceId) {
        ensureDeployed();
        LeaveRequest r = db.one(LeaveRequest.class, "SELECT * FROM ent_leave_request WHERE id=?", requestId);
        if (r == null) throw new ClientException("请假申请不存在: " + requestId);
        Map<String, Object> vars = new HashMap<>();
        vars.put("initiatorId", initiatorId);
        vars.put("traceId", traceId == null ? java.util.UUID.randomUUID().toString() : traceId);
        vars.put("requestId", requestId);
        vars.put("days", r.days());
        vars.put("leaveType", r.leaveType());
        vars.put("startDate", r.startDate().toString());
        vars.put("endDate", r.endDate().toString());
        vars.put("userId", r.userId());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(PROCESS_KEY, requestId, vars);
        syncApprovalTasks(requestId);
        log.info("leave-approval started request={} instance={} initiator={}", requestId, instance.getId(), initiatorId);
        return instance.getId();
    }

    /** 完成当前审批任务（主管或人事） */
    public void completeApproval(String actor, String requestId, String decision, String comment) {
        Task task = currentTask(requestId, actor);
        if (task == null) throw new ClientException("无待办审批任务或无权处理");
        Map<String, Object> vars = new HashMap<>();
        vars.put("decision", decision);
        if (comment != null) vars.put("comment", comment);
        taskService.complete(task.getId(), vars);
        syncApprovalTasks(requestId);
        log.info("leave-approval task completed request={} actor={} decision={}", requestId, actor, decision);
    }

    /** 撤回：取消流程实例并释放冻结 */
    public void withdraw(String actor, String requestId) {
        LeaveRequest r = db.one(LeaveRequest.class, "SELECT * FROM ent_leave_request WHERE id=?", requestId);
        if (r == null) throw new ClientException("请假申请不存在");
        if (!actor.equals(r.userId())) throw new ClientException("只能撤回本人申请");
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(requestId).singleResult();
        if (instance == null) throw new ClientException("流程实例不存在或已结束");
        if ("ANNUAL".equals(r.leaveType())) {
            jdbc.update("UPDATE ent_leave_account SET frozen=frozen-? WHERE user_id=? AND year=?", r.days(), r.userId(), r.startDate().getYear());
        }
        runtimeService.deleteProcessInstance(instance.getId(), "员工撤回");
        jdbc.update("UPDATE ent_leave_request SET status='WITHDRAWN' WHERE id=?", requestId);
        jdbc.update("UPDATE ent_approval_task SET status='CANCELLED' WHERE request_id=? AND status='PENDING'", requestId);
        log.info("leave-approval withdrawn request={} actor={}", requestId, actor);
    }

    /**
     * 将 Flowable 活动人工任务同步到 ent_approval_task，供企业工作台待办列表使用。
     */
    public void syncApprovalTasks(String requestId) {
        jdbc.update("UPDATE ent_approval_task SET status='CANCELLED' WHERE request_id=? AND status='PENDING'", requestId);
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceBusinessKey(requestId)
                .active()
                .list();
        for (Task task : tasks) {
            String stage = "hr_approval".equals(task.getTaskDefinitionKey()) ? "HR" : "MANAGER";
            if ("HR".equals(stage)) {
                jdbc.update("UPDATE ent_leave_request SET status='PENDING_HR',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING_MANAGER'",
                        requestId);
            }
            jdbc.update(
                    "INSERT INTO ent_approval_task(id,request_id,assignee_id,stage,status) VALUES(?,?,?,?,'PENDING')",
                    java.util.UUID.randomUUID().toString().replace("-", ""),
                    requestId,
                    task.getAssignee(),
                    stage);
        }
    }

    /** 一致性检查：流程状态 vs 业务状态 */
    public boolean consistencyCheck(String requestId) {
        ProcessInstance instance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(requestId).singleResult();
        LeaveRequest r = db.one(LeaveRequest.class, "SELECT * FROM ent_leave_request WHERE id=?", requestId);
        if (r == null) return false;
        boolean flowActive = instance != null && !instance.isEnded();
        boolean businessPending = List.of("PENDING_MANAGER", "PENDING_HR").contains(r.status());
        boolean consistent = (flowActive == businessPending) || (!flowActive && List.of("APPROVED", "REJECTED", "WITHDRAWN").contains(r.status()));
        if (!consistent) {
            log.warn("leave-approval consistency mismatch request={} flowActive={} businessStatus={}", requestId, flowActive, r.status());
        }
        return consistent;
    }

    private Task currentTask(String requestId, String actor) {
        return taskService.createTaskQuery()
                .processInstanceBusinessKey(requestId)
                .taskAssignee(actor)
                .active()
                .singleResult();
    }
}
