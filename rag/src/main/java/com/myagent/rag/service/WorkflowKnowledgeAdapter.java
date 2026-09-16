package com.myagent.rag.service;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.workflow.WorkflowKnowledgePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkflowKnowledgeAdapter implements WorkflowKnowledgePort {
    private final KnowledgeSearchFacade facade;
    @Override public Answer answer(ExecutionIdentity identity, String question) {
        var previous = UserContext.get();
        try {
            UserContext.set(toLoginUser(identity));
            var result = facade.searchWithEvidence(question, java.util.List.of());
            return new Answer(result.status(), result.answer(), result.sources().stream()
                    .map(source -> new Evidence(source.docId(),source.docName(),source.chunkId(),source.excerpt(),source.version())).toList());
        } finally {
            if (previous == null) UserContext.clear(); else UserContext.set(previous);
        }
    }

    private static com.myagent.framework.context.LoginUser toLoginUser(ExecutionIdentity identity) {
        if (identity instanceof com.myagent.framework.context.LoginUser user) return user;
        return com.myagent.framework.context.LoginUser.builder().userId(identity.getUserId()).username(identity.getUsername())
                .role(identity.getRole()).tenantId(identity.getTenantId()).platformRoles(identity.getPlatformRoles())
                .department(identity.getDepartment()).enterpriseRole(identity.getEnterpriseRole()).managerId(identity.getManagerId())
                .hrId(identity.getHrId()).extensions(identity.getExtensions()).build();
    }
}
