package com.myagent.erp.leave;

import com.myagent.erp.identity.ErpIdentity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class LeaveService {
    private static final String CREATE_ENDPOINT = "POST:/api/erp/v1/leave-requests";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public LeaveService(JdbcTemplate jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    public LeaveRequest create(ErpIdentity identity, CreateLeaveCommand command) {
        validate(command);
        return transaction.execute(status -> createInTransaction(identity, command));
    }

    private LeaveRequest createInTransaction(ErpIdentity identity, CreateLeaveCommand command) {
        String hash = requestHash(command);
        List<IdempotencyRecord> existing = jdbc.query("""
                SELECT request_hash,resource_id FROM erp_idempotency
                 WHERE caller_id=? AND endpoint=? AND idempotency_key=?
                """, (rs, row) -> new IdempotencyRecord(rs.getString("request_hash"), rs.getString("resource_id")),
                identity.employeeId(), CREATE_ENDPOINT, command.idempotencyKey());
        if (!existing.isEmpty()) {
            if (!existing.get(0).requestHash().equals(hash)) throw new IllegalArgumentException("幂等键不能用于不同的请假内容");
            return find(identity, existing.get(0).resourceId());
        }

        BigDecimal days = workingDays(command.startDate(), command.endDate());
        int frozen = jdbc.update("""
                UPDATE erp_leave_balance
                   SET frozen_days=frozen_days+?, version=version+1
                 WHERE tenant_id=? AND employee_id=? AND leave_year=?
                   AND total_days-used_days-frozen_days >= ?
                """, days, identity.tenantId(), identity.employeeId(), command.startDate().getYear(), days);
        if (frozen != 1) throw new InsufficientLeaveBalanceException();

        String id = UUID.randomUUID().toString();
        String managerId = jdbc.queryForObject("SELECT manager_id FROM erp_employee WHERE tenant_id=? AND employee_id=?",
                String.class, identity.tenantId(), identity.employeeId());
        if (managerId == null || managerId.isBlank()) throw new IllegalStateException("员工未配置直属主管，不能提交请假申请");
        jdbc.update("""
                INSERT INTO erp_leave_request(id,tenant_id,employee_id,start_date,end_date,leave_days,reason,status,manager_id,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,'PENDING_MANAGER',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, identity.tenantId(), identity.employeeId(), command.startDate(), command.endDate(), days, command.reason(), managerId);
        jdbc.update("INSERT INTO erp_idempotency(caller_id,endpoint,idempotency_key,request_hash,resource_id) VALUES(?,?,?,?,?)",
                identity.employeeId(), CREATE_ENDPOINT, command.idempotencyKey(), hash, id);
        return find(identity, id);
    }

    public void withdraw(ErpIdentity identity, String requestId) {
        transaction.executeWithoutResult(status -> {
            LeaveRequest request = find(identity, requestId);
            if (request.status() != LeaveStatus.PENDING_MANAGER && request.status() != LeaveStatus.PENDING_HR) {
                throw new IllegalStateException("当前请假状态不能撤回");
            }
            int changed = jdbc.update("UPDATE erp_leave_request SET status='WITHDRAWN',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PENDING_MANAGER','PENDING_HR')", requestId);
            if (changed != 1) throw new IllegalStateException("请假状态已变化，请刷新后重试");
            int released = jdbc.update("""
                    UPDATE erp_leave_balance SET frozen_days=frozen_days-?,version=version+1
                     WHERE tenant_id=? AND employee_id=? AND leave_year=? AND frozen_days>=?
                    """, request.leaveDays(), identity.tenantId(), identity.employeeId(), request.startDate().getYear(), request.leaveDays());
            if (released != 1) throw new IllegalStateException("年假冻结余额异常，已拒绝撤回");
        });
    }

    public void managerApprove(ErpIdentity identity, String requestId, boolean approved) {
        transaction.executeWithoutResult(status -> {
            InternalRequest request = internalRequest(identity.tenantId(), requestId);
            if (!identity.employeeId().equals(request.managerId()) || request.status() != LeaveStatus.PENDING_MANAGER) {
                throw new IllegalStateException("无权处理该主管审批任务");
            }
            if (approved) {
                jdbc.update("UPDATE erp_leave_request SET status='PENDING_HR',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING_MANAGER'", requestId);
                audit(identity, "LEAVE_MANAGER_APPROVED", requestId);
            } else {
                releaseAndReject(identity, request, "LEAVE_MANAGER_REJECTED");
            }
        });
    }

    public void hrApprove(ErpIdentity identity, String requestId, boolean approved) {
        transaction.executeWithoutResult(status -> {
            String role = jdbc.queryForObject("SELECT business_role FROM erp_employee WHERE tenant_id=? AND employee_id=?", String.class,
                    identity.tenantId(), identity.employeeId());
            InternalRequest request = internalRequest(identity.tenantId(), requestId);
            if (!"HR".equals(role) || request.status() != LeaveStatus.PENDING_HR) throw new IllegalStateException("无权处理该人事审批任务");
            if (!approved) { releaseAndReject(identity, request, "LEAVE_HR_REJECTED"); return; }
            int settled = jdbc.update("""
                    UPDATE erp_leave_balance SET frozen_days=frozen_days-?,used_days=used_days+?,version=version+1
                     WHERE tenant_id=? AND employee_id=? AND leave_year=? AND frozen_days>=?
                    """, request.leaveDays(), request.leaveDays(), request.tenantId(), request.employeeId(), request.startDate().getYear(), request.leaveDays());
            if (settled != 1) throw new IllegalStateException("年假冻结余额异常，已拒绝结算");
            jdbc.update("UPDATE erp_leave_request SET status='APPROVED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING_HR'", requestId);
            audit(identity, "LEAVE_HR_APPROVED", requestId);
        });
    }

    private void releaseAndReject(ErpIdentity actor, InternalRequest request, String event) {
        int released = jdbc.update("UPDATE erp_leave_balance SET frozen_days=frozen_days-?,version=version+1 WHERE tenant_id=? AND employee_id=? AND leave_year=? AND frozen_days>=?",
                request.leaveDays(), request.tenantId(), request.employeeId(), request.startDate().getYear(), request.leaveDays());
        if (released != 1) throw new IllegalStateException("年假冻结余额异常，已拒绝处理");
        jdbc.update("UPDATE erp_leave_request SET status='REJECTED',updated_at=CURRENT_TIMESTAMP WHERE id=?", request.id());
        audit(actor, event, request.id());
    }

    private InternalRequest internalRequest(String tenantId, String requestId) {
        return jdbc.queryForObject("SELECT id,tenant_id,employee_id,manager_id,start_date,leave_days,status FROM erp_leave_request WHERE id=? AND tenant_id=?",
                (rs, row) -> new InternalRequest(rs.getString("id"), rs.getString("tenant_id"), rs.getString("employee_id"), rs.getString("manager_id"),
                        rs.getObject("start_date", LocalDate.class), rs.getBigDecimal("leave_days"), LeaveStatus.valueOf(rs.getString("status"))), requestId, tenantId);
    }

    private void audit(ErpIdentity identity, String event, String targetId) {
        jdbc.update("INSERT INTO erp_operation_audit(id,tenant_id,actor_id,event,target_id,trace_id,created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), identity.tenantId(), identity.employeeId(), event, targetId, identity.traceId());
    }

    public LeaveRequest find(ErpIdentity identity, String requestId) {
        List<LeaveRequest> rows = jdbc.query("""
                SELECT id,start_date,end_date,leave_days,reason,status FROM erp_leave_request
                 WHERE id=? AND tenant_id=? AND employee_id=?
                """, (rs, row) -> new LeaveRequest(rs.getString("id"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getBigDecimal("leave_days"), rs.getString("reason"),
                LeaveStatus.valueOf(rs.getString("status"))), requestId, identity.tenantId(), identity.employeeId());
        if (rows.isEmpty()) throw new IllegalArgumentException("请假单不存在或无权访问");
        return rows.get(0);
    }

    /** 当前员工的请假单列表；租户与员工范围由认证身份固定。 */
    public List<LeaveRequest> listMine(ErpIdentity identity) {
        return jdbc.query("""
                SELECT id,start_date,end_date,leave_days,reason,status FROM erp_leave_request
                 WHERE tenant_id=? AND employee_id=?
                 ORDER BY created_at DESC,id DESC
                """, (rs, row) -> new LeaveRequest(rs.getString("id"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getBigDecimal("leave_days"), rs.getString("reason"),
                LeaveStatus.valueOf(rs.getString("status"))), identity.tenantId(), identity.employeeId());
    }

    /** 仅返回当前员工可处理的任务摘要；请假原因不应暴露给非申请人。 */
    public List<ApprovalTask> listApprovalTasks(ErpIdentity identity) {
        return jdbc.query("""
                SELECT id,employee_id,start_date,end_date,leave_days,
                       CASE WHEN manager_id=? AND status='PENDING_MANAGER' THEN 'MANAGER' ELSE 'HR' END AS stage
                  FROM erp_leave_request
                 WHERE tenant_id=? AND ((manager_id=? AND status='PENDING_MANAGER')
                    OR (status='PENDING_HR' AND EXISTS (SELECT 1 FROM erp_employee
                        WHERE tenant_id=? AND employee_id=? AND business_role='HR')))
                 ORDER BY created_at ASC,id ASC
                """, (rs, row) -> new ApprovalTask(rs.getString("id"), rs.getString("stage"),
                rs.getString("employee_id"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getBigDecimal("leave_days")),
                identity.employeeId(), identity.tenantId(), identity.employeeId(), identity.tenantId(), identity.employeeId());
    }

    public LeaveBalance balance(ErpIdentity identity, int year) {
        List<LeaveBalance> rows = jdbc.query("""
                SELECT total_days,used_days,frozen_days FROM erp_leave_balance
                 WHERE tenant_id=? AND employee_id=? AND leave_year=?
                """, (rs, row) -> {
                    BigDecimal total = rs.getBigDecimal("total_days");
                    BigDecimal used = rs.getBigDecimal("used_days");
                    BigDecimal frozen = rs.getBigDecimal("frozen_days");
                    return new LeaveBalance(year, total, used, frozen, total.subtract(used).subtract(frozen));
                },
                identity.tenantId(), identity.employeeId(), year);
        if (rows.isEmpty()) throw new IllegalArgumentException("未配置该年度年假账户");
        return rows.get(0);
    }

    private void validate(CreateLeaveCommand command) {
        if (command == null || command.startDate() == null || command.endDate() == null || command.startDate().isAfter(command.endDate())
                || command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("请假日期或幂等键无效");
        }
        if (workingDays(command.startDate(), command.endDate()).signum() == 0) throw new IllegalArgumentException("请假日期不包含工作日");
    }

    private BigDecimal workingDays(LocalDate start, LocalDate end) {
        long count = start.datesUntil(end.plusDays(1)).filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY).count();
        return BigDecimal.valueOf(count).setScale(2);
    }

    private String requestHash(CreateLeaveCommand command) {
        try {
            String text = command.startDate() + "|" + command.endDate() + "|" + (command.reason() == null ? "" : command.reason());
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException("无法生成幂等请求摘要", exception); }
    }

    public record CreateLeaveCommand(LocalDate startDate, LocalDate endDate, String reason, String idempotencyKey) {}
    public record LeaveRequest(String id, LocalDate startDate, LocalDate endDate, BigDecimal leaveDays, String reason, LeaveStatus status) {}
    public record LeaveBalance(int year, BigDecimal totalDays, BigDecimal usedDays, BigDecimal frozenDays,
                               BigDecimal availableDays) {}
    public record ApprovalTask(String requestId, String stage, String employeeId, LocalDate startDate,
                               LocalDate endDate, BigDecimal leaveDays) {}
    private record IdempotencyRecord(String requestHash, String resourceId) {}
    private record InternalRequest(String id, String tenantId, String employeeId, String managerId, LocalDate startDate,
                                   BigDecimal leaveDays, LeaveStatus status) {}
}
