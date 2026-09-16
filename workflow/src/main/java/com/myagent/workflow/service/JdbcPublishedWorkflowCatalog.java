package com.myagent.workflow.service;

import com.myagent.framework.workflow.PublishedWorkflowCatalog;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JdbcPublishedWorkflowCatalog implements PublishedWorkflowCatalog {
    private final JdbcTemplate jdbc;
    @Override public boolean isPublished(String definitionKey) {
        if (definitionKey == null || definitionKey.isBlank()) return false;
        Integer count = jdbc.queryForObject("SELECT count(*) FROM wf_definition d JOIN wf_version v ON v.id=d.current_published_version_id WHERE d.def_key=? AND v.status='PUBLISHED'", Integer.class, definitionKey);
        return count != null && count > 0;
    }
}
