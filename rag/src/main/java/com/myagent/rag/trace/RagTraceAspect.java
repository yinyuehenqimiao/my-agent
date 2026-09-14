package com.myagent.rag.trace;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.myagent.framework.trace.RagTraceContext;
import com.myagent.framework.trace.RagTraceNode;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Date;

/** 注解式推理节点采集；采集故障只告警，不改变普通推理结果。 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RagTraceAspect {

    private final RagTraceAuditService auditService;
    private final RagTraceProperties properties;

    @Around("@annotation(traceNode)")
    public Object aroundNode(ProceedingJoinPoint joinPoint, RagTraceNode traceNode) throws Throwable {
        String traceId = RagTraceContext.getTraceId();
        if (!properties.isEnabled() || !hasText(traceId)) {
            return joinPoint.proceed();
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String nodeId = IdUtil.getSnowflakeNextIdStr();
        long started = System.currentTimeMillis();
        try {
            auditService.startNode(RagTraceNodeDO.builder()
                    .id(IdUtil.getSnowflakeNextIdStr()).traceId(traceId).nodeId(nodeId)
                    .parentNodeId(RagTraceContext.currentNodeId()).depth(RagTraceContext.depth())
                    .nodeType(StrUtil.blankToDefault(traceNode.type(), "METHOD"))
                    .nodeName(StrUtil.blankToDefault(traceNode.name(), method.getName()))
                    .className(method.getDeclaringClass().getName()).methodName(method.getName())
                    .status("RUNNING").startTime(new Date(started)).build());
        } catch (Exception auditError) {
            log.warn("推理节点 Trace 启动失败，继续执行业务, traceId: {}, node: {}", traceId, nodeId, auditError);
            return joinPoint.proceed();
        }

        RagTraceContext.pushNode(nodeId);
        try {
            Object result = joinPoint.proceed();
            finish(traceId, nodeId, "SUCCESS", null, started);
            return result;
        } catch (Throwable error) {
            finish(traceId, nodeId, "ERROR", errorText(error), started);
            throw error;
        } finally {
            RagTraceContext.popNode();
        }
    }

    private void finish(String traceId, String nodeId, String status, String error, long started) {
        try {
            auditService.finishNode(traceId, nodeId, status, error, new Date(),
                    System.currentTimeMillis() - started);
        } catch (Exception auditError) {
            log.warn("推理节点 Trace 收尾失败，继续执行业务, traceId: {}, node: {}", traceId, nodeId, auditError);
        }
    }

    private String errorText(Throwable error) {
        String text = error.getClass().getSimpleName() + ": " + StrUtil.nullToEmpty(error.getMessage());
        return text.length() <= properties.getMaxErrorLength()
                ? text : text.substring(0, properties.getMaxErrorLength());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
