package com.myagent.workflow.runtime;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DynamicWorkflowRuntimeServiceTest {
    private JdbcTemplate jdbc;
    private WorkflowRuntimeService engine;
    private DynamicWorkflowRuntimeService service;
    private LoginUser user;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:dynamic-runtime;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource); jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE wf_definition(id BIGINT PRIMARY KEY,def_key VARCHAR,tenant_id VARCHAR,current_published_version_id BIGINT)");
        jdbc.execute("CREATE TABLE wf_version(id BIGINT PRIMARY KEY,definition_id BIGINT,status VARCHAR)");
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.9.0/04_workflow_execution_state.sql")).execute(dataSource);
        jdbc.update("INSERT INTO wf_definition VALUES(1,'demo','tenant-a',10)");
        jdbc.update("INSERT INTO wf_version VALUES(10,1,'PUBLISHED')");
        engine = mock(WorkflowRuntimeService.class); when(engine.startInstance(eq("demo"), eq(10L), eq("u1"), any(), any(), any())).thenReturn("flowable-1");
        TaskService taskService = mock(TaskService.class); TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query); when(query.processInstanceId(any())).thenReturn(query); when(query.active()).thenReturn(query); when(query.list()).thenReturn(List.of());
        service = new DynamicWorkflowRuntimeService(jdbc, engine, mock(RuntimeService.class), taskService);
        user = LoginUser.builder().tenantId("tenant-a").userId("u1").username("u1").build();
    }

    @Test
    void duplicateStartReturnsSameInstanceAndStartsEngineOnce() {
        var command = new DynamicWorkflowRuntimeService.StartCommand("demo", "c1", null, "request-1", Map.of("tenantId", "forged"));
        var first = service.start(user, command); var second = service.start(user, command);
        assertEquals(first.id(), second.id());
        verify(engine, times(1)).startInstance(eq("demo"), eq(10L), eq("u1"), eq(first.id()), any(), argThat(vars -> "tenant-a".equals(vars.get("tenantId"))));
    }

    @Test
    void anotherUserCannotReadInstance() {
        var created = service.start(user, new DynamicWorkflowRuntimeService.StartCommand("demo", "c1", null, "request-2", Map.of()));
        var other = LoginUser.builder().tenantId("tenant-a").userId("u2").username("u2").build();
        assertThrows(ClientException.class, () -> service.get(other, created.id()));
    }
}
