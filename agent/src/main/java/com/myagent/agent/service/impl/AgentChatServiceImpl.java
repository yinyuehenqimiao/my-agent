/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.myagent.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.agent.config.ConditionalOnAgentEngine;
import com.myagent.agent.config.ReActAgentProvider;
import com.myagent.agent.config.ReActAgentProvider.ActiveAgent;
import com.myagent.agent.dto.AgentMetaPayload;
import com.myagent.agent.dto.AgentBlock;
import com.myagent.agent.dto.AgentCompletionPayload;
import com.myagent.agent.dto.AgentMessageDelta;
import com.myagent.agent.dto.AgentToolProgress;
import com.myagent.agent.enums.AgentMessageStatus;
import com.myagent.agent.enums.AgentSSEEventType;
import com.myagent.agent.routing.ChatRoutePlan;
import com.myagent.agent.routing.ChatRoutePlanner;
import com.myagent.agent.routing.ConversationContinuationResolver;
import com.myagent.agent.routing.ConversationWorkflowStateService;
import com.myagent.agent.routing.WorkflowContinuation;
import com.myagent.agent.service.AgentChatService;
import com.myagent.agent.service.AgentConversationService;
import com.myagent.agent.service.handler.AgentRunGate;
import com.myagent.agent.service.handler.AgentRunHandle;
import com.myagent.agent.service.handler.AgentStreamEventBridge;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.trace.RagTraceContext;
import com.myagent.framework.trace.RagStreamTraceSupport;
import com.myagent.framework.web.SseEmitterSender;
import com.myagent.framework.web.StreamTaskManager;
import com.myagent.rag.dao.entity.RagTraceRunDO;
import com.myagent.rag.enums.IntentKind;
import com.myagent.rag.service.KnowledgeSearchFacade;
import com.myagent.rag.trace.RagTraceAuditService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 流式对话编排：会话落库 -> ReAct 事件流 -> SSE 桥 -> 取消接线
 * 会话 ID 即 AgentScope 的 sessionId，多轮记忆由状态存储按次加载
 */
