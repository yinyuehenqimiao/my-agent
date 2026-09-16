package com.myagent.workflow.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowInteractionServiceTest {
    private WorkflowConfirmationService confirmations;
    private LoginUser user;

    @BeforeEach void setUp() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:confirmation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds); jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE workflow_action(id VARCHAR PRIMARY KEY,instance_id VARCHAR,user_id VARCHAR,action_type VARCHAR,payload CLOB,status VARCHAR,result CLOB,expires_at TIMESTAMP,created_at TIMESTAMP,dedup_key VARCHAR)");
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.9.0/05_workflow_confirmation.sql")).execute(ds);
        confirmations = new WorkflowConfirmationService(jdbc, new ObjectMapper());
        user = LoginUser.builder().tenantId("tenant-a").userId("u1").username("u1").build();
    }

    @Test void sideQuestionDoesNotLookLikeFieldInput() {
        var classifier = new WorkflowMessageClassifier();
        assertEquals(WorkflowMessageClassifier.MessageKind.SIDE_QUESTION, classifier.classify("公司的请假制度是什么？"));
        assertEquals(WorkflowMessageClassifier.MessageKind.SIDE_QUESTION, classifier.classify("我的年假还有多少"));
        assertEquals(WorkflowMessageClassifier.MessageKind.FIELD_INPUT, classifier.classify("旅游"));
    }

    @Test void confirmationIsBoundToParametersAndCanOnlySucceedOnce() {
        var action = confirmations.create(user, "i1", "write", "create_leave", "v1", Map.of("reason", "旅游"), Instant.now().plusSeconds(60));
        assertThrows(ClientException.class, () -> confirmations.confirm(user, action.id(), 0, Map.of("reason", "探亲")));
        var confirmed = confirmations.confirm(user, action.id(), 0, Map.of("reason", "旅游"));
        assertEquals("CONFIRMED", confirmed.status());
        assertThrows(ClientException.class, () -> confirmations.confirm(user, action.id(), 0, Map.of("reason", "旅游")));
    }
}
