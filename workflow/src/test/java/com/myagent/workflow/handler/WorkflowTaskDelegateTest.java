package com.myagent.workflow.handler;

import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskDelegateTest {

    @Test
    void dispatchesRepeatedCanvasNodesByTypeAndVersion() {
        WorkflowTaskHandlerRegistry registry = new WorkflowTaskHandlerRegistry();
        AtomicBoolean invoked = new AtomicBoolean();
        registry.register("reply", 1, execution -> invoked.set(true));
        DelegateExecution execution = mock(DelegateExecution.class);
        ServiceTask task = new ServiceTask();
        task.getFieldExtensions().add(field("nodeType", "reply"));
        task.getFieldExtensions().add(field("nodeVersion", "1"));
        when(execution.getCurrentFlowElement()).thenReturn(task);
        when(execution.getCurrentActivityId()).thenReturn("任意画布节点ID");

        new WorkflowTaskDelegate(registry).execute(execution);

        assertTrue(invoked.get());
    }

    private static FieldExtension field(String name, String value) {
        FieldExtension field = new FieldExtension();
        field.setFieldName(name);
        field.setStringValue(value);
        return field;
    }
}
