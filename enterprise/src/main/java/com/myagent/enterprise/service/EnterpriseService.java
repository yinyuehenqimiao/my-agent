package com.myagent.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.flowable.LeaveApprovalFlowService;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/** Two-stage leave workflow. Every public mutation is transactional and rechecks authority. */
@Service
@RequiredArgsConstructor
public class EnterpriseService {
    private final EnterpriseMapper db;
    private final ObjectMapper json;
    private final Clock enterpriseClock;
    /** Flowable 引擎启用时注入；未启用则为空，走 legacy。 */
    private final ObjectProvider<LeaveApprovalFlowService> leaveFlow;

    public Employee profile(String actor) {
        Employee e = db.employee(actor, false);
        require(e != null && e.active(), "员工档案不存在或已停用");
        return e;
    }
    public Account balance(String actor, int year) {
        profile(actor);
        require(year >= 2000 && year <= 2100, "年份超出支持范围");
        Account account = db.one(Account.class, "SELECT * FROM ent_leave_account WHERE user_id=? AND year=?", actor, year);
        return account == null ? new Account(actor, year, 10, 0, 0) : account;
    }
    public List<LeaveRequest> requests(String actor) {
        profile(actor);
        return db.list(LeaveRequest.class, "SELECT * FROM ent_leave_request WHERE user_id=? ORDER BY created_at DESC LIMIT 200", actor);
    }
    public List<ApprovalTask> tasks(String actor) {
        profile(actor);
        return db.list(ApprovalTask.class, "SELECT * FROM ent_approval_task WHERE assignee_id=? AND status='PENDING' ORDER BY id LIMIT 200", actor);
    }
    public Detail detail(String actor, String id) {
        Employee employee = profile(actor);
        LeaveRequest r = requiredRequest(id);
        boolean owner = actor.equals(r.userId());
        boolean assigned = db.exists("SELECT id FROM ent_approval_task WHERE request_id=? AND assignee_id=?", id, actor);
        require(owner || (assigned && (employee.businessRole().equals("MANAGER") || employee.businessRole().equals("HR"))), "无权查看此申请");
        return new Detail(r, db.list(ApprovalTask.class, "SELECT * FROM ent_approval_task WHERE request_id=? ORDER BY stage DESC", id),
                db.list(Audit.class, "SELECT * FROM ent_audit WHERE request_id=? ORDER BY created_at,id", id));
    }
    public Action getAction(String actor, String id) {
        profile(actor);
        Action a = db.action(id, false);
        require(a != null && actor.equals(a.userId()), "操作不存在或无权访问");
        return a;
    }
    public List<Action> actions(String actor, String conversationId) {
        profile(actor);
        return db.list(Action.class, "SELECT * FROM ent_action WHERE user_id=? AND conversation_id=? ORDER BY created_at DESC LIMIT 100", actor, conversationId);
    }

