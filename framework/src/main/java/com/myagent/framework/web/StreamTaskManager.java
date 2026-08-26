package com.myagent.framework.web;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地流式任务取消管理（阶段1精简版，不依赖 Redis）
 * <p>
 * ragent 生产版用 Redis + Pub/Sub 做跨节点取消；这里先把「注册 / 取消 / 回调」协议跑通。
 */
@Slf4j
@Component
public class StreamTaskManager {

    private final Map<String, StreamTaskInfo> tasks = new ConcurrentHashMap<>();

    public void register(String taskId, String ownerUserId, Runnable onCancelFinalizer) {
        StreamTaskInfo info = tasks.computeIfAbsent(taskId, id -> new StreamTaskInfo());
        info.ownerUserId = ownerUserId;
        info.finalizer = onCancelFinalizer;
        if (info.cancelled.get() && onCancelFinalizer != null) {
            onCancelFinalizer.run();
        }
    }

    public void bindHandle(String taskId, Runnable cancelAction) {
        StreamTaskInfo info = tasks.computeIfAbsent(taskId, id -> new StreamTaskInfo());
        info.cancelAction = cancelAction;
        if (info.cancelled.get() && cancelAction != null) {
            cancelAction.run();
        }
    }

    public boolean isCancelled(String taskId) {
        StreamTaskInfo info = tasks.get(taskId);
        return info != null && info.cancelled.get();
    }

    public void cancel(String taskId) {
        StreamTaskInfo info = tasks.get(taskId);
        if (info == null) {
            return;
        }
        if (!info.cancelled.compareAndSet(false, true)) {
            return;
        }
        log.info("取消流式任务 taskId={}", taskId);
        if (info.cancelAction != null) {
            info.cancelAction.run();
        }
        if (info.finalizer != null) {
            info.finalizer.run();
        }
    }

    public void unregister(String taskId) {
        tasks.remove(taskId);
    }

    @PreDestroy
    public void destroy() {
        tasks.clear();
    }

    private static final class StreamTaskInfo {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile String ownerUserId;
        private volatile Runnable cancelAction;
        private volatile Runnable finalizer;
    }
}
