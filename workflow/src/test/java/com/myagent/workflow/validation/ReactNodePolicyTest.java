package com.myagent.workflow.validation;

import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolGateway;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReactNodePolicyTest {
    @Test void permitsOnlyReadOnlyToolsWithinBudget() {
        ToolGateway gateway = mock(ToolGateway.class);
        when(gateway.listDefinitions()).thenReturn(List.of(
                ToolDefinition.builder().name("balance").readOnly(true).enabled(true).build(),
                ToolDefinition.builder().name("create_leave").readOnly(false).enabled(true).build()));
        ReactNodePolicy policy = new ReactNodePolicy(gateway);
        assertDoesNotThrow(() -> policy.validate(new ReactNodePolicy.Config(List.of("balance"),5,8,60)));
        assertThrows(ClientException.class, () -> policy.validate(new ReactNodePolicy.Config(List.of("create_leave"),5,8,60)));
        assertThrows(ClientException.class, () -> policy.validate(new ReactNodePolicy.Config(List.of("balance"),6,8,60)));
    }
}
