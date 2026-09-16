package com.myagent.rag.trace;

import com.myagent.framework.trace.RagTraceContext;
import com.myagent.rag.core.intent.IntentNode;
import com.myagent.rag.core.intent.NodeScore;
import com.myagent.rag.dto.SubQuestionIntent;
import com.myagent.rag.enums.IntentKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceResultSummarizerTest {

    @AfterEach
    void clearContext() {
        RagTraceContext.clear();
    }

    @Test
    void summarizesRouteLikeObjectsForReplay() {
        String extra = TraceResultSummarizer.toExtraData(
                Map.of("decision", "WORKFLOW", "target", "leave-approval", "intentCode", "leave"),
                null, 500);
        assertThat(extra).contains("resultSummary");
        assertThat(TraceResultSummarizer.extractSummary(extra))
                .contains("固定流程")
                .contains("leave");
    }

    @Test
    void summarizesIntentScoreCollectionInChinese() {
        IntentNode node = IntentNode.builder().id("leave-approval").name("请假审批").kind(IntentKind.WORKFLOW).build();
        String extra = TraceResultSummarizer.toExtraData(
                List.of(NodeScore.builder().node(node).score(0.91).build()),
                null, 800);
        assertThat(TraceResultSummarizer.extractSummary(extra))
                .contains("意图候选")
                .contains("请假审批")
                .contains("固定流程");
    }

    @Test
    void summarizesRawIntentJsonFromProvider() {
        String raw = "[{\"id\":\"group-hr-policy\",\"score\":0.95,\"reason\":\"对应人事制度分类\"}]";
        String extra = TraceResultSummarizer.toExtraData(raw, null, 800, "LLM_PROVIDER");
        assertThat(TraceResultSummarizer.extractSummary(extra))
                .contains("意图打分")
                .contains("group-hr-policy")
                .contains("0.95");
    }

    @Test
    void summarizesSelectedModelForRoutingLayer() {
        RagTraceContext.putAttr("llm.selectedProvider", "siliconflow");
        RagTraceContext.putAttr("llm.selectedModelId", "Qwen/Qwen2.5-7B");
        String extra = TraceResultSummarizer.toExtraData(
                "[{\"id\":\"x\",\"score\":1}]", null, 500, "LLM_ROUTING");
        assertThat(TraceResultSummarizer.extractSummary(extra))
                .contains("已选用模型")
                .contains("siliconflow")
                .contains("Qwen/Qwen2.5-7B");
    }

    @Test
    void summarizesSubQuestionIntents() {
        IntentNode node = IntentNode.builder().id("kb-1").name("转正制度").kind(IntentKind.KB).build();
        String extra = TraceResultSummarizer.toExtraData(
                List.of(new SubQuestionIntent("公司转正流程", List.of(NodeScore.builder().node(node).score(0.88).build()))),
                null, 800);
        assertThat(TraceResultSummarizer.extractSummary(extra))
                .contains("子问题意图")
                .contains("转正制度")
                .contains("知识库问答");
    }

    @Test
    void prefersErrorWhenPresent() {
        String extra = TraceResultSummarizer.toExtraData(null, "boom", 200);
        assertThat(TraceResultSummarizer.extractSummary(extra)).isEqualTo("boom");
    }
}
