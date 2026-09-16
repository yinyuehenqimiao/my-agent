package com.myagent.rag.core.mcp;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;

/** 维护跨实例可见的租户工具目录版本。数据库是权威来源，Redis 只负责加速通知。 */
@Slf4j
@Service
public class McpCatalogRevisionService {
    static final String TOPIC = "myagent:mcp-catalog-changed";

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final ObjectProvider<RedissonClient> redissonProvider;

    public McpCatalogRevisionService(JdbcTemplate jdbc, ApplicationEventPublisher events,
                                     ObjectProvider<RedissonClient> redissonProvider) {
        this.jdbc = jdbc;
        this.events = events;
        this.redissonProvider = redissonProvider;
    }

    public long current(String tenantId) {
        try {
            Long value = jdbc.queryForObject(
                    "SELECT revision FROM mcp_catalog_revision WHERE tenant_id=?", Long.class, tenantId);
            return value == null ? 0L : value;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    public long bump(String tenantId) {
        Timestamp now = Timestamp.from(Instant.now());
        int changed = jdbc.update(
                "UPDATE mcp_catalog_revision SET revision=revision+1,updated_at=? WHERE tenant_id=?", now, tenantId);
        if (changed == 0) {
            jdbc.update("INSERT INTO mcp_catalog_revision(tenant_id,revision,updated_at) VALUES(?,1,?)", tenantId, now);
        }
        long revision = current(tenantId);
        Runnable publish = () -> publishChanged(tenantId, revision);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
        return revision;
    }

    private void publishChanged(String tenantId, long revision) {
        events.publishEvent(new McpCatalogChangedEvent(tenantId, revision));
        RedissonClient redisson = redissonProvider.getIfAvailable();
        if (redisson != null) {
            try {
                redisson.getTopic(TOPIC).publish(tenantId + ":" + revision);
            } catch (RuntimeException ex) {
                log.warn("MCP 目录 Redis 通知失败，运行时仍会读取数据库版本 tenant={}", tenantId, ex);
            }
        }
    }
}
