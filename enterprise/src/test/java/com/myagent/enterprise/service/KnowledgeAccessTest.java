package com.myagent.enterprise.service;

import com.myagent.enterprise.controller.EnterpriseSourceController;
import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.Grant;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringJUnitConfig(EnterpriseWorkflowTest.Config.class)
class KnowledgeAccessTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EnterpriseMapper mapper;
    KnowledgeAccessService access;
    @BeforeEach void setup() {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE t_user(id BIGINT PRIMARY KEY,username VARCHAR,role VARCHAR,deleted INT)");
        jdbc.execute("CREATE TABLE ent_employee(user_id VARCHAR PRIMARY KEY,display_name VARCHAR,department VARCHAR,business_role VARCHAR,manager_id VARCHAR,hr_id VARCHAR,active BOOLEAN)");
        jdbc.execute("CREATE TABLE t_knowledge_base(id VARCHAR PRIMARY KEY,collection_name VARCHAR,deleted INT)");
        jdbc.execute("CREATE TABLE t_knowledge_document(id VARCHAR PRIMARY KEY,kb_id VARCHAR,doc_name VARCHAR,deleted INT,enabled INT)");
        jdbc.execute("CREATE TABLE t_knowledge_chunk(id VARCHAR PRIMARY KEY,doc_id VARCHAR,content VARCHAR,chunk_index INT,deleted INT,enabled INT)");
        jdbc.execute("CREATE TABLE ent_kb_grant(kb_id VARCHAR,audience VARCHAR,subject VARCHAR)");
        jdbc.update("INSERT INTO t_user VALUES(1,'employee','user',0),(2,'admin','admin',0)");
        jdbc.update("INSERT INTO ent_employee VALUES('1','员工','研发','EMPLOYEE',NULL,NULL,TRUE)");
        jdbc.update("INSERT INTO t_knowledge_base VALUES('k1','internal',0),('k2','other',0)");
        jdbc.update("INSERT INTO t_knowledge_document VALUES('d1','k1','制度',0,1),('d2','k2','机密',0,1)");
        jdbc.update("INSERT INTO t_knowledge_chunk VALUES('c1','d1','制度原文',0,0,1),('c2','d2','机密原文',0,0,1)");
        access=new KnowledgeAccessService(mapper);ReflectionTestUtils.setField(access,"enabled",true);
        UserContext.set(new LoginUser("1","employee","user"));
    }
    @AfterEach void cleanup() { UserContext.clear(); }
    private void grant(String audience,String subject){jdbc.update("INSERT INTO ent_kb_grant VALUES('k1',?,?)",audience,subject);}
    @Test void oldKnowledgeDefaultsToDeny() {assertTrue(access.allowedCollections().isEmpty());assertThrows(ClientException.class,()->access.requireDocument("d1"));}
    @Test void allEmployeeGrantAllowsOnlyPublishedCollection() {grant("ALL","*");assertEquals(List.of("internal"),access.allowedCollections());access.requireDocument("d1");assertThrows(ClientException.class,()->access.requireDocument("d2"));}
    @Test void departmentGrantIsIsolated() {grant("DEPARTMENT","销售");assertTrue(access.allowedCollections().isEmpty());grant("DEPARTMENT","研发");access.requireKb("k1");}
    @Test void businessRoleGrantDoesNotUseSystemRole() {grant("ROLE","HR");assertTrue(access.allowedCollections().isEmpty());grant("ROLE","EMPLOYEE");access.requireKb("k1");}
    @Test void missingIdentityFailsClosed() {UserContext.clear();assertThrows(ClientException.class,()->access.allowedCollections());}
    @Test void inactiveEmployeeCannotReadPublishedKnowledge() {grant("ALL","*");jdbc.update("UPDATE ent_employee SET active=FALSE");assertTrue(access.allowedCollections().isEmpty());}
    @Test void disabledDocumentCannotBePreviewed() {grant("ALL","*");jdbc.update("UPDATE t_knowledge_document SET enabled=0 WHERE id='d1'");assertThrows(ClientException.class,()->access.requireDocument("d1"));}
    @Test void deletedKnowledgeIsNotSearchable() {grant("ALL","*");jdbc.update("UPDATE t_knowledge_base SET deleted=1 WHERE id='k1'");assertTrue(access.allowedCollections().isEmpty());}
    @Test void sourceChecksDocumentAndChunkBinding() {grant("ALL","*");var controller=new EnterpriseSourceController(access,mapper);assertThrows(ClientException.class,()->controller.source("d1","c2"));assertNotNull(controller.source("d1","c1"));}
    @Test void sourceCannotReadUnauthorizedDocument() {grant("ALL","*");assertThrows(ClientException.class,()->new EnterpriseSourceController(access,mapper).source("d2","c2"));}
    @Test void employeeCannotPublishKnowledge() {assertThrows(ClientException.class,()->access.grants("k1",List.of(new Grant("ALL","*"))));}
    @Test void serverIdentityUsesDatabaseRoleAndAdminCanReadOldKnowledge() {LoginUser admin=access.identity("2");assertEquals("admin",admin.getRole());UserContext.set(admin);assertEquals(2,access.allowedCollections().size());}
    @Test void deletedUserCannotRestoreAsyncIdentity() {jdbc.update("UPDATE t_user SET deleted=1 WHERE id=1");assertThrows(ClientException.class,()->access.identity("1"));}
}
