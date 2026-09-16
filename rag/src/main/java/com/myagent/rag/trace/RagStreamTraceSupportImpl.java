package com.myagent.rag.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.myagent.framework.trace.RagStreamTraceSupport;
import com.myagent.framework.trace.RagTraceContext;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/** 跨线程流式节点审计：调用线程建立父子关系，回调线程幂等结束节点。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagStreamTraceSupportImpl implements RagStreamTraceSupport {

    private final RagTraceProperties properties;
    private final RagTraceAuditService auditService;

    @Override
    public StreamSpan beginStreamNode(String name, String type) {
        String traceId = RagTraceContext.getTraceId();
        if (!properties.isEnabled() || !hasText(traceId)) {
            return NOOP_SPAN;
        }
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        long started = System.currentTimeMillis();
        try {
            auditService.startNode(RagTraceNodeDO.builder()
                    .id(IdUtil.getSnowflakeNextIdStr()).traceId(traceId).nodeId(nodeId)
                    .parentNodeId(RagTraceContext.currentNodeId()).depth(RagTraceContext.depth())
                    .nodeType(StrUtil.blankToDefault(type, "STREAM")).nodeName(name)
                    .status("RUNNING").startTime(new Date(started)).build());
        } catch (Exception error) {
            log.warn("流式节点 Trace 启动失败，继续模型调用, traceId: {}, node: {}", traceId, nodeId, error);
            return NOOP_SPAN;
        }
        RagTraceContext.pushNode(nodeId);
        return new Span(traceId, nodeId, started, name, type);
    }

    private final class Span implements StreamSpan {
        private final String traceId;
        private final String nodeId;
        private final long started;
        private final String nodeName;
        private final String nodeType;
        private final AtomicBoolean detached = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();

        private Span(String traceId, String nodeId, long started, String nodeName, String nodeType) {
            this.traceId = traceId;
            this.nodeId = nodeId;
            this.started = started;
            this.nodeName = nodeName;
            this.nodeType = nodeType;
        }

        @Override
        public void detach() {
            if (detached.compareAndSet(false, true) && nodeId.equals(RagTraceContext.currentNodeId())) {
                RagTraceContext.popNode();
            }
        }

        @Override
        public void finishSuccess(String resultSummary) {
            finish("SUCCESS", null, resultSummary);
        }

        @Override
        public void finishError(Throwable error) {
            finish("ERROR", errorText(error), null);
        }

        @Override
        public void finishCancelledIfRunning() {
            finish("CANCELLED", null, null);
        }

        private void finish(String status, String error, String resultSummary) {
            if (!finished.compareAndSet(false, true)) return;
            try {
                String summary = hasText(resultSummary) ? resultSummary : defaultSummary(status, error);
                String extra = TraceResultSummarizer.toExtraData(summary, error, properties.getMaxExtraDataLength(), nodeType);
                auditService.finishNode(traceId, nodeId, status, error, new Date(),
                        System.currentTimeMillis() - started, extra);
            } catch (Exception auditError) {
                log.warn("流式节点 Trace 收尾失败, traceId: {}, node: {}", traceId, nodeId, auditError);
            }
        }

        private String defaultSummary(String status, String error) {
            String type = StrUtil.blankToDefault(nodeType, "").toUpperCase();
            String name = StrUtil.blankToDefault(nodeName, "流式节点");
            return switch (status) {
                case "SUCCESS" -> switch (type) {
                    case "WORKFLOW" -> "固定流程执行完成：" + stripPrefix(name, "workflow-agent:");
                    case "REACT" -> "开放式智能体执行完成：" + stripPrefix(name, "react-agent:");
                    case "LLM_PROVIDER" -> "模型供应商流式输出完成：" + name;
                    case "LLM_ROUTING" -> "LLM 路由流式调用完成";
                    default -> "流式调用完成：" + name;
                };
                case "CANCELLED" -> "流式调用已取消：" + name;
                case "ERROR" -> hasText(error) ? error : ("流式调用失败：" + name);
                default -> status + "：" + name;
            };
        }
    }

    private static String stripPrefix(String value, String prefix) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }

    private String errorText(Throwable error) {
        if (error == null) return null;
        String value = error.getClass().getSimpleName() + ": " + StrUtil.nullToEmpty(error.getMessage());
        return value.length() <= properties.getMaxErrorLength()
                ? value : value.substring(0, properties.getMaxErrorLength());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

