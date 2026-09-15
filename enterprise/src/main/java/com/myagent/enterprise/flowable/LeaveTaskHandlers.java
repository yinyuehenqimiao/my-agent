package com.myagent.enterprise.flowable;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.Account;
import com.myagent.enterprise.dto.EnterpriseTypes.Employee;
import com.myagent.enterprise.dto.EnterpriseTypes.LeaveRequest;
import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.handler.WorkflowTaskHandler;
import com.myagent.workflow.handler.WorkflowTaskHandlerRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * 请假流程服务任务处理器：注册到 WorkflowTaskHandler SPI，按节点 id 分发。
 * <p>
 * 业务事实（ent_leave_request/account/audit）经 EnterpriseMapper 直接读写，复用与 legacy 一致的规则。
 * Flowable 负责流程状态与人工任务；模型不参与权限判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "myagent.enterprise.leave.engine", havingValue = "flowable")
public class LeaveTaskHandlers {

    private final WorkflowTaskHandlerRegistry registry;
    private final EnterpriseMapper db;

    @PostConstruct
    void register() {
        registry.register("validate_identity", this::validateIdentity);
        registry.register("validate_dates", this::validateDates);
        registry.register("check_balance", this::checkBalance);
        registry.register("freeze_balance", this::freezeBalance);
        registry.register("release_reject", this::releaseReject);
        registry.register("settle_balance", this::settleBalance);
        registry.register("audit", this::audit);
    }

    private void validateIdentity(DelegateExecution ex) {
        String actor = actor(ex);
        Employee e = db.employee(actor, false);
        if (e == null || !e.active()) throw new ClientException("员工档案不存在或已停用");
        require(e.managerId() != null && e.hrId() != null, "请先配置直属主管和人事审批人");
        require(!e.userId().equals(e.managerId()) && !e.userId().equals(e.hrId()) && !e.managerId().equals(e.hrId()), "禁止自审或同一人两级审批");
        Employee manager = db.employee(e.managerId(), false);
        Employee hr = db.employee(e.hrId(), false);
        require(manager != null && "MANAGER".equals(manager.businessRole()), "主管角色配置错误");
        require(hr != null && "HR".equals(hr.businessRole()), "人事角色配置错误");
        ex.setVariable("managerId", e.managerId());
        ex.setVariable("hrId", e.hrId());
        ex.setVariable("department", e.department());
    }

    private void validateDates(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        int working = workingDays(r.startDate(), r.endDate());
        require(working == r.days(), "工作日历已变更，请重新生成申请");
        require(!r.startDate().isBefore(LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))), "不能申请过去的日期");
        boolean conflict = db.exists("SELECT id FROM ent_leave_request WHERE user_id=? AND id<>? AND status IN ('PENDING_MANAGER','PENDING_HR','APPROVED') AND start_date<=? AND end_date>=?",
                r.userId(), r.id(), r.endDate(), r.startDate());
        require(!conflict, "与已有申请日期重叠");
    }

    private void checkBalance(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        if (!"ANNUAL".equals(r.leaveType())) return;
        Account a = account(r.userId(), r.startDate().getYear());
        require(a.getAvailable() >= r.days(), "年假余额不足");
    }

    private void freezeBalance(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        if (!"ANNUAL".equals(r.leaveType())) return;
        db.update("UPDATE ent_leave_account SET frozen=frozen+? WHERE user_id=? AND year=?", r.days(), r.userId(), r.startDate().getYear());
        db.update("UPDATE ent_leave_request SET status='PENDING_MANAGER' WHERE id=?", r.id());
    }

    private void releaseReject(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        release(r);
        db.update("UPDATE ent_leave_request SET status='REJECTED' WHERE id=?", r.id());
        db.update("UPDATE ent_approval_task SET status='REJECTED' WHERE request_id=? AND status='PENDING'", r.id());
    }

    private void settleBalance(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        if ("ANNUAL".equals(r.leaveType())) {
            db.update("UPDATE ent_leave_account SET frozen=frozen-?, used=used+? WHERE user_id=? AND year=?",
                    r.days(), r.days(), r.userId(), r.startDate().getYear());
        }
        db.update("UPDATE ent_leave_request SET status='APPROVED' WHERE id=?", r.id());
        db.update("UPDATE ent_approval_task SET status='APPROVED' WHERE request_id=? AND status='PENDING'", r.id());
    }

    private void audit(DelegateExecution ex) {
        LeaveRequest r = request(ex);
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        db.update(
                "INSERT INTO ent_audit(id,request_id,actor_id,action_id,event,comment,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                id, r.id(), actor(ex), null, "APPROVED", "Flowable 审批通过");
    }

    // ===== 辅助 =====

    private String actor(DelegateExecution ex) {
        Object a = ex.getVariable("initiatorId");
        if (a == null) throw new ClientException("缺少发起人身份");
        return a.toString();
    }

    private LeaveRequest request(DelegateExecution ex) {
        String requestId = (String) ex.getVariable("requestId");
        if (requestId == null) requestId = ex.getProcessInstanceBusinessKey();
        // 从流程变量构建，避免在 Flowable 事务内重查业务表（事务可见性问题）
        return new LeaveRequest(
                requestId,
                (String) ex.getVariable("userId"),
                (String) ex.getVariable("leaveType"),
                LocalDate.parse((String) ex.getVariable("startDate")),
                LocalDate.parse((String) ex.getVariable("endDate")),
                (Integer) ex.getVariable("days"),
                "", // reason 不在流程变量中传递（敏感数据不进流程变量）
                "", // status 由业务表维护
                (String) ex.getVariable("managerId"),
                (String) ex.getVariable("hrId"),
                null, null);
    }

    private Account account(String user, int year) {
        Account a = db.one(Account.class, "SELECT * FROM ent_leave_account WHERE user_id=? AND year=?", user, year);
        if (a == null) {
            db.update("INSERT INTO ent_leave_account(user_id,year,total,frozen,used) VALUES(?,?,10,0,0)", user, year);
            a = new Account(user, year, 10, 0, 0);
        }
        return a;
    }

    private void release(LeaveRequest r) {
        if ("ANNUAL".equals(r.leaveType())) {
            db.update("UPDATE ent_leave_account SET frozen=frozen-? WHERE user_id=? AND year=?", r.days(), r.userId(), r.startDate().getYear());
        }
    }

    private int workingDays(LocalDate start, LocalDate end) {
        record Workday(LocalDate day, boolean working) {
        }
        var rows = db.list(Workday.class, "SELECT day,working FROM ent_workday WHERE day>=? AND day<=?", start, end);
        java.util.Map<LocalDate, Boolean> overrides = new java.util.HashMap<>();
        for (Workday w : rows) overrides.put(w.day(), w.working());
        int count = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Boolean override = overrides.get(d);
            boolean working = override != null ? override : (d.getDayOfWeek().getValue() < 6);
            if (working) count++;
        }
        return count;
    }

    private void require(boolean cond, String msg) {
        if (!cond) throw new ClientException(msg);
    }
}
