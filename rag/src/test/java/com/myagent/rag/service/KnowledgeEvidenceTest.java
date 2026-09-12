package com.myagent.rag.service;

import com.myagent.enterprise.dto.EnterpriseTypes.Policy;
import com.myagent.enterprise.service.KnowledgeAccessService;
import com.myagent.framework.convention.ChatRequest;
import com.myagent.framework.convention.RetrievedChunk;
import com.myagent.framework.exception.ClientException;
import com.myagent.infra.chat.LLMService;
import com.myagent.rag.core.guidance.*;
import com.myagent.rag.core.intent.IntentResolver;
import com.myagent.rag.core.prompt.RAGPromptService;
import com.myagent.rag.core.retrieval.RetrievalEngine;
import com.myagent.rag.core.rewrite.*;
import com.myagent.rag.core.source.CitationContextEnricher;
import com.myagent.rag.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeEvidenceTest {
    final QueryRewriteService rewrite=mock(QueryRewriteService.class);
    final IntentResolver intent=mock(IntentResolver.class);
    final IntentGuidanceService guidance=mock(IntentGuidanceService.class);
    final RetrievalEngine retrieval=mock(RetrievalEngine.class);
    final CitationContextEnricher citations=mock(CitationContextEnricher.class);
    final RAGPromptService prompts=mock(RAGPromptService.class);
    final LLMService llm=mock(LLMService.class);
    final KnowledgeAccessService access=mock(KnowledgeAccessService.class);
    final KnowledgeSearchFacade service=new KnowledgeSearchFacade(rewrite,intent,guidance,retrieval,citations,prompts,llm);
    @BeforeEach void setup(){
        ReflectionTestUtils.setField(service,"enterpriseAccess",access);
        when(access.enabled()).thenReturn(true);
        when(rewrite.rewriteWithSplit(anyString(),anyList())).thenReturn(new RewriteResult("请假制度",List.of("请假制度")));
        when(intent.resolve(any(RewriteResult.class))).thenReturn(List.of(new SubQuestionIntent("请假制度",List.of())));
        when(intent.mergeIntentGroup(anyList())).thenReturn(new IntentGroup(List.of(),List.of()));
        when(guidance.detectAmbiguity(anyString(),anyList())).thenReturn(GuidanceDecision.none());
        when(citations.stripDocIdAnchors(anyString())).thenReturn("年假10天");
        when(access.policy("d1")).thenReturn(new Policy("v1.0",LocalDate.of(2026,9,1)));
        when(llm.chat(any(ChatRequest.class))).thenReturn("年假10天，依据请假制度v1.0");
        context(List.of(chunk("d1","c1")));
    }
    private RetrievedChunk chunk(String doc,String chunk){return RetrievedChunk.builder().id(chunk).docId(doc).docName("请假制度").chunkIndex(0).text("演示年假10天").build();}
    private void context(List<RetrievedChunk> chunks){when(retrieval.retrieve(anyList())).thenReturn(RetrievalContext.builder().kbContext("年假10天").intentChunks(Map.of("kb",chunks)).build());}
    @Test void sourcesAreReturnedFromRetrievalWithPolicyMetadata(){var answer=service.searchWithEvidence("年假几天",List.of());assertEquals("SUCCESS",answer.status());assertEquals("c1",answer.sources().get(0).chunkId());assertEquals("v1.0",answer.sources().get(0).version());verify(access).requireDocument("d1");}
    @Test void duplicateChunksDoNotCreateDuplicateCitations(){context(List.of(chunk("d1","c1"),chunk("d1","c1")));assertEquals(1,service.searchWithEvidence("年假几天",List.of()).sources().size());}
    @Test void noEvidenceDoesNotAskModelToInventAnswer(){context(List.of());assertEquals("NO_EVIDENCE",service.searchWithEvidence("年假几天",List.of()).status());verifyNoInteractions(llm);}
    @Test void revokedDocumentCannotReachAnswerModel(){doThrow(new ClientException("无权访问")).when(access).requireDocument("d1");assertThrows(ClientException.class,()->service.searchWithEvidence("年假几天",List.of()));verifyNoInteractions(llm);}
    @Test void conflictingPolicyVersionsAreExplicit(){context(List.of(chunk("d1","c1"),chunk("d2","c2")));when(access.policy("d2")).thenReturn(new Policy("v2.0",LocalDate.of(2026,9,1)));assertEquals("POLICY_CONFLICT",service.searchWithEvidence("年假几天",List.of()).status());verifyNoInteractions(llm);}
    @Test void futurePolicyIsNotClaimedAsCurrent(){when(access.policy("d1")).thenReturn(new Policy("v2.0",LocalDate.now().plusYears(1)));assertEquals("POLICY_NOT_EFFECTIVE",service.searchWithEvidence("年假几天",List.of()).status());verifyNoInteractions(llm);}
    @Test void oldStringInterfaceRemainsAvailable(){assertEquals("年假10天，依据请假制度v1.0",service.search("年假几天",List.of()));}
}
