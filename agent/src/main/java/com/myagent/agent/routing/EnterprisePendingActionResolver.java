package com.myagent.agent.routing;

import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.enterprise.service.ExternalErpActionService;
import com.myagent.framework.context.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 只从当前用户当前会话读取待确认动作，不接受客户端传入身份。 */
@Component
@RequiredArgsConstructor
public class EnterprisePendingActionResolver {

    private final EnterpriseService enterpriseService;
    private final ExternalErpActionService externalErpActions;

    @Value("${myagent.enterprise.enabled:false}")
    private boolean enterpriseEnabled;
    @Value("${myagent.enterprise.adapter:LOCAL}")
    private String adapterMode;

    public boolean hasPendingAction(String userId, String conversationId) {
        if (!enterpriseEnabled || conversationId == null || conversationId.isBlank()) {
            return false;
        }
        if ("HTTP_ERP".equalsIgnoreCase(adapterMode)) {
            var identity = UserContext.get();
            return identity != null && userId.equals(identity.getUserId())
                    && externalErpActions.actions(identity, conversationId).stream().anyMatch(action -> "PENDING".equals(action.status()));
        }
        return enterpriseService.actions(userId, conversationId).stream()
                .anyMatch(action -> "PENDING".equals(action.status()));
    }
}
