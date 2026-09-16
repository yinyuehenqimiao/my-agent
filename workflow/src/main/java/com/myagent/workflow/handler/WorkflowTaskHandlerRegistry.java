package com.myagent.workflow.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流任务处理器注册表：动态流程按节点类型和契约版本注册；旧 BPMN 保留节点 id 兼容入口。
 */
@Slf4j
@Component
public class WorkflowTaskHandlerRegistry {

    private final Map<String, WorkflowTaskHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, WorkflowTaskHandler> legacyHandlers = new ConcurrentHashMap<>();

    /** 注册动态业务积木执行器。 */
    public void register(String nodeType, int nodeVersion, WorkflowTaskHandler handler) {
        String key = key(nodeType, nodeVersion);
        handlers.put(key, handler);
        log.info("workflow node handler registered: {}", key);
    }

    /** 旧 BPMN 兼容入口，新动态流程不得使用。 */
    public void register(String activityId, WorkflowTaskHandler handler) {
        legacyHandlers.put(activityId, handler);
        log.info("legacy workflow task handler registered: {}", activityId);
    }

    public WorkflowTaskHandler get(String nodeType, int nodeVersion) {
        return handlers.get(key(nodeType, nodeVersion));
    }

    public WorkflowTaskHandler getLegacy(String activityId) {
        return legacyHandlers.get(activityId);
    }

    private static String key(String nodeType, int nodeVersion) {
        if (nodeType == null || nodeType.isBlank() || nodeVersion < 1) {
            throw new IllegalArgumentException("节点类型和版本不合法");
        }
        return nodeType.trim() + ":v" + nodeVersion;
    }
}