    @Transactional
    public Action prepareLeave(String actor, LeaveInput input) {
        Employee employee = profile(actor);
        db.employee(actor, true);
        Action previous = previous(actor, input.conversationId(), input.requestKey());
        if (previous != null) return previous;
        require(Set.of("ANNUAL", "PERSONAL").contains(input.leaveType()), "不支持的假期类型");
        require(input.reason() != null && !input.reason().isBlank() && input.reason().length() <= 1000, "请填写不超过 1000 字的原因");
        validateApprovers(employee);
        int days = workingDays(input.startDate(), input.endDate());
        LeaveRequest request = new LeaveRequest(id(), actor, input.leaveType(), input.startDate(), input.endDate(), days,
                input.reason(), "DRAFT", employee.managerId(), employee.hrId(), now(), now());
        validateSubmission(request);
        db.update("INSERT INTO ent_leave_request(id,user_id,leave_type,start_date,end_date,days,reason,status,manager_id,hr_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                request.id(), actor, request.leaveType(), request.startDate(), request.endDate(), days, request.reason(), request.status(), request.managerId(), request.hrId(), now(), now());
        audit(request.id(), actor, null, "DRAFT", "生成申请确认卡");
        return createAction(actor, input.conversationId(), input.requestKey(), "SUBMIT", request, Map.of("summary", request));
    }

    @Transactional
    public Action prepareDecision(String actor, String requestId, DecisionInput input) {
        profile(actor);
        require(Set.of("APPROVE", "REJECT").contains(input.decision()), "请选择同意或拒绝");
        require(input.comment() == null || input.comment().length() <= 1000, "审批意见过长");
        if (input.decision().equals("REJECT")) require(input.comment() != null && !input.comment().isBlank(), "拒绝时请填写原因");
        LeaveRequest snapshot = requiredRequest(requestId);
        db.employee(snapshot.userId(), true);
        Action previous = previous(actor, input.conversationId(), input.requestKey());
        if (previous != null) return previous;
        LeaveRequest r = db.request(requestId, true);
        authorizeApproval(actor, r);
        return createAction(actor, input.conversationId(), input.requestKey(), input.decision(), r,
                Map.of("summary", r, "expectedStatus", r.status(), "comment", input.comment() == null ? "" : input.comment()));
    }

    @Transactional
    public Action prepareWithdrawal(String actor, String requestId, WithdrawInput input) {
        profile(actor);
        db.employee(actor, true);
        Action previous = previous(actor, input.conversationId(), input.requestKey());
        if (previous != null) return previous;
        LeaveRequest r = db.request(requestId, true);
        require(r != null && actor.equals(r.userId()) && pending(r), "只能撤回本人审批中的申请");
        return createAction(actor, input.conversationId(), input.requestKey(), "WITHDRAW", r, Map.of("summary", r));
    }

    /** Locks action then applicant then request. All business writers use the applicant lock. */
    @Transactional
    public Action confirm(String actor, String actionId) {
        profile(actor);
        Action a = db.action(actionId, true);
        require(a != null && actor.equals(a.userId()), "操作不存在或无权访问");
        if (a.status().equals("DONE")) return a;
        require(a.status().equals("PENDING"), "该操作已取消");
        require(now().isBefore(a.expiresAt()), "确认已过期，请重新生成");
        LeaveRequest snapshot = requiredRequest(a.requestId());
        db.employee(snapshot.userId(), true);
        LeaveRequest r = db.request(a.requestId(), true);
        String next;
        Map<?, ?> payload = decode(a.payload());
        LeaveApprovalFlowService flow = leaveFlow.getIfAvailable();
        if (flow != null) {
            next = confirmWithFlowable(flow, actor, a, r, payload);
        } else {
            next = confirmLegacy(actor, a, r, payload);
        }
        db.update("UPDATE ent_leave_request SET status=?,updated_at=? WHERE id=?", next, now(), r.id());
        audit(r.id(), actor, a.id(), next, Objects.toString(payload.get("comment"), ""));
        db.update("UPDATE ent_action SET status='DONE',result=? WHERE id=?", encode(Map.of("requestId", r.id(), "status", next, "traceId", a.id())), a.id());
        return db.action(a.id(), false);
    }

    /** Flowable 路径：提交启动流程；审批完成 Flowable 任务；撤回取消实例。 */
    private String confirmWithFlowable(LeaveApprovalFlowService flow, String actor, Action a, LeaveRequest r, Map<?, ?> payload) {
        return switch (a.kind()) {
            case "SUBMIT" -> {
                require(actor.equals(r.userId()) && r.status().equals("DRAFT"), "申请状态已变化");
                Employee employee = profile(actor);
                validateApprovers(employee);
                require(Objects.equals(employee.managerId(), r.managerId()) && Objects.equals(employee.hrId(), r.hrId()), "审批关系已变化，请重新生成申请");
                validateSubmission(r);
                flow.start(r.id(), actor, a.id());
                LeaveRequest after = db.request(r.id(), false);
                yield after != null ? after.status() : "PENDING_MANAGER";
            }
            case "APPROVE", "REJECT" -> {
                authorizeApproval(actor, r);
                require(r.status().equals(payload.get("expectedStatus")), "审批节点已变化，请刷新");
                flow.completeApproval(actor, r.id(), a.kind(), Objects.toString(payload.get("comment"), ""));
                LeaveRequest after = db.request(r.id(), false);
                yield after != null ? after.status() : a.kind().equals("REJECT") ? "REJECTED" : r.status();
            }
            case "WITHDRAW" -> {
                require(actor.equals(r.userId()) && pending(r), "只能撤回本人审批中的申请");
                flow.withdraw(actor, r.id());
                yield "WITHDRAWN";
            }
            default -> throw new ClientException("不支持的操作");
        };
    }

    private String confirmLegacy(String actor, Action a, LeaveRequest r, Map<?, ?> payload) {
        return switch (a.kind()) {
            case "SUBMIT" -> {
                require(actor.equals(r.userId()) && r.status().equals("DRAFT"), "申请状态已变化");
                Employee employee = profile(actor);
                validateApprovers(employee);
                require(Objects.equals(employee.managerId(), r.managerId()) && Objects.equals(employee.hrId(), r.hrId()), "审批关系已变化，请重新生成申请");
                validateSubmission(r);
                if (r.leaveType().equals("ANNUAL")) db.update("UPDATE ent_leave_account SET frozen=frozen+? WHERE user_id=? AND year=?", r.days(), actor, r.startDate().getYear());
                task(r, r.managerId(), "MANAGER");
                yield "PENDING_MANAGER";
            }
            case "APPROVE", "REJECT" -> {
                authorizeApproval(actor, r);
                require(r.status().equals(payload.get("expectedStatus")), "审批节点已变化，请刷新");
                db.update("UPDATE ent_approval_task SET status=? WHERE request_id=? AND assignee_id=? AND status='PENDING'", a.kind().equals("APPROVE") ? "APPROVED" : "REJECTED", r.id(), actor);
                if (a.kind().equals("REJECT")) {
                    release(r, false);
                    yield "REJECTED";
                } else if (r.status().equals("PENDING_MANAGER")) {
                    require(profile(r.hrId()).businessRole().equals("HR"), "人事审批人已失效");
                    task(r, r.hrId(), "HR");
                    yield "PENDING_HR";
                } else {
                    release(r, true);
                    yield "APPROVED";
                }
            }
            case "WITHDRAW" -> {
                require(actor.equals(r.userId()) && pending(r), "只能撤回本人审批中的申请");
                db.update("UPDATE ent_approval_task SET status='CANCELLED' WHERE request_id=? AND status='PENDING'", r.id());
                release(r, false);
                yield "WITHDRAWN";
            }
            default -> throw new ClientException("不支持的操作");
        };
    }

    @Transactional
    public Action cancel(String actor, String actionId) {
        profile(actor);
        Action a = db.action(actionId, true);
        require(a != null && actor.equals(a.userId()), "操作不存在或无权访问");
        if (!a.status().equals("PENDING")) return a;
        db.update("UPDATE ent_action SET status='CANCELLED' WHERE id=?", a.id());
        audit(a.requestId(), actor, a.id(), "ACTION_CANCELLED", "取消确认");
        return db.action(a.id(), false);
    }
    private void validateSubmission(LeaveRequest r) {
        require(!r.startDate().isBefore(LocalDate.now(enterpriseClock)), "不能申请过去的日期");
        require(workingDays(r.startDate(), r.endDate()) == r.days(), "工作日历已变更，请重新生成申请");
        require(!db.exists("SELECT id FROM ent_leave_request WHERE user_id=? AND id<>? AND status IN ('PENDING_MANAGER','PENDING_HR','APPROVED') AND start_date<=? AND end_date>=?", r.userId(), r.id(), r.endDate(), r.startDate()), "与已有申请日期重叠");
        if (r.leaveType().equals("ANNUAL")) require(account(r.userId(), r.startDate().getYear()).getAvailable() >= r.days(), "年假余额不足");
    }
    private void validateApprovers(Employee e) {
        require(e.managerId() != null && e.hrId() != null, "请先配置直属主管和人事审批人");
        require(!e.userId().equals(e.managerId()) && !e.userId().equals(e.hrId()) && !e.managerId().equals(e.hrId()), "禁止自审或同一人两级审批");
        require(profile(e.managerId()).businessRole().equals("MANAGER") && profile(e.hrId()).businessRole().equals("HR"), "审批人角色配置错误");
    }
    private void authorizeApproval(String actor, LeaveRequest r) {
        Employee e = profile(actor);
        require(!actor.equals(r.userId()), "不能审批自己的申请");
        boolean manager = r.status().equals("PENDING_MANAGER") && actor.equals(r.managerId()) && e.businessRole().equals("MANAGER");
        boolean hr = r.status().equals("PENDING_HR") && actor.equals(r.hrId()) && e.businessRole().equals("HR");
        require((manager || hr) && db.exists("SELECT id FROM ent_approval_task WHERE request_id=? AND assignee_id=? AND status='PENDING'", r.id(), actor), "无权处理当前审批节点");
    }
    private Account account(String user, int year) {
        Account a = db.one(Account.class, "SELECT * FROM ent_leave_account WHERE user_id=? AND year=?", user, year);
        if (a == null) {
            db.update("INSERT INTO ent_leave_account(user_id,year,total,frozen,used) VALUES(?,?,10,0,0)", user, year);
            a = new Account(user, year, 10, 0, 0);
        }
        return a;
    }
    private int workingDays(LocalDate start, LocalDate end) {
        record Workday(LocalDate day, boolean working) { }
        Map<LocalDate, Boolean> overrides = new HashMap<>();
        db.list(Workday.class, "SELECT day,working FROM ent_workday WHERE day>=? AND day<=?", start, end).forEach(d -> overrides.put(d.day(), d.working()));
        return LeaveRules.days(start, end, overrides);
    }
    private void release(LeaveRequest r, boolean consume) {
        if (r.leaveType().equals("ANNUAL")) db.update("UPDATE ent_leave_account SET frozen=frozen-?,used=used+? WHERE user_id=? AND year=?", r.days(), consume ? r.days() : 0, r.userId(), r.startDate().getYear());
    }
    private void task(LeaveRequest r, String assignee, String stage) {
        db.update("INSERT INTO ent_approval_task(id,request_id,assignee_id,stage,status) VALUES(?,?,?,?,'PENDING')", id(), r.id(), assignee, stage);
    }
    private Action previous(String actor, String session, String key) {
        require(session != null && !session.isBlank() && session.length() <= 64 && key != null && !key.isBlank() && key.length() <= 64, "缺少有效的会话或请求标识");
        return db.one(Action.class, "SELECT * FROM ent_action WHERE user_id=? AND conversation_id=? AND dedup_key=?", actor, session, key);
    }
    private Action createAction(String actor, String session, String key, String kind, LeaveRequest r, Map<String, ?> payload) {
        String actionId = id();
        db.update("INSERT INTO ent_action(id,user_id,conversation_id,kind,request_id,payload,status,expires_at,created_at,dedup_key) VALUES(?,?,?,?,?,?,'PENDING',?,?,?)",
                actionId, actor, session, kind, r.id(), encode(payload), now().plusMinutes(15), now(), key);
        return db.action(actionId, false);
    }
    private void audit(String request, String actor, String action, String event, String comment) {
        db.update("INSERT INTO ent_audit(id,request_id,actor_id,action_id,event,comment,created_at) VALUES(?,?,?,?,?,?,?)", id(), request, actor, action, event, comment, now());
    }
    private LeaveRequest requiredRequest(String id) { LeaveRequest r = db.request(id, false); require(r != null, "申请不存在"); return r; }
    private static boolean pending(LeaveRequest r) { return r.status().equals("PENDING_MANAGER") || r.status().equals("PENDING_HR"); }
    private static void require(boolean valid, String message) { if (!valid) throw new ClientException(message); }
    private LocalDateTime now() { return LocalDateTime.now(enterpriseClock); }
    private static String id() { return UUID.randomUUID().toString(); }
    private String encode(Object o) { try { return json.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException("无法保存操作", e); } }
    private Map<?, ?> decode(String value) { try { return json.readValue(value, Map.class); } catch (Exception e) { throw new IllegalStateException("操作数据损坏", e); } }
}