@Slf4j
@Service
@ConditionalOnAgentEngine
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final ReActAgentProvider agentProvider;
    private final AgentConversationService conversationService;
    private final StreamTaskManager taskManager;
    private final AgentRunGate runGate;
    private final ChatRoutePlanner routePlanner;
    private final KnowledgeSearchFacade knowledgeSearchFacade;
    private final ConversationContinuationResolver continuationResolver;
    private final ConversationWorkflowStateService workflowStateService;
    private final ObjectMapper objectMapper;
    private final RagTraceAuditService traceAuditService;
    private final RagStreamTraceSupport streamTraceSupport;

    @Override
    public void streamChat(String question, String conversationId, SseEmitter emitter) {
        LoginUser identity = UserContext.requireUser();
        String userId = identity.getUserId();
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? IdUtil.getSnowflakeNextIdStr()
                : conversationId;
        String taskId = IdUtil.getSnowflakeNextIdStr();

        // 闸门先于一切副作用：被拒的请求不该留下 META 事件、会话行与任务登记
        Runnable releaseGate = runGate.acquire(userId, taskId, actualConversationId);
        boolean started = false;
        try {
            startRun(question, identity, actualConversationId, taskId, emitter, releaseGate);
            started = true;
        } finally {
            // 启动期还没有收尾路可挂，就地归还闸门并撤销任务登记，否则该用户被挡到 TTL 过期
            // 用 finally 而非 catch RuntimeException：启动段抛 Error 同样会把闸门锁死半小时
            if (!started) {
                releaseGate.run();
                taskManager.unregister(taskId);
            }
        }
    }

    private void startRun(String question, LoginUser identity, String conversationId, String taskId,
                          SseEmitter emitter, Runnable releaseGate) {
        TraceRun trace = beginTrace(question, identity, conversationId, taskId);
        if (trace.started()) {
            RagTraceContext.setTraceId(trace.traceId());
            RagTraceContext.setTaskId(taskId);
        }
        try {
            startTracedRun(question, identity, conversationId, taskId, emitter, releaseGate, trace);
        } catch (RuntimeException | Error error) {
            finishTrace(trace, "ERROR", error);
            throw error;
        } finally {
            RagTraceContext.clear();
        }
    }

    private void startTracedRun(String question, LoginUser identity, String conversationId, String taskId,
                                SseEmitter emitter, Runnable releaseGate, TraceRun trace) {
        String userId = identity.getUserId();
        SseEmitterSender sender = new SseEmitterSender(emitter);
        sender.sendEvent(AgentSSEEventType.META.value(), new AgentMetaPayload(conversationId, taskId));

        String title = conversationService.touchConversation(conversationId, userId, question);
        String questionMessageId = conversationService.addUserMessage(conversationId, userId, question);

        String tenantId = StrUtil.blankToDefault(identity.getTenantId(), "default");
        Optional<WorkflowContinuation> continuation = continuationResolver.resolve(
                tenantId, userId, conversationId);
        ChatRoutePlan plan;
        if (continuation.isPresent()) {
            plan = routePlanner.plan(question, continuation.get(), identity);
        } else {
            plan = routePlanner.plan(question, false, identity);
        }
        sender.sendEvent(AgentSSEEventType.ROUTE_DECISION.value(), routePayload(plan));

        AgentRunHandle runHandle = new AgentRunHandle(taskId, sender, taskManager);
        runHandle.onRelease(releaseGate);
        runHandle.onSettlement((settlement, error) -> finishTrace(trace, switch (settlement) {
            case SUCCESS -> "SUCCESS";
            case CANCELLED -> "CANCELLED";
            case ERROR -> "ERROR";
        }, error));
        // 记忆常驻内存是确定性泄漏，流一结束就驱逐：三条收尾路都会执行，换来内存上界
        // 状态已在框架侧随本轮落库（正常完成与打断各自 save 后才发终答），下一轮从 PG 读回，代价是一次反序列化
        runHandle.onRelease(() -> agentProvider.evictStateCache(userId, conversationId));
        bindEmitterLifecycle(emitter, runHandle, taskId);

        RouteDecision decision = plan.route().getDecision();
        if (decision == RouteDecision.DENY) {
            registerDirect(taskId, userId, title, runHandle);
            completeDirect(runHandle, conversationId, userId, title, questionMessageId,
                    "当前请求未通过权限策略，无法执行。" + reasonSuffix(plan), true);
            return;
        }
        if (decision == RouteDecision.CLARIFY) {
            registerDirect(taskId, userId, title, runHandle);
            completeDirect(runHandle, conversationId, userId, title, questionMessageId,
                    "请补充更明确的业务对象、操作或必要参数。" + reasonSuffix(plan), false);
            return;
        }
        if (decision == RouteDecision.SYSTEM && plan.intentKind() == IntentKind.KB) {
            registerDirect(taskId, userId, title, runHandle);
            startKnowledgeRun(question, identity, conversationId, userId, title, questionMessageId, runHandle);
            return;
        }
        if (decision == RouteDecision.SYSTEM) {
            registerDirect(taskId, userId, title, runHandle);
            completeDirect(runHandle, conversationId, userId, title, questionMessageId,
                    "你好，我是企业智能助手，可以帮助你查询公司制度、个人信息并办理已配置的业务流程。", false);
            return;
        }

        ActiveAgent activeAgent = decision == RouteDecision.WORKFLOW
                ? agentProvider.getWorkflowAgent(plan.route().getTarget())
                : agentProvider.getAgent();
        if (decision == RouteDecision.WORKFLOW) {
            workflowStateService.activate(tenantId, userId, conversationId, plan.route().getTarget());
            sender.sendEvent(AgentSSEEventType.WORKFLOW_PROGRESS.value(), Map.of(
                    "workflowDefKey", plan.route().getTarget(), "node", "参数收集与确认", "status", "RUNNING"));
        }
        String executionType = decision == RouteDecision.WORKFLOW ? "WORKFLOW" : "REACT";
        String executionName = (decision == RouteDecision.WORKFLOW ? "workflow-agent:" : "react-agent:")
                + StrUtil.blankToDefault(plan.route().getTarget(), "default");
        RagStreamTraceSupport.StreamSpan executionSpan = streamTraceSupport.beginStreamNode(executionName, executionType);
        runHandle.onSettlement((settlement, error) -> {
            finishTrace(trace, switch (settlement) {
                case SUCCESS -> "SUCCESS";
                case CANCELLED -> "CANCELLED";
                case ERROR -> "ERROR";
            }, error);
            switch (settlement) {
                case SUCCESS -> executionSpan.finishSuccess();
                case CANCELLED -> executionSpan.finishCancelledIfRunning();
                case ERROR -> executionSpan.finishError(error);
            }
            if (decision == RouteDecision.WORKFLOW && settlement == AgentRunHandle.Settlement.SUCCESS) {
                synchronizeWorkflowState(identity, tenantId, userId, conversationId);
            }
        });
        try {
            startAgentRun(question, userId, conversationId, taskId, title, questionMessageId,
                    runHandle, activeAgent);
        } catch (RuntimeException | Error error) {
            executionSpan.finishError(error);
            throw error;
        } finally {
            executionSpan.detach();
        }
    }

    private void synchronizeWorkflowState(LoginUser identity, String tenantId, String userId,
                                          String conversationId) {
        LoginUser previous = UserContext.get();
        try {
            // HTTP ERP 适配器还会校验线程身份；异步流完成时显式恢复请求身份，不能依赖 ThreadLocal 碰巧透传。
            UserContext.set(identity);
            continuationResolver.synchronizeAfterWorkflowTurn(tenantId, userId, conversationId);
        } finally {
            if (previous == null) {
                UserContext.clear();
            } else {
                UserContext.set(previous);
            }
        }
    }

    private TraceRun beginTrace(String question, LoginUser identity, String conversationId, String taskId) {
        String traceId = IdUtil.getSnowflakeNextIdStr();
        long startMillis = System.currentTimeMillis();
        try {
            traceAuditService.startRun(RagTraceRunDO.builder()
                    .id(IdUtil.getSnowflakeNextIdStr())
                    .traceId(traceId)
                    .traceName("agent-chat")
                    .entryMethod("AgentChatService#streamChat")
                    .conversationId(conversationId)
                    .taskId(taskId)
                    .userId(identity.getUserId())
                    .tenantId(StrUtil.blankToDefault(identity.getTenantId(), "default"))
                    .status("RUNNING")
                    .startTime(new Date(startMillis))
                    .extraData(cn.hutool.json.JSONUtil.createObj()
                            .set("questionLength", StrUtil.length(question))
                            .set("question", question)
                            .toString())
                    .build());
            return new TraceRun(traceId, startMillis, true);
        } catch (Exception error) {
            log.warn("推理 Trace 启动失败，继续执行对话, taskId: {}", taskId, error);
            return new TraceRun(traceId, startMillis, false);
        }
    }

    private void finishTrace(TraceRun trace, String status, Throwable error) {
        if (!trace.started() || !trace.finished().compareAndSet(false, true)) {
            return;
        }
        try {
            String message = error == null ? null : error.getClass().getSimpleName() + ": " + StrUtil.nullToEmpty(error.getMessage());
            traceAuditService.finishRun(trace.traceId(), status, message, new Date(),
                    System.currentTimeMillis() - trace.startMillis());
        } catch (Exception auditError) {
            log.warn("推理 Trace 收尾失败，traceId: {}", trace.traceId(), auditError);
        }
    }

    private record TraceRun(String traceId, long startMillis, boolean started, AtomicBoolean finished) {
        private TraceRun(String traceId, long startMillis, boolean started) {
            this(traceId, startMillis, started, new AtomicBoolean(false));
        }
    }

    private void startAgentRun(String question, String userId, String conversationId, String taskId,
                               String title, String questionMessageId, AgentRunHandle runHandle,
                               ActiveAgent activeAgent) {
        AgentStreamEventBridge bridge = new AgentStreamEventBridge(AgentStreamEventBridge.Params.builder()
                .runHandle(runHandle)
                .conversationService(conversationService)
                .catalog(activeAgent.catalog())
                .conversationId(conversationId)
                .userId(userId)
                .title(title)
                .replyToMessageId(questionMessageId)
                .build());
        @SuppressWarnings("resource")
        ReActAgent agent = activeAgent.agent();
        Disposable.Swap subscription = Disposables.swap();
        runHandle.bindStream(subscription, () -> agent.interrupt(userId, conversationId));
        taskManager.register(taskId, userId, bridge::finishCancelledStream);
        taskManager.bindHandle(taskId, runHandle::interruptUpstream);

        Flux<AgentEvent> events = agent.streamEvents(question, RuntimeContext.builder()
                .userId(userId)
                .sessionId(conversationId)
                .build());
        Disposable disposable = events.subscribe(bridge::onEvent, bridge::onError, bridge::onComplete);

        // 订阅句柄通过 Swap 后补；若取消早到，后放入的订阅会立即 dispose，避免启动窗口漏取消。
        subscription.update(disposable);
    }

    private void startKnowledgeRun(String question, LoginUser identity, String conversationId, String userId,
                                   String title, String questionMessageId, AgentRunHandle runHandle) {
        Disposable.Swap subscription = Disposables.swap();
        runHandle.bindStream(subscription, () -> { });
        taskManager.bindHandle(runHandle.getTaskId(), runHandle::interruptUpstream);
        Disposable disposable = Mono.fromCallable(() -> {
                    LoginUser previous = UserContext.get();
                    try {
                        UserContext.set(identity);
                        return knowledgeSearchFacade.searchWithEvidence(question,
                                conversationService.loadRecentTurns(conversationId, userId, 2));
                    } finally {
                        if (previous == null) UserContext.clear();
                        else UserContext.set(previous);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(answer -> {
                    completeKnowledge(runHandle, conversationId, userId, title, questionMessageId, answer);
                }, error -> runHandle.fail(error, () -> {
                    runHandle.getSender().sendEvent(AgentSSEEventType.ERROR.value(),
                            Map.of("error", "知识库查询失败，请稍后重试"));
                    runHandle.getSender().sendEvent(AgentSSEEventType.DONE.value(), "[DONE]");
                }));
        subscription.update(disposable);
    }

    private void completeDirect(AgentRunHandle runHandle, String conversationId, String userId, String title,
                                String replyToMessageId, String content, boolean denied) {
        List<AgentBlock> blocks = List.of(answerBlock(content));
        runHandle.complete(() -> {
            if (denied) {
                runHandle.getSender().sendEvent(AgentSSEEventType.POLICY_DENIED.value(), Map.of("reason", content));
            }
            runHandle.getSender().sendEvent(AgentSSEEventType.MESSAGE.value(), new AgentMessageDelta("response", content));
            String messageId = persistDirectAnswer(conversationId, userId, replyToMessageId, content, blocks);
            runHandle.getSender().sendEvent(AgentSSEEventType.FINISH.value(),
                    new AgentCompletionPayload(messageId, title, AgentMessageStatus.NORMAL.name()));
            runHandle.getSender().sendEvent(AgentSSEEventType.DONE.value(), "[DONE]");
        });
    }

    private void completeKnowledge(AgentRunHandle runHandle, String conversationId, String userId, String title,
                                   String replyToMessageId, KnowledgeSearchFacade.KnowledgeAnswer answer) {
        String evidenceJson;
        try {
            evidenceJson = objectMapper.writeValueAsString(Map.of(
                    "status", answer.status(), "sources", answer.sources()));
        } catch (Exception error) {
            log.warn("知识来源序列化失败, conversationId: {}", conversationId, error);
            evidenceJson = "{\"status\":\"SOURCE_SERIALIZATION_FAILED\",\"sources\":[]}";
        }
        String finalEvidenceJson = evidenceJson;
        runHandle.complete(() -> {
            if (!answer.sources().isEmpty()) {
                runHandle.getSender().sendEvent(AgentSSEEventType.TOOL.value(),
                        new AgentToolProgress("search_knowledge", "知识来源", "start", null, null));
                runHandle.getSender().sendEvent(AgentSSEEventType.TOOL.value(),
                        new AgentToolProgress("search_knowledge", "知识来源", "end", finalEvidenceJson, true));
            }
            runHandle.getSender().sendEvent(AgentSSEEventType.MESSAGE.value(),
                    new AgentMessageDelta("response", answer.answer()));
            List<AgentBlock> blocks = new java.util.ArrayList<>();
            if (!answer.sources().isEmpty()) {
                blocks.add(AgentBlock.builder().kind("tool").at(nowText())
                        .name("search_knowledge").displayName("知识来源")
                        .status("done").result(finalEvidenceJson).build());
            }
            blocks.add(answerBlock(answer.answer()));
            String messageId = persistDirectAnswer(
                    conversationId, userId, replyToMessageId, answer.answer(), blocks);
            runHandle.getSender().sendEvent(AgentSSEEventType.FINISH.value(),
                    new AgentCompletionPayload(messageId, title, AgentMessageStatus.NORMAL.name()));
            runHandle.getSender().sendEvent(AgentSSEEventType.DONE.value(), "[DONE]");
        });
    }

    private String persistDirectAnswer(String conversationId, String userId, String replyToMessageId,
                                       String content, List<AgentBlock> blocks) {
        try {
            return conversationService.addAssistantMessage(conversationId, userId, content, "",
                    blocks, replyToMessageId, AgentMessageStatus.NORMAL);
        } catch (Exception error) {
            // 与 ReAct 收尾一致：落库故障不能吞掉 finish/done，让前端永远停在“生成中”。
            log.error("路由直答消息落库失败, conversationId: {}", conversationId, error);
            return null;
        }
    }

    private AgentBlock answerBlock(String content) {
        return AgentBlock.builder().kind("answer").at(nowText()).text(content).build();
    }

    private String nowText() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private void finishCancelledDirect(AgentRunHandle runHandle, String title) {
        runHandle.cancel(() -> {
            runHandle.getSender().sendEvent(AgentSSEEventType.CANCEL.value(),
                    new AgentCompletionPayload(null, title, AgentMessageStatus.INTERRUPTED.name()));
            runHandle.getSender().sendEvent(AgentSSEEventType.DONE.value(), "[DONE]");
        });
    }

    private void registerDirect(String taskId, String userId, String title, AgentRunHandle runHandle) {
        taskManager.register(taskId, userId, () -> finishCancelledDirect(runHandle, title));
    }

    private Map<String, Object> routePayload(ChatRoutePlan plan) {
        return Map.of(
                "decision", plan.route().getDecision().name(),
                "target", StrUtil.nullToEmpty(plan.route().getTarget()),
                "reason", StrUtil.nullToEmpty(plan.route().getReason()),
                "confidence", plan.route().getConfidence(),
                "intentCode", StrUtil.nullToEmpty(plan.intentCode()),
                "intentName", StrUtil.nullToEmpty(plan.intentName()));
    }

    private String reasonSuffix(ChatRoutePlan plan) {
        return StrUtil.isBlank(plan.route().getReason()) ? "" : "（" + plan.route().getReason() + "）";
    }

    /**
     * 把 SSE 通道的三条终止信号接到取消协议上
     * 不接则关页、断网、超时之后事件被静默丢弃，而 ReAct 循环照常跑满迭代上限
     */
    private void bindEmitterLifecycle(SseEmitter emitter, AgentRunHandle runHandle, String taskId) {
        AtomicBoolean recycled = new AtomicBoolean(false);
        Runnable recycleUpstream = () -> {
            // 已结算说明收尾路跑过了，再取消只会往 Redis 留一条 30 分钟的死标记
            if (!runHandle.isSettled() && recycled.compareAndSet(false, true)) {
                taskManager.cancel(taskId);
            }
        };
        emitter.onTimeout(recycleUpstream);
        emitter.onError(e -> recycleUpstream.run());
        // 客户端关页时写失败走的是 completeWithError，容器既不报超时也不报错，只有 completion 兜得住
        emitter.onCompletion(recycleUpstream);
    }

    @Override
    public void stopTask(String taskId) {
        taskManager.cancelByUser(taskId);
    }
}
