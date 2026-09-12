package com.myagent.workflow.handler;

import org.flowable.engine.delegate.DelegateExecution;

/**
 * 工作流服务任务处理器 SPI。
 * <p>
 * BPMN 中 serviceTask 通过白名单中央委托 {@code WorkflowTaskDelegate} 调用，
 * 委托按节点 id 分发到注册的 {@link WorkflowTaskHandler}。业务模块实现具体逻辑，
 * 不在 BPMN 中写任意 Java 类。
 */
@FunctionalInterface
public interface WorkflowTaskHandler {

    /** 处理服务任务节点 */
    void handle(DelegateExecution execution) throws Exception;
}
