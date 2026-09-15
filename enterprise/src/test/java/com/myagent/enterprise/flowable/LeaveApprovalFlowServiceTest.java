package com.myagent.enterprise.flowable;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.workflow.handler.WorkflowTaskHandlerRegistry;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/** 阶段 4 请假 Flowable 流程测试：两级审批/拒绝/撤回/一致性。 */
@TestPropertySource(properties = {"myagent.enterprise.leave.engine=flowable"})
@SpringJUnitConfig(LeaveApprovalFlowServiceTest.Config.class)
class LeaveApprovalFlowServiceTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean(destroyMethod = "close") DataSource ds() {
            var ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:leaveflow;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,DAY");
            ds.setUsername("sa");
            ds.setPassword("");
            ds.setMaximumPoolSize(4);
            return ds;
        }

        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }

        @Bean EnterpriseMapper mapper(JdbcTemplate jdbc) { return new EnterpriseMapper(jdbc); }

        @Bean WorkflowTaskHandlerRegistry registry() { return new WorkflowTaskHandlerRegistry(); }

        @Bean(name = "workflowTaskDelegate") com.myagent.workflow.handler.WorkflowTaskDelegate workflowTaskDelegate(WorkflowTaskHandlerRegistry r) {
            return new com.myagent.workflow.handler.WorkflowTaskDelegate(r);
        }

        @Bean ProcessEngineConfiguration processEngineConfig(DataSource ds, org.springframework.context.ApplicationContext ac, PlatformTransactionManager tx) {
            SpringProcessEngineConfiguration cfg = new SpringProcessEngineConfiguration();
            cfg.setDataSource(ds);
            cfg.setDatabaseType("h2");
            cfg.setDatabaseSchemaUpdate("create-drop");
            cfg.setAsyncExecutorActivate(false);
            cfg.setApplicationContext(ac);
            cfg.setTransactionManager(tx);
            // 将 Spring bean 注册到 Flowable 表达式解析器
            java.util.Map<Object, Object> beans = new java.util.HashMap<>();
            try { beans.put("workflowTaskDelegate", ac.getBean("workflowTaskDelegate")); } catch (Exception ignore) {}
            cfg.setBeans(beans);
            return cfg;
        }

        @Bean ProcessEngine processEngine(ProcessEngineConfiguration cfg) { return cfg.buildProcessEngine(); }

        @Bean RuntimeService runtimeService(ProcessEngine engine) { return engine.getRuntimeService(); }

        @Bean TaskService taskService(ProcessEngine engine) { return engine.getTaskService(); }

        @Bean RepositoryService repositoryService(ProcessEngine engine) { return engine.getRepositoryService(); }

        @Bean LeaveTaskHandlers leaveHandlers(WorkflowTaskHandlerRegistry r, EnterpriseMapper m) {
            return new LeaveTaskHandlers(r, m);
        }

        @Bean LeaveApprovalFlowService flowService(RuntimeService rs, TaskService ts, RepositoryService repo, EnterpriseMapper m, JdbcTemplate jdbc) {
            return new LeaveApprovalFlowService(rs, ts, repo, m, jdbc);
        }

        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LeaveApprovalFlowService flow;

    @BeforeEach
    void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_employee(user_id VARCHAR PRIMARY KEY,display_name VARCHAR,department VARCHAR,business_role VARCHAR,manager_id VARCHAR,hr_id VARCHAR,active BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_leave_account(user_id VARCHAR,year INT,total INT,frozen INT,used INT,PRIMARY KEY(user_id,year))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_workday(day DATE PRIMARY KEY,working BOOLEAN,description VARCHAR)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_leave_request(id VARCHAR PRIMARY KEY,user_id VARCHAR,leave_type VARCHAR,start_date DATE,end_date DATE,days INT,reason VARCHAR,status VARCHAR,manager_id VARCHAR,hr_id VARCHAR,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_approval_task(id VARCHAR PRIMARY KEY,request_id VARCHAR,assignee_id VARCHAR,stage VARCHAR,status VARCHAR)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ent_audit(id VARCHAR PRIMARY KEY,request_id VARCHAR,actor_id VARCHAR,action_id VARCHAR,event VARCHAR,comment VARCHAR,created_at TIMESTAMP)");
        // 清空业务表（Flowable ACT_* 表由引擎管理，不在此清理；每个测试用唯一 request id 隔离）
        jdbc.update("DELETE FROM ent_audit");
        jdbc.update("DELETE FROM ent_approval_task");
        jdbc.update("DELETE FROM ent_leave_request");
        jdbc.update("DELETE FROM ent_leave_account");
        jdbc.update("DELETE FROM ent_workday");
        jdbc.update("DELETE FROM ent_employee");
        jdbc.update("INSERT INTO ent_employee VALUES('e','员工','研发','EMPLOYEE','m','h',TRUE),('m','主管','研发','MANAGER',NULL,NULL,TRUE),('h','人事','HR','HR',NULL,NULL,TRUE)");
        jdbc.update("INSERT INTO ent_leave_account VALUES('e',2026,10,0,0)");
    }

    private String createRequest(String id, String start, String end, int days, String type) {
        jdbc.update("INSERT INTO ent_leave_request(id,user_id,leave_type,start_date,end_date,days,reason,status,manager_id,hr_id,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                id, "e", type, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), days, "事假", "DRAFT", "m", "h");
        return id;
    }

    @Test
    void twoStageApprovalReachesApproved() {
        String req = createRequest("r1", "2026-10-12", "2026-10-12", 1, "PERSONAL");
        flow.start(req, "e", "t1");
        // 主管审批通过
        flow.completeApproval("m", req, "APPROVE", "同意");
        // 人事审批通过
        flow.completeApproval("h", req, "APPROVE", "同意");
        String status = jdbc.queryForObject("SELECT status FROM ent_leave_request WHERE id=?", String.class, req);
        assertEquals("APPROVED", status);
        assertTrue(flow.consistencyCheck(req));
    }

    @Test
    void managerRejectReleasesBalance() {
        String req = createRequest("r2", "2026-10-19", "2026-10-20", 2, "ANNUAL");
        jdbc.update("INSERT INTO ent_workday VALUES('2026-10-19',TRUE,NULL),('2026-10-20',TRUE,NULL)");
        flow.start(req, "e", "t2");
        // 余额应已冻结
        int frozen = jdbc.queryForObject("SELECT frozen FROM ent_leave_account WHERE user_id='e' AND year=2026", Integer.class);
        assertTrue(frozen >= 2);
        flow.completeApproval("m", req, "REJECT", "不同意");
        String status = jdbc.queryForObject("SELECT status FROM ent_leave_request WHERE id=?", String.class, req);
        assertEquals("REJECTED", status);
        // 驳回应释放冻结
        int frozenAfter = jdbc.queryForObject("SELECT frozen FROM ent_leave_account WHERE user_id='e' AND year=2026", Integer.class);
        assertEquals(0, frozenAfter);
        assertTrue(flow.consistencyCheck(req));
    }

    @Test
    void employeeWithdrawCancelsInstance() {
        String req = createRequest("r3", "2026-10-26", "2026-10-26", 1, "PERSONAL");
        flow.start(req, "e", "t3");
        flow.withdraw("e", req);
        String status = jdbc.queryForObject("SELECT status FROM ent_leave_request WHERE id=?", String.class, req);
        assertEquals("WITHDRAWN", status);
        assertTrue(flow.consistencyCheck(req));
    }

    @Test
    void approverWithoutTaskCannotComplete() {
        String req = createRequest("r4", "2026-11-02", "2026-11-02", 1, "PERSONAL");
        flow.start(req, "e", "t4");
        // 非审批人无待办
        assertThrows(Exception.class, () -> flow.completeApproval("x", req, "APPROVE", "ok"));
    }
}
