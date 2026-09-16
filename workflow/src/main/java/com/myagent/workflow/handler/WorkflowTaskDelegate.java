package com.myagent.workflow.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.ServiceTask;
import org.springframework.stereotype.Component;

/**
 * 白名单中央委托：BPMN serviceTask 通过 flowable:class 指向本类，
 * 本类按当前节点 id 分发到 {@link WorkflowTaskHandlerRegistry} 中注册的处理器。
 * <p>
 * 这是 BPMN 中唯一允许的 Java 类（见 BpmnValidator 白名单），禁止任意其他 Java 类。
 */
@Slf4j
@Component("workflowTaskDelegate")
@RequiredArgsConstructor
public class WorkflowTaskDelegate implements JavaDelegate {

    private final WorkflowTaskHandlerRegistry registry;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.myagent.workflow.runtime.WorkflowNodeExecutionRecorder executionRecorder;

    @Override
    public void execute(DelegateExecution execution) {
        String activityId = execution.getCurrentActivityId();
        String nodeType = field(execution, "nodeType");
        String versionText = field(execution, "nodeVersion");
        WorkflowTaskHandler handler = nodeType == null
                ? registry.getLegacy(activityId)
                : registry.get(nodeType, parseVersion(versionText));
        if (handler == null) {
            throw new IllegalStateException("未注册的工作流任务处理器: "
                    + (nodeType == null ? activityId : nodeType + ":v" + versionText));
        }
        String executionId = executionRecorder == null ? null : executionRecorder.start(execution, nodeType);
        try {
            handler.handle(execution);
            if (executionRecorder != null) executionRecorder.success(executionId, execution);
        } catch (Exception e) {
            log.error("workflow task handler failed activity={} traceId={}", activityId, execution.getProcessInstanceBusinessKey(), e);
            RuntimeException runtime = e instanceof RuntimeException re ? re : new RuntimeException(e);
            if (executionRecorder != null) executionRecorder.failure(executionId, runtime);
            throw runtime;
        }
    }

    private static String field(DelegateExecution execution, String name) {
        if (!(execution.getCurrentFlowElement() instanceof ServiceTask task)) return null;
        return task.getFieldExtensions().stream()
                .filter(item -> name.equals(item.getFieldName()))
                .map(FieldExtension::getStringValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private static int parseVersion(String value) {
        try {
            int version = Integer.parseInt(value);
            if (version < 1) throw new NumberFormatException();
            return version;
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("工作流节点版本不合法: " + value);
        }
    }
}
