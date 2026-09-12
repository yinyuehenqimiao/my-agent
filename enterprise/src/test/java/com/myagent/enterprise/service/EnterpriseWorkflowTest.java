package com.myagent.enterprise.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.framework.exception.ClientException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the real schema, SQL, service and transaction proxy; no mocked persistence. */
@SpringJUnitConfig(EnterpriseWorkflowTest.Config.class)
class EnterpriseWorkflowTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean(destroyMethod="close") com.zaxxer.hikari.HikariDataSource ds() {
            var ds = new com.zaxxer.hikari.HikariDataSource();
            ds.setJdbcUrl("jdbc:h2:mem:enterprise;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR,DAY");
            ds.setUsername("sa"); ds.setPassword(""); ds.setMaximumPoolSize(4); return ds;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean EnterpriseMapper mapper(JdbcTemplate jdbc) { return new EnterpriseMapper(jdbc); }
        @Bean ObjectMapper json() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean Clock clock() { return Clock.fixed(Instant.parse("2026-09-10T02:00:00Z"),ZoneId.of("Asia/Shanghai")); }
        @Bean EnterpriseService service(EnterpriseMapper mapper,ObjectMapper json,Clock clock) { return new EnterpriseService(mapper,json,clock); }
        @Bean PlatformTransactionManager tx(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @Autowired EnterpriseService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource ds;
    @BeforeEach void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        new ResourceDatabasePopulator(new FileSystemResource("../resources/database/upgrades/v0.4.0/01_enterprise.sql")).execute(ds);
        jdbc.update("INSERT INTO ent_employee VALUES('e','员工','研发','EMPLOYEE','m','h',TRUE),('m','主管','研发','MANAGER',NULL,NULL,TRUE),('h','人事','HR','HR',NULL,NULL,TRUE),('x','他人','销售','EMPLOYEE','m','h',TRUE)");
    }
    private LeaveInput input(String type,String start,String end) { return new LeaveInput("session",UUID.randomUUID().toString(),type,LocalDate.parse(start),LocalDate.parse(end),"个人安排"); }
    private Action prepare() { return service.prepareLeave("e",input("ANNUAL","2026-09-14","2026-09-15")); }
    private String submit() { Action a=prepare(); service.confirm("e",a.id()); return a.requestId(); }
    private Action decision(String actor,String id,String decision) { return service.prepareDecision(actor,id,new DecisionInput("session",UUID.randomUUID().toString(),decision,"审批意见")); }

