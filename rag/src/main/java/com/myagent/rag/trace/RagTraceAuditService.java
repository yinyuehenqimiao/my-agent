package com.myagent.rag.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.exception.ClientException;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.controller.vo.RagTraceDetailVO;
import com.myagent.rag.controller.vo.RagTraceNodeVO;
import com.myagent.rag.controller.vo.RagTraceRunVO;
import com.myagent.rag.controller.request.RagTraceRunPageRequest;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import com.myagent.rag.dao.entity.RagTraceRunDO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

/**
 * 推理链路审计存储。运行记录持有租户边界，节点通过 trace_id 从属于运行记录。
 */
@Service
@RequiredArgsConstructor
public class RagTraceAuditService {

    private final JdbcTemplate jdbc;
    private final RagTraceProperties properties;

    public void startRun(RagTraceRunDO run) {
        if (run == null || !hasText(run.getTraceId()) || !hasText(run.getTenantId())) {
            throw new IllegalArgumentException("Trace 运行记录缺少 traceId 或 tenantId");
        }
        jdbc.update("""
                        INSERT INTO t_rag_trace_run
                            (id,trace_id,tenant_id,trace_name,entry_method,conversation_id,task_id,user_id,
                             status,error_message,start_time,end_time,duration_ms,extra_data,create_time,update_time,deleted)
                        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)
                        """,
                valueOrId(run.getId()), run.getTraceId(), run.getTenantId(), run.getTraceName(), run.getEntryMethod(),
                run.getConversationId(), run.getTaskId(), run.getUserId(), defaultText(run.getStatus(), "RUNNING"),
                truncate(run.getErrorMessage()), timestamp(run.getStartTime()), timestamp(run.getEndTime()),
                run.getDurationMs(), run.getExtraData());
    }

    public void finishRun(String traceId, String status, String errorMessage, Date endTime, long durationMs) {
        jdbc.update("""
                        UPDATE t_rag_trace_run
                           SET status=?,error_message=?,end_time=?,duration_ms=?,update_time=CURRENT_TIMESTAMP
                         WHERE trace_id=? AND deleted=0
                        """, status, truncate(errorMessage), timestamp(endTime), Math.max(0L, durationMs), traceId);
    }

    public void startNode(RagTraceNodeDO node) {
        if (node == null || !hasText(node.getTraceId()) || !hasText(node.getNodeId())) {
            throw new IllegalArgumentException("Trace 节点缺少 traceId 或 nodeId");
        }
        int inserted = jdbc.update("""
                        INSERT INTO t_rag_trace_node
                            (id,trace_id,node_id,parent_node_id,depth,node_type,node_name,class_name,method_name,
                             status,error_message,start_time,end_time,duration_ms,extra_data,create_time,update_time,deleted)
                        SELECT ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0
                         WHERE EXISTS (SELECT 1 FROM t_rag_trace_run WHERE trace_id=? AND deleted=0)
                        """,
                valueOrId(node.getId()), node.getTraceId(), node.getNodeId(), node.getParentNodeId(),
                node.getDepth() == null ? 0 : node.getDepth(), node.getNodeType(), node.getNodeName(),
                node.getClassName(), node.getMethodName(), defaultText(node.getStatus(), "RUNNING"),
                truncate(node.getErrorMessage()), timestamp(node.getStartTime()), timestamp(node.getEndTime()),
                node.getDurationMs(), node.getExtraData(), node.getTraceId());
        if (inserted != 1) {
            throw new IllegalStateException("Trace 运行记录不存在: " + node.getTraceId());
        }
    }

    public void finishNode(String traceId, String nodeId, String status, String errorMessage,
                           Date endTime, long durationMs) {
        jdbc.update("""
                        UPDATE t_rag_trace_node
                           SET status=?,error_message=?,end_time=?,duration_ms=?,update_time=CURRENT_TIMESTAMP
                         WHERE trace_id=? AND node_id=? AND deleted=0
                        """, status, truncate(errorMessage), timestamp(endTime), Math.max(0L, durationMs), traceId, nodeId);
    }

