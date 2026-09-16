package com.myagent.framework.workflow;

import com.myagent.framework.context.ExecutionIdentity;
import java.util.List;

public interface WorkflowKnowledgePort {
    Answer answer(ExecutionIdentity identity, String question);
    record Answer(String status, String summary, List<Evidence> sources) { }
    record Evidence(String documentId, String documentName, String chunkId, String excerpt, String version) { }
}
