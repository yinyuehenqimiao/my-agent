package com.myagent.framework.trace;

import com.alibaba.ttl.TransmittableThreadLocal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * RAG Trace 上下文
 * 使用 TTL 在异步线程池中透传 traceId 与节点栈
 */
public final class RagTraceContext {

    private static final TransmittableThreadLocal<String> TRACE_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<String> TASK_ID = new TransmittableThreadLocal<>();
    private static final TransmittableThreadLocal<Map<String, String>> ATTRS = new TransmittableThreadLocal<>() {
        @Override
        public Map<String, String> copy(Map<String, String> parentValue) {
            return parentValue == null ? null : new HashMap<>(parentValue);
        }
    };
    // 父线程 -> 子线程的 NODE_STACK 必须深拷贝
    // 默认 copy() 返回父值引用，并发子任务会共用同一个 Deque，
    // 互相 push/pop 时父子节点 ID 串挂，trace 层级紊乱
    private static final TransmittableThreadLocal<Deque<String>> NODE_STACK = new TransmittableThreadLocal<>() {
        @Override
        public Deque<String> copy(Deque<String> parentValue) {
            return parentValue == null ? null : new ArrayDeque<>(parentValue);
        }
    };

    private RagTraceContext() {
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static String getTaskId() {
        return TASK_ID.get();
    }

    public static void setTaskId(String taskId) {
        TASK_ID.set(taskId);
    }

    public static void putAttr(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        Map<String, String> attrs = ATTRS.get();
        if (attrs == null) {
            attrs = new HashMap<>();
            ATTRS.set(attrs);
        }
        attrs.put(key, value);
    }

    public static String getAttr(String key) {
        Map<String, String> attrs = ATTRS.get();
        return attrs == null || key == null ? null : attrs.get(key);
    }

    public static int depth() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? 0 : stack.size();
    }

    public static String currentNodeId() {
        Deque<String> stack = NODE_STACK.get();
        return stack == null ? null : stack.peek();
    }

    public static void pushNode(String nodeId) {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            NODE_STACK.set(stack);
        }
        stack.push(nodeId);
    }

    public static void popNode() {
        Deque<String> stack = NODE_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            NODE_STACK.remove();
        }
    }

    public static void clear() {
        TRACE_ID.remove();
        TASK_ID.remove();
        NODE_STACK.remove();
        ATTRS.remove();
    }
}
