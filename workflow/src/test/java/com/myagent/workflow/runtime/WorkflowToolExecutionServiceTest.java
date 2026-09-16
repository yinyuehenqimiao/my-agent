package com.myagent.workflow.runtime;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkflowToolExecutionServiceTest {
    @Test void writeToolConsumesConfirmationAndInjectsStableIdentityAndIdempotencyKey() {
        ToolGateway gateway = mock(ToolGateway.class); WorkflowConfirmationService confirmations = mock(WorkflowConfirmationService.class);
        when(gateway.listDefinitions()).thenReturn(List.of(ToolDefinition.builder().name("create_leave").readOnly(false).enabled(true).build()));
        when(gateway.invoke(any())).thenReturn(ToolResult.success(Map.of("id", "L1"), "t1"));
        var identity = LoginUser.builder().tenantId("tenant-a").userId("u1").username("u1").build();
        var command = new WorkflowToolExecutionService.Command("i1","write",1,"create_leave",Map.of("reason","旅游"),"a1",1,"c1","t1");

        new WorkflowToolExecutionService(gateway, confirmations).execute(identity, command);

        verify(confirmations).consumeConfirmed(identity,"a1",1,command.input());
        verify(gateway).invoke(argThat(call -> call.getIdentity() == identity && "tenant-a:i1:write:1".equals(call.getIdempotencyKey()) && call.getSource() == ToolDefinition.Source.WORKFLOW));
        verify(confirmations).finish(eq("a1"),eq("COMPLETED"),any());
    }

    @Test void writeToolWithoutConfirmationIsRejectedBeforeGateway() {
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.listDefinitions()).thenReturn(List.of(ToolDefinition.builder().name("write").readOnly(false).build()));
        var service = new WorkflowToolExecutionService(gateway,mock(WorkflowConfirmationService.class));
        var user = LoginUser.builder().tenantId("t").userId("u").build();
        assertThrows(ClientException.class, () -> service.execute(user,new WorkflowToolExecutionService.Command("i","n",1,"write",Map.of(),null,0,null,null)));
        verify(gateway,never()).invoke(any());
    }
}
