package com.myagent.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流任务处理器注册表：按节点 id 注册处理器。
 * 业务模块在启动时通过 {@link #register(String, WorkflowTaskHandler)} 注册。
 */
@Slf4j
@Component
public class WorkflowTaskHandlerRegistry {

    private final Map<String, WorkflowTaskHandler> handlers = new ConcurrentHashMap<>();

    public void register(String activityId, WorkflowTaskHandler handler) {
        handlers.put(activityId, handler);
        log.info("workflow task handler registered: {}", activityId);
    }

    public WorkflowTaskHandler get(String activityId) {
        return handlers.get(activityId);
    }
}