    public RagTraceDetailVO detail(ExecutionIdentity identity, String traceId) {
        String tenantId = requireTenant(identity);
        List<RagTraceRunVO> runs = jdbc.query("""
                        SELECT trace_id,trace_name,entry_method,conversation_id,task_id,user_id,status,error_message,
                               duration_ms,start_time,end_time,extra_data
                          FROM t_rag_trace_run
                         WHERE trace_id=? AND tenant_id=? AND deleted=0
                        """, (rs, rowNum) -> toRun(rs), traceId, tenantId);
        if (runs.isEmpty()) {
            return null;
        }
        List<RagTraceNodeVO> nodes = jdbc.query("""
                        SELECT n.trace_id,n.node_id,n.parent_node_id,n.depth,n.node_type,n.node_name,n.class_name,
                               n.method_name,n.status,n.error_message,n.duration_ms,n.start_time,n.end_time
                          FROM t_rag_trace_node n
                          JOIN t_rag_trace_run r ON r.trace_id=n.trace_id AND r.deleted=0
                         WHERE n.trace_id=? AND r.tenant_id=? AND n.deleted=0
                         ORDER BY n.start_time,n.id
                        """, (rs, rowNum) -> toNode(rs), traceId, tenantId);
        return RagTraceDetailVO.builder().run(runs.get(0)).nodes(nodes).build();
    }

    public Page<RagTraceRunVO> page(ExecutionIdentity identity, RagTraceRunPageRequest request) {
        String tenantId = requireTenant(identity);
        long current = Math.max(1L, request == null ? 1L : request.getCurrent());
        long size = Math.min(200L, Math.max(1L, request == null ? 20L : request.getSize()));
        StringBuilder where = new StringBuilder(" WHERE tenant_id=? AND deleted=0");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (request != null) {
            appendFilter(where, params, "trace_id", request.getTraceId());
            appendFilter(where, params, "conversation_id", request.getConversationId());
            appendFilter(where, params, "task_id", request.getTaskId());
            appendFilter(where, params, "status", request.getStatus());
            appendFilter(where, params, "user_id", request.getUserId());
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_rag_trace_run" + where,
                Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((current - 1L) * size);
        List<RagTraceRunVO> records = jdbc.query("""
                        SELECT trace_id,trace_name,entry_method,conversation_id,task_id,user_id,status,error_message,
                               duration_ms,start_time,end_time,extra_data
                          FROM t_rag_trace_run
                        """ + where + " ORDER BY start_time DESC,id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> toRun(rs), pageParams.toArray());
        Page<RagTraceRunVO> page = new Page<>(current, size, total == null ? 0L : total);
        page.setRecords(records);
        return page;
    }

    private void appendFilter(StringBuilder where, List<Object> params, String column, String value) {
        if (!hasText(value)) return;
        where.append(" AND ").append(column).append("=?");
        params.add(value);
    }

    private RagTraceRunVO toRun(ResultSet rs) throws SQLException {
        String extraData = rs.getString("extra_data");
        Long ttft = null;
        return RagTraceRunVO.builder()
                .traceId(rs.getString("trace_id")).traceName(rs.getString("trace_name"))
                .entryMethod(rs.getString("entry_method")).conversationId(rs.getString("conversation_id"))
                .taskId(rs.getString("task_id")).userId(rs.getString("user_id"))
                .status(rs.getString("status")).errorMessage(rs.getString("error_message"))
                .durationMs(nullableLong(rs, "duration_ms")).ttftMs(ttft).question(question(extraData))
                .startTime(rs.getTimestamp("start_time")).endTime(rs.getTimestamp("end_time")).build();
    }

    private RagTraceNodeVO toNode(ResultSet rs) throws SQLException {
        return RagTraceNodeVO.builder()
                .traceId(rs.getString("trace_id")).nodeId(rs.getString("node_id"))
                .parentNodeId(rs.getString("parent_node_id")).depth(rs.getInt("depth"))
                .nodeType(rs.getString("node_type")).nodeName(rs.getString("node_name"))
                .className(rs.getString("class_name")).methodName(rs.getString("method_name"))
                .status(rs.getString("status")).errorMessage(rs.getString("error_message"))
                .durationMs(nullableLong(rs, "duration_ms"))
                .startTime(rs.getTimestamp("start_time")).endTime(rs.getTimestamp("end_time")).build();
    }

    private String question(String extraData) {
        if (!hasText(extraData)) return null;
        try {
            return JSONUtil.parseObj(extraData).getStr("question");
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private String requireTenant(ExecutionIdentity identity) {
        if (identity == null || !hasText(identity.getTenantId())) {
            throw new ClientException("缺少租户身份，拒绝查询 Trace 审计");
        }
        return identity.getTenantId();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= properties.getMaxErrorLength()) return value;
        return value.substring(0, properties.getMaxErrorLength());
    }

    private Timestamp timestamp(Date value) {
        return value == null ? null : new Timestamp(value.getTime());
    }

    private String valueOrId(String value) {
        return hasText(value) ? value : IdUtil.getSnowflakeNextIdStr();
    }

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
