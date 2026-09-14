package com.myagent.rag.trace;

import com.myagent.framework.context.LoginUser;
import com.myagent.rag.config.RagTraceProperties;
import com.myagent.rag.dao.entity.RagTraceNodeDO;
import com.myagent.rag.dao.entity.RagTraceRunDO;
import com.myagent.rag.controller.request.RagTraceRunPageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class RagTraceAuditServiceTest {

    private RagTraceAuditService service;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:trace-audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE t_rag_trace_run(id VARCHAR PRIMARY KEY,trace_id VARCHAR UNIQUE,tenant_id VARCHAR," +
                "trace_name VARCHAR,entry_method VARCHAR,conversation_id VARCHAR,task_id VARCHAR,user_id VARCHAR," +
                "status VARCHAR,error_message VARCHAR,start_time TIMESTAMP,end_time TIMESTAMP,duration_ms BIGINT," +
                "extra_data VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP,deleted SMALLINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE t_rag_trace_node(id VARCHAR PRIMARY KEY,trace_id VARCHAR,node_id VARCHAR," +
                "parent_node_id VARCHAR,depth INT,node_type VARCHAR,node_name VARCHAR,class_name VARCHAR," +
                "method_name VARCHAR,status VARCHAR,error_message VARCHAR,start_time TIMESTAMP,end_time TIMESTAMP," +
                "duration_ms BIGINT,extra_data VARCHAR,create_time TIMESTAMP,update_time TIMESTAMP,deleted SMALLINT DEFAULT 0," +
                "UNIQUE(trace_id,node_id))");
        RagTraceProperties properties = new RagTraceProperties();
        properties.setMaxErrorLength(12);
        service = new RagTraceAuditService(jdbc, properties);
    }

    @Test
    void recordsCompleteTimelineAndKeepsDetailTenantScoped() {
        Date start = new Date();
        service.startRun(RagTraceRunDO.builder()
                .id("run-1").traceId("trace-1").tenantId("tenant-a").userId("employee-a")
                .traceName("agent-chat").entryMethod("AgentChatService#streamChat")
                .conversationId("conversation-1").taskId("task-1").status("RUNNING").startTime(start).build());
        service.startNode(RagTraceNodeDO.builder()
                .id("row-1").traceId("trace-1").nodeId("node-1").depth(0)
                .nodeType("INTENT").nodeName("intent-classify").status("RUNNING").startTime(start).build());
        service.finishNode("trace-1", "node-1", "SUCCESS", null, new Date(), 18L);
        service.finishRun("trace-1", "SUCCESS", null, new Date(), 25L);

        var own = service.detail(identity("tenant-a"), "trace-1");

        assertThat(own).isNotNull();
        assertThat(own.getRun().getStatus()).isEqualTo("SUCCESS");
        assertThat(own.getNodes()).singleElement().satisfies(node -> {
            assertThat(node.getNodeType()).isEqualTo("INTENT");
            assertThat(node.getStatus()).isEqualTo("SUCCESS");
            assertThat(node.getDurationMs()).isEqualTo(18L);
        });
        assertThat(service.detail(identity("tenant-b"), "trace-1")).isNull();
    }

    @Test
    void truncatesPersistedErrorsAtTheAuditBoundary() {
        service.startRun(RagTraceRunDO.builder().id("run-2").traceId("trace-2")
                .tenantId("tenant-a").status("RUNNING").startTime(new Date()).build());

        service.finishRun("trace-2", "ERROR", "0123456789-too-long", new Date(), 3L);

        assertThat(service.detail(identity("tenant-a"), "trace-2").getRun().getErrorMessage())
                .isEqualTo("0123456789-t");
    }

    @Test
    void pagesRunsByCurrentTenantAndSupportedFilters() {
        service.startRun(RagTraceRunDO.builder().id("run-a").traceId("trace-a")
                .tenantId("tenant-a").userId("employee-a").conversationId("conversation-a")
                .status("ERROR").startTime(new Date()).extraData("{\"question\":\"年假余额\"}").build());
        service.startRun(RagTraceRunDO.builder().id("run-b").traceId("trace-b")
                .tenantId("tenant-b").userId("employee-b").conversationId("conversation-b")
                .status("ERROR").startTime(new Date()).extraData("{\"question\":\"他人问题\"}").build());
        RagTraceRunPageRequest request = new RagTraceRunPageRequest();
        request.setCurrent(1);
        request.setSize(20);
        request.setStatus("ERROR");

        var page = service.page(identity("tenant-a"), request);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).singleElement().satisfies(run -> {
            assertThat(run.getTraceId()).isEqualTo("trace-a");
            assertThat(run.getQuestion()).isEqualTo("年假余额");
        });
    }

    private LoginUser identity(String tenantId) {
        return LoginUser.builder().userId("auditor").username("审计员").tenantId(tenantId).build();
    }
}
