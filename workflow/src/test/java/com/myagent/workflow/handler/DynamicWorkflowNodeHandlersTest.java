package com.myagent.workflow.handler;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.workflow.WorkflowKnowledgePort;
import com.myagent.framework.workflow.WorkflowReactPort;
import com.myagent.workflow.model.WorkflowProcess;
import com.myagent.workflow.runtime.WorkflowKnowledgeService;
import com.myagent.workflow.runtime.WorkflowNodeContext;
import com.myagent.workflow.runtime.WorkflowNodeContextFactory;
import com.myagent.workflow.runtime.WorkflowToolExecutionService;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DynamicWorkflowNodeHandlersTest {
    @Test
    void knowledgeHandlerWritesSummaryInsteadOfRawChunks() throws Exception {
        WorkflowTaskHandlerRegistry registry = new WorkflowTaskHandlerRegistry();
        WorkflowNodeContextFactory contexts = mock(WorkflowNodeContextFactory.class);
        WorkflowKnowledgeService knowledge = mock(WorkflowKnowledgeService.class);
        WorkflowToolExecutionService tools = mock(WorkflowToolExecutionService.class);
        @SuppressWarnings("unchecked") ObjectProvider<WorkflowReactPort> react = mock(ObjectProvider.class);
        new DynamicWorkflowNodeHandlers(registry, contexts, knowledge, tools, react).register();

        DelegateExecution execution = mock(DelegateExecution.class);
        LoginUser identity = LoginUser.builder().tenantId("tenant-a").userId("u1").build();
        WorkflowProcess.Node node = new WorkflowProcess.Node("kb", "knowledge", 1,
                Map.of("questionTemplate", "制度：${topic}", "outputVariable", "policyAnswer"));
        WorkflowNodeContext context = new WorkflowNodeContext("i1", "kb", 1, node, identity,
                Map.of("topic", "请假"), execution);
        when(contexts.create(execution)).thenReturn(context);
        when(knowledge.answer(identity, "制度：请假")).thenReturn(new WorkflowKnowledgePort.Answer(
                "ANSWERED", "年假需提前申请", List.of(new WorkflowKnowledgePort.Evidence(
                "d1", "请假制度", "c1", "摘要证据", "v1"))));

        registry.get("knowledge", 1).handle(execution);

        verify(execution).setVariable("policyAnswer", Map.of(
                "status", "ANSWERED", "summary", "年假需提前申请",
                "sources", List.of(new WorkflowKnowledgePort.Evidence("d1", "请假制度", "c1", "摘要证据", "v1")),
                "evidenceCount", 1));
        verify(execution).setVariable("kb_hasEvidence", true);
        assertThat(registry.get("tool.query", 1)).isNotNull();
        assertThat(registry.get("tool.execute", 1)).isNotNull();
        assertThat(registry.get("react", 1)).isNotNull();
        assertThat(registry.get("reply", 1)).isNotNull();
    }
}
