package com.myagent.agent.routing;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationWorkflowStateServiceTest {

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:workflow-route-state;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS t_agent_route_state");
        jdbc.execute("""
                CREATE TABLE t_agent_route_state (
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    conversation_id VARCHAR(64) NOT NULL,
                    pipeline_type VARCHAR(16) NOT NULL,
                    pipeline_target VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    expected_input VARCHAR(64),
                    action_id VARCHAR(64),
                    workflow_instance_id VARCHAR(64),
                    version BIGINT NOT NULL DEFAULT 0,
                    expires_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (tenant_id, user_id, conversation_id)
                )
                """);
    }

    @Test
    void activeWorkflowSurvivesServiceRecreationAndStopsContinuingAfterCompletion() {
        ConversationWorkflowStateService first = new ConversationWorkflowStateService(jdbc, Duration.ofMinutes(30));
        first.activate("tenant-a", "user-1", "conversation-1", "leave-approval");

        ConversationWorkflowStateService afterRestart = new ConversationWorkflowStateService(jdbc, Duration.ofMinutes(30));
        assertThat(afterRestart.findContinuation("tenant-a", "user-1", "conversation-1"))
                .contains(new WorkflowContinuation(
                        "leave-approval", WorkflowConversationStatus.COLLECTING_PARAMETERS));

        afterRestart.complete("tenant-a", "user-1", "conversation-1");
        assertThat(afterRestart.findContinuation("tenant-a", "user-1", "conversation-1")).isEmpty();
        assertThat(afterRestart.findRecentContext("tenant-a", "user-1", "conversation-1"))
                .contains(new RecentWorkflowContext(
                        "leave-approval", WorkflowConversationStatus.COMPLETED));
    }
}
