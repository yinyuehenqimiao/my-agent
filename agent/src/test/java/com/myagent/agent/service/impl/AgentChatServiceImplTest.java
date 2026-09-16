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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.agent.config.ReActAgentProvider;
import com.myagent.agent.config.ReActAgentProvider.ActiveAgent;
import com.myagent.agent.dto.AgentBlock;
import com.myagent.agent.service.AgentConversationService;
import com.myagent.agent.service.AgentCapabilityService;
import com.myagent.agent.service.CurrentTimeService;
import com.myagent.agent.service.handler.AgentRunGate;
import com.myagent.agent.tool.AgentToolCatalog.ResolvedCatalog;
import com.myagent.agent.routing.ChatRoutePlan;
import com.myagent.agent.routing.ChatRoutePlanner;
import com.myagent.agent.routing.ConversationContinuationResolver;
import com.myagent.agent.routing.ConversationWorkflowStateService;
import com.myagent.agent.routing.RecentWorkflowContext;
import com.myagent.agent.routing.WorkflowContinuation;
import com.myagent.agent.routing.WorkflowConversationStatus;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.routing.RouteDecision;
import com.myagent.framework.routing.RouteResult;
import com.myagent.framework.trace.RagStreamTraceSupport;
import com.myagent.framework.web.StreamTaskManager;
import com.myagent.rag.enums.IntentKind;
import com.myagent.rag.service.KnowledgeSearchFacade;
import com.myagent.rag.dao.entity.RagTraceRunDO;
import com.myagent.rag.trace.RagTraceAuditService;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentChatServiceImplTest {

    private static final String USER_ID = "u-1001";
    private static final String CONVERSATION_ID = "c-2002";

    private ReActAgentProvider agentProvider;
    private AgentConversationService conversationService;
    private StreamTaskManager taskManager;
    private AgentRunGate runGate;
    private ChatRoutePlanner routePlanner;
    private KnowledgeSearchFacade knowledgeSearchFacade;
    private AgentCapabilityService capabilityService;
    private CurrentTimeService currentTimeService;
    private ConversationContinuationResolver continuationResolver;
    private ConversationWorkflowStateService workflowStateService;
    private RagTraceAuditService traceAuditService;
    private RagStreamTraceSupport streamTraceSupport;
    private RagStreamTraceSupport.StreamSpan executionSpan;
    private AtomicInteger gateReleased;
    private ReActAgent agent;
    private AgentChatServiceImpl service;

    @BeforeEach
    void setUp() {
        agentProvider = mock(ReActAgentProvider.class);
        conversationService = mock(AgentConversationService.class);
        taskManager = mock(StreamTaskManager.class);
        runGate = mock(AgentRunGate.class);
        routePlanner = mock(ChatRoutePlanner.class);
        knowledgeSearchFacade = mock(KnowledgeSearchFacade.class);
        capabilityService = mock(AgentCapabilityService.class);
        currentTimeService = mock(CurrentTimeService.class);
        continuationResolver = mock(ConversationContinuationResolver.class);
        workflowStateService = mock(ConversationWorkflowStateService.class);
        traceAuditService = mock(RagTraceAuditService.class);
        streamTraceSupport = mock(RagStreamTraceSupport.class);
        executionSpan = mock(RagStreamTraceSupport.StreamSpan.class);
        when(streamTraceSupport.beginStreamNode(anyString(), anyString())).thenReturn(executionSpan);
        agent = mock(ReActAgent.class);
        service = new AgentChatServiceImpl(agentProvider, conversationService, taskManager, runGate,
                routePlanner, knowledgeSearchFacade, capabilityService, currentTimeService,
                continuationResolver, workflowStateService,
                new ObjectMapper().findAndRegisterModules(),
                traceAuditService, streamTraceSupport);

        gateReleased = new AtomicInteger();
        when(runGate.acquire(anyString(), anyString(), anyString())).thenReturn(gateReleased::incrementAndGet);
        when(agentProvider.getAgent()).thenReturn(new ActiveAgent(
                agent, new ResolvedCatalog("知识库工具描述", List.of(), List.of())));
        when(conversationService.touchConversation(anyString(), anyString(), anyString())).thenReturn("会话标题");
        when(conversationService.addUserMessage(anyString(), anyString(), anyString())).thenReturn("m-3003");
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "open-question", "开放问题", IntentKind.MCP,
                RouteResult.of(RouteDecision.REACT, "default", "授权 ReAct", 0.9)));
        when(continuationResolver.resolve(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        UserContext.set(LoginUser.builder().userId(USER_ID).username("tester").build());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldEvictStateCacheWhenStreamCompletes() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());

        service.streamChat("问题", CONVERSATION_ID, new SseEmitter());

        // 不驱逐则每个 (用户, 会话) 的全量记忆在单例 Agent 里常驻到进程重启
        verify(agentProvider).evictStateCache(USER_ID, CONVERSATION_ID);
    }

    @Test
    void shouldEvictStateCacheWhenStreamCancelled() {
        // never 流不会自行走到完成路，驱逐只可能来自取消收尾
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        ArgumentCaptor<Runnable> finalizer = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, new SseEmitter());
        verify(taskManager).register(anyString(), anyString(), finalizer.capture());
        finalizer.getValue().run();

        verify(agentProvider).evictStateCache(USER_ID, CONVERSATION_ID);
    }

    @Test
    void shouldEvictOnlyOnceWhenCancelRacesCompletion() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());
        ArgumentCaptor<Runnable> finalizer = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, new SseEmitter());
        verify(taskManager).register(anyString(), anyString(), finalizer.capture());
        finalizer.getValue().run();

        verify(agentProvider, times(1)).evictStateCache(USER_ID, CONVERSATION_ID);
    }

    @Test
    void shouldReleaseGateWhenStreamCompletes() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());

        service.streamChat("问题", CONVERSATION_ID, new SseEmitter());

        // 闸门不还，该用户到 TTL 过期前发不出下一轮
        assertThat(gateReleased.get()).isOne();
    }

    @Test
    void shouldReleaseGateWhenStreamCancelled() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        ArgumentCaptor<Runnable> finalizer = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, new SseEmitter());
        verify(taskManager).register(anyString(), anyString(), finalizer.capture());
        finalizer.getValue().run();

        assertThat(gateReleased.get()).isOne();
    }

    @Test
    void shouldReleaseGateWhenStartupFails() {
        when(conversationService.touchConversation(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("库炸了"));

        assertThatThrownBy(() -> service.streamChat("问题", CONVERSATION_ID, new SseEmitter()))
                .isInstanceOf(IllegalStateException.class);

        // 启动期失败还没有收尾路可挂，闸门要就地归还
        assertThat(gateReleased.get()).isOne();
    }

    @Test
    void shouldReleaseGateWhenStartupThrowsError() {
        when(conversationService.touchConversation(anyString(), anyString(), anyString()))
                .thenThrow(new NoClassDefFoundError("类没了"));

        assertThatThrownBy(() -> service.streamChat("问题", CONVERSATION_ID, new SseEmitter()))
                .isInstanceOf(NoClassDefFoundError.class);

        // 只接 RuntimeException 的话，启动段抛 Error 会把该用户挡到 TTL 过期（默认半小时）
        assertThat(gateReleased.get()).isOne();
    }

    @Test
    void shouldUnregisterTaskWhenStartupFails() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class)))
                .thenThrow(new IllegalStateException("上游没起来"));

        assertThatThrownBy(() -> service.streamChat("问题", CONVERSATION_ID, new SseEmitter()))
                .isInstanceOf(IllegalStateException.class);

        // 已 register 未 unregister 的任务会守灵到 30 分钟 TTL，期间还能被取消去戳已丢弃的 emitter
        verify(taskManager).unregister(anyString());
    }

    @Test
    void shouldNotStartRunWhenGateRejects() {
        when(runGate.acquire(anyString(), anyString(), anyString()))
                .thenThrow(new ClientException("当前会话处理中，请稍后再发起新的对话"));

        assertThatThrownBy(() -> service.streamChat("问题", CONVERSATION_ID, new SseEmitter()))
                .isInstanceOf(ClientException.class);

        // 被拒的请求不该留下会话行与任务登记，否则闸门反倒制造了脏数据
        verifyNoInteractions(conversationService);
        verifyNoInteractions(taskManager);
        verify(agent, never()).streamEvents(anyString(), any(RuntimeContext.class));
    }

    @Test
    void shouldCancelUpstreamWhenEmitterTimesOut() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<String> taskId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, emitter);
        verify(taskManager).register(taskId.capture(), anyString(), any());
        verify(emitter, atLeastOnce()).onTimeout(callbacks.capture());
        callbacks.getAllValues().forEach(Runnable::run);

        // 超时只关响应不回收上游，ReAct 会在无人消费的情况下跑满迭代上限
        verify(taskManager).cancel(taskId.getValue());
    }

    @Test
    void shouldCancelUpstreamWhenEmitterErrors() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<String> taskId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Consumer<Throwable>> callbacks = ArgumentCaptor.forClass(Consumer.class);

        service.streamChat("问题", CONVERSATION_ID, emitter);
        verify(taskManager).register(taskId.capture(), anyString(), any());
        verify(emitter, atLeastOnce()).onError(callbacks.capture());
        callbacks.getAllValues().forEach(callback -> callback.accept(new IOException("客户端断开")));

        verify(taskManager).cancel(taskId.getValue());
    }

    @Test
    void shouldCancelUpstreamWhenEmitterClosesWithoutSettling() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<String> taskId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, emitter);
        verify(taskManager).register(taskId.capture(), anyString(), any());
        verify(emitter, atLeastOnce()).onCompletion(callbacks.capture());
        callbacks.getAllValues().forEach(Runnable::run);

        // 关页导致写失败时容器不报超时也不报错，只有 completion 回调兜得住
        verify(taskManager).cancel(taskId.getValue());
    }

    @Test
    void shouldNotCancelAfterRunAlreadySettled() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());
        SseEmitter emitter = mock(SseEmitter.class);
        ArgumentCaptor<Runnable> callbacks = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("问题", CONVERSATION_ID, emitter);
        verify(emitter, atLeastOnce()).onCompletion(callbacks.capture());
        callbacks.getAllValues().forEach(Runnable::run);

        // 正常完成也会触发 completion 回调，这里再取消等于每个请求都往 Redis 写一条 30 分钟死标记
        verify(taskManager, never()).cancel(anyString());
    }

    @Test
    void shouldPersistSuccessfulAgentRequestAsOneTraceRun() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());

        service.streamChat("查询我的年假", CONVERSATION_ID, new SseEmitter());

        ArgumentCaptor<RagTraceRunDO> run = ArgumentCaptor.forClass(RagTraceRunDO.class);
        verify(traceAuditService).startRun(run.capture());
        assertThat(run.getValue().getTenantId()).isEqualTo("default");
        assertThat(run.getValue().getConversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(run.getValue().getExtraData()).contains("查询我的年假");
        verify(traceAuditService).finishRun(eq(run.getValue().getTraceId()), eq("SUCCESS"),
                org.mockito.ArgumentMatchers.isNull(), any(), anyLong());
        verify(streamTraceSupport).beginStreamNode("react-agent:default", "REACT");
        verify(executionSpan).finishSuccess(org.mockito.ArgumentMatchers.contains("开放式智能体"));
        verify(executionSpan).detach();
    }

    @Test
    void shouldFinishTraceAsErrorWhenAgentStreamFails() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class)))
                .thenReturn(Flux.error(new IllegalStateException("模型流失败")));

        service.streamChat("查询我的年假", CONVERSATION_ID, new SseEmitter());

        ArgumentCaptor<RagTraceRunDO> run = ArgumentCaptor.forClass(RagTraceRunDO.class);
        verify(traceAuditService).startRun(run.capture());
        verify(traceAuditService).finishRun(eq(run.getValue().getTraceId()), eq("ERROR"),
                contains("模型流失败"), any(), anyLong());
        verify(executionSpan).finishError(any(Throwable.class));
    }

    @Test
    void shouldFinishTraceAsCancelledWhenTaskIsCancelled() {
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.never());
        ArgumentCaptor<Runnable> finalizer = ArgumentCaptor.forClass(Runnable.class);

        service.streamChat("查询我的年假", CONVERSATION_ID, new SseEmitter());
        verify(taskManager).register(anyString(), anyString(), finalizer.capture());
        finalizer.getValue().run();

        ArgumentCaptor<RagTraceRunDO> run = ArgumentCaptor.forClass(RagTraceRunDO.class);
        verify(traceAuditService).startRun(run.capture());
        verify(traceAuditService).finishRun(eq(run.getValue().getTraceId()), eq("CANCELLED"),
                org.mockito.ArgumentMatchers.isNull(), any(), anyLong());
        verify(executionSpan).finishCancelledIfRunning();
    }

    @Test
    void shouldNotStartReactWhenPolicyDeniesRequest() {
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "leave-write", "高风险写操作", IntentKind.MCP,
                RouteResult.of(RouteDecision.DENY, null, "高风险操作未授权", 0.99)));

        service.streamChat("替别人提交请假", CONVERSATION_ID, new SseEmitter());

        verify(agentProvider, never()).getAgent();
        verify(agentProvider, never()).getWorkflowAgent(anyString());
        verify(conversationService).addAssistantMessage(eq(CONVERSATION_ID), eq(USER_ID),
                contains("未通过权限策略"), eq(""), any(), eq("m-3003"), eq(com.myagent.agent.enums.AgentMessageStatus.NORMAL));
        assertThat(gateReleased.get()).isOne();
    }

    @Test
    void shouldUseDedicatedWorkflowAgentInsteadOfOpenReactAgent() {
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "leave-approval", "请假办理", IntentKind.WORKFLOW,
                RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "高置信固定流程", 0.95)));
        when(agentProvider.getWorkflowAgent("leave-approval")).thenReturn(new ActiveAgent(
                agent, new ResolvedCatalog("知识库工具描述", List.of(), List.of())));
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());

        service.streamChat("我要请年假", CONVERSATION_ID, new SseEmitter());

        verify(agentProvider).getWorkflowAgent("leave-approval");
        verify(agentProvider, never()).getAgent();
        verify(streamTraceSupport).beginStreamNode("workflow-agent:leave-approval", "WORKFLOW");
        verify(executionSpan).finishSuccess(org.mockito.ArgumentMatchers.contains("固定流程"));
        verify(continuationResolver).synchronizeAfterWorkflowTurn(
                "default", USER_ID, CONVERSATION_ID);
    }

    @Test
    void shouldResumeActiveWorkflowForShortSlotAnswerBeforeClassifyingANewIntent() {
        WorkflowContinuation continuation = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);
        when(continuationResolver.resolve("default", USER_ID, CONVERSATION_ID))
                .thenReturn(Optional.of(continuation));
        when(routePlanner.plan(eq("旅游"), eq(continuation), any())).thenReturn(new ChatRoutePlan(
                "leave-approval", "进行中的业务流程", IntentKind.WORKFLOW,
                RouteResult.of(RouteDecision.WORKFLOW, "leave-approval", "继续当前工作流", 1D)));
        when(agentProvider.getWorkflowAgent("leave-approval")).thenReturn(new ActiveAgent(
                agent, new ResolvedCatalog("固定流程", List.of(), List.of())));
        when(agent.streamEvents(anyString(), any(RuntimeContext.class))).thenReturn(Flux.empty());

        service.streamChat("旅游", CONVERSATION_ID, new SseEmitter());

        verify(routePlanner).plan(eq("旅游"), eq(continuation), any());
        verify(agentProvider).getWorkflowAgent("leave-approval");
    }

    @Test
    void shouldAnswerKnowledgeIntentThroughRagWithoutStartingAnyAgent() {
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "leave-policy", "请假制度", IntentKind.KB,
                RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.91)));
        when(conversationService.loadRecentTurns(CONVERSATION_ID, USER_ID, 2)).thenReturn(List.of());
        when(knowledgeSearchFacade.searchWithEvidence("年假制度是什么", List.of()))
                .thenReturn(new KnowledgeSearchFacade.KnowledgeAnswer("SUCCESS", "年假制度答案", List.of()));

        service.streamChat("年假制度是什么", CONVERSATION_ID, new SseEmitter());

        verify(knowledgeSearchFacade, timeout(2_000)).searchWithEvidence("年假制度是什么", List.of());
        verify(conversationService, timeout(2_000)).addAssistantMessage(eq(CONVERSATION_ID), eq(USER_ID),
                eq("年假制度答案"), eq(""), any(), eq("m-3003"), eq(com.myagent.agent.enums.AgentMessageStatus.NORMAL));
        verify(agentProvider, never()).getAgent();
        verify(agentProvider, never()).getWorkflowAgent(anyString());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void shouldExposeOnlyKnowledgeSourceSummaryInsteadOfRawChunkExcerpts() throws Exception {
        String privateExcerpt = "这是仅供模型生成答案使用的完整知识分片，不应直接展示给用户";
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "leave-policy", "请假制度", IntentKind.KB,
                RouteResult.of(RouteDecision.SYSTEM, null, "知识库回答", 0.91)));
        when(conversationService.loadRecentTurns(CONVERSATION_ID, USER_ID, 2)).thenReturn(List.of());
        when(knowledgeSearchFacade.searchWithEvidence("年假制度是什么", List.of()))
                .thenReturn(new KnowledgeSearchFacade.KnowledgeAnswer("SUCCESS", "年假制度答案", List.of(
                        new KnowledgeSearchFacade.Evidence(
                                "doc-1", "人事制度.md", "chunk-7", 7, privateExcerpt,
                                "2026.1", java.time.LocalDate.of(2026, 1, 1)))));

        service.streamChat("年假制度是什么", CONVERSATION_ID, new SseEmitter());

        ArgumentCaptor<List> blocks = ArgumentCaptor.forClass(List.class);
        verify(conversationService, timeout(2_000)).addAssistantMessage(
                eq(CONVERSATION_ID), eq(USER_ID), eq("年假制度答案"), eq(""),
                blocks.capture(), eq("m-3003"), eq(com.myagent.agent.enums.AgentMessageStatus.NORMAL));
        String persistedTimeline = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString((List<AgentBlock>) blocks.getValue());
        assertThat(persistedTimeline)
                .contains("人事制度.md", "2026.1")
                .doesNotContain(privateExcerpt, "chunk-7");
    }

    @Test
    void shouldStillFinishStreamWhenDirectAnswerPersistenceFails() throws IOException {
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "greeting", "问候", IntentKind.SYSTEM,
                RouteResult.of(RouteDecision.SYSTEM, null, "系统回答", 0.99)));
        when(conversationService.addAssistantMessage(anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), any())).thenThrow(new IllegalStateException("消息库不可用"));

        SseEmitter emitter = mock(SseEmitter.class);
        service.streamChat("你好", CONVERSATION_ID, emitter);

        assertThat(gateReleased.get()).isOne();
        verify(taskManager).unregister(anyString());
        // meta + route + message + finish + done；落库失败不能让前端停在生成中。
        verify(emitter, times(5)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldDescribeConnectedCapabilitiesForAboutBotIntent() {
        when(routePlanner.plan(anyString(), anyBoolean(), any())).thenReturn(new ChatRoutePlan(
                "sys-about-bot", "关于助手", IntentKind.SYSTEM,
                RouteResult.of(RouteDecision.SYSTEM, null, "系统回答", 0.99)));
        when(capabilityService.describe(any())).thenReturn("当前可用：我的年假余额（get_leave_balance）");

        service.streamChat("你都有什么工具可用", CONVERSATION_ID, new SseEmitter());

        verify(capabilityService).describe(any());
        verify(conversationService).addAssistantMessage(eq(CONVERSATION_ID), eq(USER_ID),
                contains("get_leave_balance"), eq(""), any(), eq("m-3003"),
                eq(com.myagent.agent.enums.AgentMessageStatus.NORMAL));
    }

    @Test
    void shouldAnswerModificationFollowUpUsingRecentlyCompletedLeaveContext() {
        RecentWorkflowContext completedLeave = new RecentWorkflowContext(
                "leave-approval", WorkflowConversationStatus.COMPLETED);
        when(workflowStateService.findRecentContext("default", USER_ID, CONVERSATION_ID))
                .thenReturn(Optional.of(completedLeave));
        when(routePlanner.plan(eq("能修改吗"), eq(completedLeave), any())).thenReturn(new ChatRoutePlan(
                "sys-leave-modification", "请假修改说明", IntentKind.SYSTEM,
                RouteResult.of(RouteDecision.SYSTEM, null, "关联最近完成的请假流程", 1D)));

        service.streamChat("能修改吗", CONVERSATION_ID, new SseEmitter());

        verify(routePlanner).plan(eq("能修改吗"), eq(completedLeave), any());
        verify(conversationService).addAssistantMessage(eq(CONVERSATION_ID), eq(USER_ID),
                contains("撤回"), eq(""), any(), eq("m-3003"),
                eq(com.myagent.agent.enums.AgentMessageStatus.NORMAL));
    }
}
