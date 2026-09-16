package com.myagent.workflow.runtime;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.workflow.WorkflowKnowledgePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowKnowledgeService {
    private final WorkflowKnowledgePort knowledge;
    public WorkflowKnowledgePort.Answer answer(ExecutionIdentity identity, String question) {
        return knowledge.answer(identity, question);
    }
}
