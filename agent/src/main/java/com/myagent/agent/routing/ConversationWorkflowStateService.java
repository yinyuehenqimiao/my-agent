package com.myagent.agent.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 持久化会话当前固定工作流，不依赖模型记忆 JSON 判断路由控制状态。 */
@Service
public class ConversationWorkflowStateService {

    private static final String PIPELINE_TYPE = "WORKFLOW";

    private final JdbcTemplate jdbc;
    private final Duration conversationTtl;

    public ConversationWorkflowStateService(
            JdbcTemplate jdbc,
            @Value("${myagent.routing.workflow.conversationTtl:PT30M}") Duration conversationTtl) {
        this.jdbc = jdbc;
        this.conversationTtl = conversationTtl;
    }

    /** 首次进入工作流时建立控制面状态；续接同一工作流时只延长有效期。 */
    @Transactional
    public void activate(String tenantId, String userId, String conversationId, String workflowDefKey) {
        requireScope(tenantId, userId, conversationId);
        if (workflowDefKey == null || workflowDefKey.isBlank()) {
            throw new IllegalArgumentException("工作流目标不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(conversationTtl);
        int updated = jdbc.update("""
                        UPDATE t_agent_route_state
                           SET pipeline_target=?,
                               status=CASE
                                   WHEN pipeline_target=? AND status IN ('COLLECTING_PARAMETERS','AWAITING_CONFIRMATION')
                                   THEN status ELSE 'COLLECTING_PARAMETERS' END,
                               expires_at=?, update_time=?, version=version+1
                         WHERE tenant_id=? AND user_id=? AND conversation_id=?
                        """,
                workflowDefKey, workflowDefKey, expiresAt, now, tenantId, userId, conversationId);
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update("""
                            INSERT INTO t_agent_route_state
                                (tenant_id,user_id,conversation_id,pipeline_type,pipeline_target,status,
                                 version,expires_at,create_time,update_time)
                            VALUES(?,?,?,?,?,'COLLECTING_PARAMETERS',0,?,?,?)
                            """,
                    tenantId, userId, conversationId, PIPELINE_TYPE, workflowDefKey, expiresAt, now, now);
        } catch (DuplicateKeyException concurrentInsert) {
            jdbc.update("""
                            UPDATE t_agent_route_state
                               SET pipeline_target=?, status='COLLECTING_PARAMETERS', expires_at=?,
                                   update_time=?, version=version+1
                             WHERE tenant_id=? AND user_id=? AND conversation_id=?
                            """,
                    workflowDefKey, expiresAt, now, tenantId, userId, conversationId);
        }
    }

    public Optional<WorkflowContinuation> findContinuation(
            String tenantId, String userId, String conversationId) {
        requireScope(tenantId, userId, conversationId);
        List<WorkflowContinuation> states = jdbc.query("""
                        SELECT pipeline_target,status
                          FROM t_agent_route_state
                         WHERE tenant_id=? AND user_id=? AND conversation_id=?
                           AND pipeline_type='WORKFLOW'
                           AND status IN ('COLLECTING_PARAMETERS','AWAITING_CONFIRMATION')
                           AND expires_at>?
                        """,
                (rs, rowNum) -> new WorkflowContinuation(
                        rs.getString("pipeline_target"),
                        WorkflowConversationStatus.valueOf(rs.getString("status"))),
                tenantId, userId, conversationId, LocalDateTime.now());
        return states.stream().findFirst();
    }

    /**
     * 返回仍在会话 TTL 内的最近非活动流程，仅用于理解“能修改吗”等紧邻省略式追问。
     * 活动流程不从这里返回，避免与正常续接语义混淆。
     */
    public Optional<RecentWorkflowContext> findRecentContext(
            String tenantId, String userId, String conversationId) {
        requireScope(tenantId, userId, conversationId);
        List<RecentWorkflowContext> states = jdbc.query("""
                        SELECT pipeline_target,status
                          FROM t_agent_route_state
                         WHERE tenant_id=? AND user_id=? AND conversation_id=?
                           AND pipeline_type='WORKFLOW'
                           AND status NOT IN ('COLLECTING_PARAMETERS','AWAITING_CONFIRMATION')
                           AND expires_at>?
                        """,
                (rs, rowNum) -> new RecentWorkflowContext(
                        rs.getString("pipeline_target"),
                        WorkflowConversationStatus.valueOf(rs.getString("status"))),
                tenantId, userId, conversationId, LocalDateTime.now());
        return states.stream().findFirst();
    }

    public void complete(String tenantId, String userId, String conversationId) {
        requireScope(tenantId, userId, conversationId);
        jdbc.update("""
                        UPDATE t_agent_route_state
                           SET status='COMPLETED', update_time=?, version=version+1
                         WHERE tenant_id=? AND user_id=? AND conversation_id=?
                        """,
                LocalDateTime.now(), tenantId, userId, conversationId);
    }

    public void markAwaitingConfirmation(String tenantId, String userId, String conversationId) {
        requireScope(tenantId, userId, conversationId);
        jdbc.update("""
                        UPDATE t_agent_route_state
                           SET status='AWAITING_CONFIRMATION', update_time=?, version=version+1
                         WHERE tenant_id=? AND user_id=? AND conversation_id=?
                           AND status='COLLECTING_PARAMETERS'
                        """,
                LocalDateTime.now(), tenantId, userId, conversationId);
    }

    private void requireScope(String tenantId, String userId, String conversationId) {
        if (tenantId == null || tenantId.isBlank()
                || userId == null || userId.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("会话工作流状态缺少租户、用户或会话标识");
        }
    }
}
