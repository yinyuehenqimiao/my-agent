package com.myagent.agent.routing;

import com.myagent.enterprise.dto.EnterpriseTypes.Action;
import com.myagent.enterprise.service.EnterpriseService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EnterprisePendingActionResolverTest {

    private final EnterpriseService enterpriseService = mock(EnterpriseService.class);
    private final EnterprisePendingActionResolver resolver = new EnterprisePendingActionResolver(enterpriseService);

    @Test
    void shouldUseOnlyCurrentUsersCurrentConversationPendingActions() {
        ReflectionTestUtils.setField(resolver, "enterpriseEnabled", true);
        when(enterpriseService.actions("u-1", "c-1")).thenReturn(List.of(
                new Action("a-1", "u-1", "c-1", "SUBMIT", "r-1", "{}", "PENDING",
                        null, LocalDateTime.now().plusMinutes(5), LocalDateTime.now(), "k-1")));

        assertThat(resolver.hasPendingAction("u-1", "c-1")).isTrue();
    }

    @Test
    void shouldNotTouchEnterpriseTablesWhenFeatureIsDisabled() {
        ReflectionTestUtils.setField(resolver, "enterpriseEnabled", false);

        assertThat(resolver.hasPendingAction("u-1", "c-1")).isFalse();
        verifyNoInteractions(enterpriseService);
    }
}
