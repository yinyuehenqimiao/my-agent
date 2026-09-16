package com.myagent.workflow.runtime;

import com.myagent.framework.exception.ClientException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 阶段 0 运行时入口测试：未发布版本不得启动。 */
class WorkflowRuntimeServiceTest {

    @Test
    void rejectsDefinitionWithoutPublishedVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(eq("SELECT current_published_version_id FROM wf_definition WHERE def_key=?"),
                eq(Long.class), eq("demo"))).thenReturn(null);
        WorkflowRuntimeService service = new WorkflowRuntimeService(
                mock(RepositoryService.class), mock(RuntimeService.class), jdbc);

        assertThrows(ClientException.class,
                () -> service.startCurrentPublishedInstance("demo", "u1", "b1", "t1", null));
    }

    @Test
    void bindsCompiledProcessToImmutableRuntimeVersionKey() {
        String xml = "<definitions><process id=\"wf_demo\" isExecutable=\"true\"><startEvent id=\"s\"/></process></definitions>";
        String bound = WorkflowRuntimeService.bindRuntimeProcessKey(xml, "wf-12-v34");
        assertTrue(bound.contains("process id=\"wf-12-v34\""));
    }
}
