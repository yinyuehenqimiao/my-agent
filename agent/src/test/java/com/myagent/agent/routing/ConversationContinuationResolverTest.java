package com.myagent.agent.routing;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationContinuationResolverTest {

    private final ConversationWorkflowStateService stateService = mock(ConversationWorkflowStateService.class);
    private final EnterprisePendingActionResolver pendingActionResolver = mock(EnterprisePendingActionResolver.class);
    private final ConversationContinuationResolver resolver = new ConversationContinuationResolver(
            stateService, pendingActionResolver, "leave-approval");

    @Test
    void collectingWorkflowContinuesEvenBeforeAConfirmationActionExists() {
        WorkflowContinuation collecting = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);
        when(stateService.findContinuation("tenant-a", "user-1", "conversation-1"))
                .thenReturn(Optional.of(collecting));
        when(pendingActionResolver.hasPendingAction("user-1", "conversation-1")).thenReturn(false);

        assertThat(resolver.resolve("tenant-a", "user-1", "conversation-1")).contains(collecting);

        verify(pendingActionResolver).hasPendingAction("user-1", "conversation-1");
    }

    @Test
    void confirmationStateStopsContinuingAfterTheActionHasBeenCompletedElsewhere() {
        WorkflowContinuation awaiting = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.AWAITING_CONFIRMATION);
        when(stateService.findContinuation("tenant-a", "user-1", "conversation-1"))
                .thenReturn(Optional.of(awaiting));
        when(pendingActionResolver.hasPendingAction("user-1", "conversation-1")).thenReturn(false);

        assertThat(resolver.resolve("tenant-a", "user-1", "conversation-1")).isEmpty();

        verify(stateService).complete("tenant-a", "user-1", "conversation-1");
    }

    @Test
    void generatedConfirmationActionMovesCollectingWorkflowToAwaitingConfirmation() {
        WorkflowContinuation collecting = new WorkflowContinuation(
                "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS);
        when(stateService.findContinuation("tenant-a", "user-1", "conversation-1"))
                .thenReturn(Optional.of(collecting));
        when(pendingActionResolver.hasPendingAction("user-1", "conversation-1")).thenReturn(true);

        assertThat(resolver.resolve("tenant-a", "user-1", "conversation-1"))
                .contains(new WorkflowContinuation(
                        "leave-approval", WorkflowConversationStatus.AWAITING_CONFIRMATION));

        verify(stateService).markAwaitingConfirmation("tenant-a", "user-1", "conversation-1");
    }

    @Test
    void existingPendingActionContinuesWorkflowWhenNoConversationStateWasRecorded() {
        when(stateService.findContinuation("tenant-a", "user-1", "conversation-1"))
                .thenReturn(Optional.empty());
        when(pendingActionResolver.hasPendingAction("user-1", "conversation-1")).thenReturn(true);

        assertThat(resolver.resolve("tenant-a", "user-1", "conversation-1"))
                .contains(new WorkflowContinuation(
                        "leave-approval", WorkflowConversationStatus.AWAITING_CONFIRMATION));
    }

    @Test
    void completedTurnMovesCollectingWorkflowToAwaitingWhenItCreatedAConfirmationAction() {
        when(stateService.findContinuation("tenant-a", "user-1", "conversation-1"))
                .thenReturn(Optional.of(new WorkflowContinuation(
                        "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS)));
        when(pendingActionResolver.hasPendingAction("user-1", "conversation-1")).thenReturn(true);

        resolver.synchronizeAfterWorkflowTurn("tenant-a", "user-1", "conversation-1");

        verify(stateService).markAwaitingConfirmation("tenant-a", "user-1", "conversation-1");
    }
}
