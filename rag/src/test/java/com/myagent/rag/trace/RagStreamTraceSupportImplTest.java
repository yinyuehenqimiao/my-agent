package com.myagent.rag.trace;

import com.myagent.framework.trace.RagStreamTraceSupport;
import com.myagent.framework.trace.RagTraceContext;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RagStreamTraceSupportImplTest {

    @AfterEach
    void clearTrace() {
        RagTraceContext.clear();
    }

    @Test
    void streamSpanCanDetachOnCallerThreadAndFinishOnceOnWorkerThread() throws Exception {
        RagTraceAuditService audit = mock(RagTraceAuditService.class);
        RagTraceContext.setTraceId("trace-stream");
        RagTraceContext.pushNode("routing-node");
        RagStreamTraceSupport support = new RagStreamTraceSupportImpl(new RagTraceProperties(), audit);

        RagStreamTraceSupport.StreamSpan span = support.beginStreamNode("openai-stream", "LLM_PROVIDER");
        ArgumentCaptor<RagTraceNodeDO> node = ArgumentCaptor.forClass(RagTraceNodeDO.class);
        verify(audit).startNode(node.capture());
        assertThat(node.getValue().getParentNodeId()).isEqualTo("routing-node");
        span.detach();
        assertThat(RagTraceContext.currentNodeId()).isEqualTo("routing-node");

        Thread worker = new Thread(span::finishSuccess);
        worker.start();
        worker.join();
        span.finishError(new IllegalStateException("late error"));

        verify(audit, times(1)).finishNode(eq("trace-stream"), eq(node.getValue().getNodeId()),
                eq("SUCCESS"), org.mockito.ArgumentMatchers.isNull(), any(), any(Long.class));
    }
}
