package com.myagent.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.ErpPendingAction;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.enterprise.EnterpriseSystemPort;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.myagent.framework.enterprise.LeaveRequestResult;
import com.myagent.framework.exception.ClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部 ERP 的本地确认协调器。
 * <p>本表不镜像 ERP 业务单据；它仅保证确认卡的归属、过期和幂等上下文。ERP 写入只会在
 * {@link #confirm(ExecutionIdentity, String)} 中发生，且调用端始终使用服务端签发身份。</p>
 */
@Service
@RequiredArgsConstructor
public class ExternalErpActionService {
    private static final String CREATE_LEAVE = "CREATE_LEAVE";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock enterpriseClock;
    private final EnterpriseSystemPort enterpriseSystem;

    public ErpPendingAction prepareCreateLeave(ExecutionIdentity identity, String conversationId,
                                               String idempotencyKey, LeaveRequestCommand command) {
        Owner owner = owner(identity);
        requireText(conversationId, "缺少有效会话标识");
        requireText(idempotencyKey, "缺少有效请求标识");
        require(command != null, "缺少请假内容");
        ErpPendingAction previous = one("SELECT * FROM ent_erp_pending_action WHERE tenant_id=? AND user_id=? AND conversation_id=? AND idempotency_key=?",
                owner.tenantId(), owner.userId(), conversationId, idempotencyKey);
        if (previous != null) return previous;
        CreateLeavePayload payload = new CreateLeavePayload(command.getLeaveType(), command.getStartDate(), command.getEndDate(), command.getReason());
        requireText(payload.leaveType(), "缺少请假类型");
        requireText(payload.startDate(), "缺少开始日期");
        requireText(payload.endDate(), "缺少结束日期");
        requireText(payload.reason(), "缺少请假原因");
        LocalDateTime now = now();
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ent_erp_pending_action(id,tenant_id,user_id,conversation_id,action_type,payload,idempotency_key,trace_id,status,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id, owner.tenantId(), owner.userId(), conversationId, CREATE_LEAVE, encode(payload), idempotencyKey,
                traceId(command), "PENDING", Timestamp.valueOf(now.plusMinutes(15)), Timestamp.valueOf(now), Timestamp.valueOf(now));
        return get(identity, id);
    }

    public ErpPendingAction get(ExecutionIdentity identity, String actionId) {
        Owner owner = owner(identity);
        ErpPendingAction action = one("SELECT * FROM ent_erp_pending_action WHERE id=? AND tenant_id=? AND user_id=?", actionId, owner.tenantId(), owner.userId());
        require(action != null, "确认操作不存在或无权访问");
        return action;
    }

    public List<ErpPendingAction> actions(ExecutionIdentity identity, String conversationId) {
        Owner owner = owner(identity);
        requireText(conversationId, "缺少有效会话标识");
        return jdbc.query("SELECT * FROM ent_erp_pending_action WHERE tenant_id=? AND user_id=? AND conversation_id=? ORDER BY created_at DESC LIMIT 100",
                (rs, rowNum) -> map(rs), owner.tenantId(), owner.userId(), conversationId);
    }

    @Transactional(noRollbackFor = ClientException.class)
    public ErpPendingAction confirm(ExecutionIdentity identity, String actionId) {
        Owner owner = owner(identity);
        ErpPendingAction action = one("SELECT * FROM ent_erp_pending_action WHERE id=? AND tenant_id=? AND user_id=? FOR UPDATE", actionId, owner.tenantId(), owner.userId());
        require(action != null, "确认操作不存在或无权访问");
        if ("DONE".equals(action.status())) return action;
        require("PENDING".equals(action.status()), "该确认操作已取消或过期");
        if (!now().isBefore(action.expiresAt())) {
            jdbc.update("UPDATE ent_erp_pending_action SET status='EXPIRED',updated_at=? WHERE id=?", Timestamp.valueOf(now()), action.id());
            throw new ClientException("确认已过期，请重新生成");
        }
        CreateLeavePayload payload = decode(action.payload());
        LeaveRequestResult result = switch (action.actionType()) {
            case CREATE_LEAVE -> enterpriseSystem.createLeaveRequest(identity, LeaveRequestCommand.builder()
                    .leaveType(payload.leaveType()).startDate(payload.startDate()).endDate(payload.endDate()).reason(payload.reason())
                    .idempotencyKey(idempotencyKey(action.id())).traceId(traceIdFrom(action)).conversationId(conversationId(action.id())).build());
            default -> throw new ClientException("不支持的 ERP 确认操作");
        };
        String resultText = encode(Map.of("requestId", result.requestId(), "status", result.status(), "traceId", result.traceId()));
        jdbc.update("UPDATE ent_erp_pending_action SET status='DONE',result=?,updated_at=? WHERE id=?", resultText, Timestamp.valueOf(now()), action.id());
        return get(identity, action.id());
    }

    @Transactional(noRollbackFor = ClientException.class)
    public ErpPendingAction cancel(ExecutionIdentity identity, String actionId) {
        ErpPendingAction action = get(identity, actionId);
        if ("PENDING".equals(action.status())) {
            jdbc.update("UPDATE ent_erp_pending_action SET status='CANCELLED',updated_at=? WHERE id=?", Timestamp.valueOf(now()), action.id());
        }
        return get(identity, actionId);
    }

    private ErpPendingAction one(String sql, Object... arguments) {
        List<ErpPendingAction> rows = jdbc.query(sql, (rs, rowNum) -> map(rs), arguments);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private ErpPendingAction map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new ErpPendingAction(rs.getString("id"), rs.getString("action_type"), rs.getString("payload"), rs.getString("status"),
                rs.getString("result"), expiresAt == null ? null : expiresAt.toLocalDateTime(), createdAt == null ? null : createdAt.toLocalDateTime());
    }

    private CreateLeavePayload decode(String raw) {
        try { return json.readValue(raw, CreateLeavePayload.class); }
        catch (Exception exception) { throw new ClientException("确认内容已损坏，请重新生成"); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法保存确认操作", exception); }
    }
    private Owner owner(ExecutionIdentity identity) {
        require(identity != null, "缺少已认证的执行身份");
        requireText(identity.getTenantId(), "缺少有效租户身份");
        requireText(identity.getUserId(), "缺少已认证的执行身份");
        return new Owner(identity.getTenantId(), identity.getUserId());
    }
    private String traceId(LeaveRequestCommand command) { return command.getTraceId() == null || command.getTraceId().isBlank() ? UUID.randomUUID().toString() : command.getTraceId(); }
    private String idempotencyKey(String actionId) { return jdbc.queryForObject("SELECT idempotency_key FROM ent_erp_pending_action WHERE id=?", String.class, actionId); }
    private String traceIdFrom(ErpPendingAction action) { return jdbc.queryForObject("SELECT trace_id FROM ent_erp_pending_action WHERE id=?", String.class, action.id()); }
    private String conversationId(String actionId) { return jdbc.queryForObject("SELECT conversation_id FROM ent_erp_pending_action WHERE id=?", String.class, actionId); }
    private LocalDateTime now() { return LocalDateTime.now(enterpriseClock); }
    private static void require(boolean condition, String message) { if (!condition) throw new ClientException(message); }
    private static void requireText(String text, String message) { require(text != null && !text.isBlank() && text.length() <= 128, message); }

    private record Owner(String tenantId, String userId) { }
    private record CreateLeavePayload(String leaveType, String startDate, String endDate, String reason) { }
}