    @Test void twoStageApprovalAndIdempotentConfirmation() {
        Action draft=prepare(); assertEquals(0,service.balance("e",2026).frozen());
        assertTrue(draft.payload().contains("\"startDate\":\"2026-09-14\""), "Confirmation dates must be ISO strings, not JSON arrays");
        Action submitted=service.confirm("e",draft.id());
        assertEquals(submitted,service.confirm("e",draft.id()));
        assertEquals(2,service.balance("e",2026).frozen());
        assertEquals(1,service.tasks("m").size()); assertTrue(service.tasks("h").isEmpty());
        service.confirm("m",decision("m",draft.requestId(),"APPROVE").id());
        assertEquals("PENDING_HR",service.detail("e",draft.requestId()).request().status());
        service.confirm("h",decision("h",draft.requestId(),"APPROVE").id());
        Account a=service.balance("e",2026); assertEquals(8,a.getAvailable()); assertEquals(0,a.frozen()); assertEquals(2,a.used());
        assertEquals(4,service.detail("e",draft.requestId()).timeline().size());
    }
    @ParameterizedTest @CsvSource({"m,false","h,true"}) void rejectionReleasesBalance(String actor,boolean managerFirst) {
        String id=submit(); if(managerFirst)service.confirm("m",decision("m",id,"APPROVE").id());
        service.confirm(actor,decision(actor,id,"REJECT").id());
        assertEquals("REJECTED",service.detail("e",id).request().status()); assertEquals(10,service.balance("e",2026).getAvailable());
    }
    @ParameterizedTest @CsvSource({"false","true"}) void withdrawalReleasesAndCancelsTask(boolean managerFirst) {
        String id=submit(); if(managerFirst)service.confirm("m",decision("m",id,"APPROVE").id());
        Action a=service.prepareWithdrawal("e",id,new WithdrawInput("session","withdraw"));service.confirm("e",a.id());
        assertEquals("WITHDRAWN",service.detail("e",id).request().status());assertEquals(10,service.balance("e",2026).getAvailable());assertTrue(service.tasks("m").isEmpty());assertTrue(service.tasks("h").isEmpty());
    }
    @Test void personalLeaveDoesNotConsumeAnnualBalance() {
        Action a=service.prepareLeave("e",input("PERSONAL","2026-09-14","2026-09-15")); service.confirm("e",a.id());
        service.confirm("m",decision("m",a.requestId(),"APPROVE").id());service.confirm("h",decision("h",a.requestId(),"APPROVE").id());assertEquals(10,service.balance("e",2026).getAvailable());
    }
    @ParameterizedTest @CsvSource({"2026-09-16,2026-09-15","2026-12-31,2027-01-01","2026-09-12,2026-09-13","2026-09-01,2026-09-02"})
    void invalidDatesNeverCreateDraft(String start,String end) { assertThrows(ClientException.class,()->service.prepareLeave("e",input("ANNUAL",start,end))); assertTrue(service.requests("e").isEmpty()); }
    @Test void holidayAndMakeupDayAreDeterministic() {
        jdbc.update("INSERT INTO ent_workday VALUES(DATE '2026-09-14',FALSE,'休息'),(DATE '2026-09-12',TRUE,'调休')");
        Action a=service.prepareLeave("e",input("ANNUAL","2026-09-12","2026-09-15"));assertEquals(2,service.detail("e",a.requestId()).request().days());
    }
    @Test void calendarChangeInvalidatesConfirmation() {
        Action a=prepare();jdbc.update("INSERT INTO ent_workday VALUES(DATE '2026-09-14',FALSE,'休息')");
        assertThrows(ClientException.class,()->service.confirm("e",a.id()));assertEquals(0,service.balance("e",2026).frozen());
    }
    @Test void overlapRejectedAtConfirmationAndNoPartialWrite() {
        Action a=prepare(), b=prepare();service.confirm("e",a.id());
        assertThrows(ClientException.class,()->service.confirm("e",b.id()));assertEquals("PENDING",service.getAction("e",b.id()).status());assertEquals(2,service.balance("e",2026).frozen());
    }
    @Test void balanceRecheckedAfterPreparation() {
        Action a=prepare();jdbc.update("UPDATE ent_leave_account SET total=1 WHERE user_id='e'");
        assertThrows(ClientException.class,()->service.confirm("e",a.id()));assertEquals("DRAFT",service.detail("e",a.requestId()).request().status());
    }
    @Test void insufficientBalanceRefusesPreparation() { assertThrows(ClientException.class,()->service.prepareLeave("e",input("ANNUAL","2026-09-14","2026-09-30"))); }
    @Test void expiryAndCancellationNeverSubmit() {
        Action a=prepare();jdbc.update("UPDATE ent_action SET expires_at=TIMESTAMP '2026-09-10 09:59:00' WHERE id=?",a.id());
        assertThrows(ClientException.class,()->service.confirm("e",a.id()));assertEquals("CANCELLED",service.cancel("e",a.id()).status());assertThrows(ClientException.class,()->service.confirm("e",a.id()));assertEquals(0,service.balance("e",2026).frozen());
    }
    @Test void crossUserAccessAndImpersonationAreRejected() {
        Action a=prepare(); assertThrows(ClientException.class,()->service.getAction("x",a.id()));assertThrows(ClientException.class,()->service.confirm("x",a.id()));assertThrows(ClientException.class,()->service.detail("x",a.requestId()));
        service.confirm("e",a.id());assertThrows(ClientException.class,()->decision("e",a.requestId(),"APPROVE"));assertThrows(ClientException.class,()->decision("h",a.requestId(),"APPROVE"));assertThrows(ClientException.class,()->decision("x",a.requestId(),"APPROVE"));
    }
    @ParameterizedTest @CsvSource({"e,h","m,m","m,e"}) void invalidApproverRelationships(String manager,String hr) { jdbc.update("UPDATE ent_employee SET manager_id=?,hr_id=? WHERE user_id='e'",manager,hr);assertThrows(ClientException.class,()->prepare()); }
    @Test void missingAndInactiveApproverRefused() {jdbc.update("UPDATE ent_employee SET active=FALSE WHERE user_id='h'");assertThrows(ClientException.class,()->prepare());}
    @Test void changedApproverRefusesOldCard() {Action a=prepare();jdbc.update("UPDATE ent_employee SET manager_id='x' WHERE user_id='e'");assertThrows(ClientException.class,()->service.confirm("e",a.id()));}
    @Test void approvalRoleRevocationRefusesConfirmation() {String id=submit();Action a=decision("m",id,"APPROVE");jdbc.update("UPDATE ent_employee SET business_role='EMPLOYEE' WHERE user_id='m'");assertThrows(ClientException.class,()->service.confirm("m",a.id()));}
    @Test void staleApprovalCardCannotApproveNextNode() {String id=submit();Action a=decision("m",id,"APPROVE"),b=decision("m",id,"APPROVE");service.confirm("m",a.id());assertThrows(ClientException.class,()->service.confirm("m",b.id()));assertEquals("PENDING_HR",service.detail("e",id).request().status());}
    @Test void duplicatePreparationReturnsSameCard() {LeaveInput i=input("ANNUAL","2026-09-14","2026-09-15");assertEquals(service.prepareLeave("e",i).id(),service.prepareLeave("e",i).id());assertEquals(1,service.requests("e").size());}
    @Test void blankRejectionReasonRefused() {String id=submit();assertThrows(ClientException.class,()->service.prepareDecision("m",id,new DecisionInput("session","reject","REJECT","")));}
    @Test void completedLeaveCannotBeWithdrawn() {String id=submit();service.confirm("m",decision("m",id,"APPROVE").id());service.confirm("h",decision("h",id,"APPROVE").id());assertThrows(ClientException.class,()->service.prepareWithdrawal("e",id,new WithdrawInput("session","w")));}
    @Test void concurrentConfirmationsExecuteOnce() throws Exception {
        Action a=prepare(); ExecutorService pool=Executors.newFixedThreadPool(2);
        try { CountDownLatch start=new CountDownLatch(1);List<Future<Action>> futures=new ArrayList<>();for(int i=0;i<2;i++)futures.add(pool.submit(()->{start.await();return service.confirm("e",a.id());}));start.countDown();for(Future<Action> f:futures)assertEquals("DONE",f.get(10,TimeUnit.SECONDS).status()); }
        finally {pool.shutdownNow();}
        assertEquals(2,service.balance("e",2026).frozen());assertEquals(1,service.tasks("m").size());
    }
    @Test void concurrentOverlappingApplicationsCannotBothSubmit() throws Exception {
        Action a=prepare(),b=prepare();ExecutorService pool=Executors.newFixedThreadPool(2);
        try {CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> futures=new ArrayList<>();for(Action action:List.of(a,b))futures.add(pool.submit(()->{start.await();try{service.confirm("e",action.id());return true;}catch(ClientException ex){return false;}}));start.countDown();int successes=0;for(Future<Boolean> f:futures)if(f.get(10,TimeUnit.SECONDS))successes++;assertEquals(1,successes);}
        finally{pool.shutdownNow();}assertEquals(2,service.balance("e",2026).frozen());
    }
    @Test void newServiceInstanceRecoversPersistedCard() {
        Action a=prepare();EnterpriseService restarted=new EnterpriseService(new EnterpriseMapper(jdbc),new ObjectMapper().registerModule(new JavaTimeModule()),new Config().clock());
        assertEquals(a,restarted.getAction("e",a.id()));service.confirm("e",a.id());assertEquals("DONE",restarted.getAction("e",a.id()).status());
    }
}
