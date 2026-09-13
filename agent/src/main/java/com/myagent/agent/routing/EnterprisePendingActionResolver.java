package com.myagent.agent.routing;

import com.myagent.enterprise.service.EnterpriseService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 只从当前用户当前会话读取待确认动作，不接受客户端传入身份。 */
@Component
@RequiredArgsConstructor
public class EnterprisePendingActionResolver {

    private final EnterpriseService enterpriseService;

    @Value("${myagent.enterprise.enabled:false}")
    private boolean enterpriseEnabled;

    public boolean hasPendingAction(String userId, String conversationId) {
        if (!enterpriseEnabled || conversationId == null || conversationId.isBlank()) {
            return false;
        }
        return enterpriseService.actions(userId, conversationId).stream()
                .anyMatch(action -> "PENDING".equals(action.status()));
    }
}
