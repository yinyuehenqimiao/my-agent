package com.myagent.rag.trace;

import com.myagent.framework.trace.RagTraceContext;
import com.myagent.framework.trace.RagTraceNode;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RagTraceAspectTest {

    @AfterEach
    void clearTrace() {
        RagTraceContext.clear();
    }

    @Test
    void annotatedPipelineStepCreatesSuccessfulChildNode() {
        RagTraceAuditService audit = mock(RagTraceAuditService.class);
        RagTraceProperties properties = new RagTraceProperties();
        RagTraceAspect aspect = new RagTraceAspect(audit, properties);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SamplePipeline());
        factory.addAspect(aspect);
        SamplePipeline proxy = factory.getProxy();
        RagTraceContext.setTraceId("trace-1");
        RagTraceContext.pushNode("parent-1");

        assertThat(proxy.classify()).isEqualTo("ok");

        ArgumentCaptor<RagTraceNodeDO> node = ArgumentCaptor.forClass(RagTraceNodeDO.class);
        verify(audit).startNode(node.capture());
        assertThat(node.getValue().getTraceId()).isEqualTo("trace-1");
        assertThat(node.getValue().getParentNodeId()).isEqualTo("parent-1");
        assertThat(node.getValue().getNodeType()).isEqualTo("INTENT");
        verify(audit).finishNode(eq("trace-1"), eq(node.getValue().getNodeId()), eq("SUCCESS"),
                org.mockito.ArgumentMatchers.isNull(), any(), any(Long.class), any());
        assertThat(RagTraceContext.currentNodeId()).isEqualTo("parent-1");
    }

    @Test
    void annotatedPipelineStepPersistsErrorAndRethrows() {
        RagTraceAuditService audit = mock(RagTraceAuditService.class);
        RagTraceProperties properties = new RagTraceProperties();
        RagTraceAspect aspect = new RagTraceAspect(audit, properties);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SamplePipeline());
        factory.addAspect(aspect);
        SamplePipeline proxy = factory.getProxy();
        RagTraceContext.setTraceId("trace-2");

        assertThatThrownBy(proxy::fail).isInstanceOf(IllegalStateException.class).hasMessage("分类失败");

        ArgumentCaptor<RagTraceNodeDO> node = ArgumentCaptor.forClass(RagTraceNodeDO.class);
        verify(audit).startNode(node.capture());
        verify(audit).finishNode(eq("trace-2"), eq(node.getValue().getNodeId()), eq("ERROR"),
                org.mockito.ArgumentMatchers.contains("分类失败"), any(), any(Long.class), any());
    }

    static class SamplePipeline {
        @RagTraceNode(name = "intent-classify", type = "INTENT")
        public String classify() {
            return "ok";
        }

        @RagTraceNode(name = "intent-classify", type = "INTENT")
        public String fail() {
            throw new IllegalStateException("分类失败");
        }
    }
}
