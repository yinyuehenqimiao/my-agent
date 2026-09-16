package com.myagent.erp.mcp;

import com.myagent.erp.InternalErpApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;

import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import static org.assertj.core.api.Assertions.assertThat;

/** MCP 协议边界：工具发现、可信员工身份和工具范围限制。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = InternalErpApplication.class)
class ErpMcpProtocolTest {

    private static final KeyPair KEY_PAIR = Keys.keyPairFor(SignatureAlgorithm.RS256);
    private static boolean schemaInitialized;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:erp-mcp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("erp.mcp.jwt.public-key", () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
        registry.add("erp.mcp.jwt.issuer", () -> "my-agent");
        registry.add("erp.mcp.jwt.audience", () -> "internal-erp");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private McpSyncClient client;

    @BeforeEach
    void setUp() {
        initializeSchema();
        jdbc.update("INSERT INTO erp_department(tenant_id,department_id,name) VALUES('tenant-a','hr','人力资源部')");
        jdbc.update("INSERT INTO erp_employee(tenant_id,employee_id,employee_no,full_name,hire_date,status,department_id,position_name,manager_id,business_role) VALUES('tenant-a','emp-1','E001','张三',DATE '2020-01-01','ACTIVE','hr','工程师','mgr-1','EMPLOYEE')");
        jdbc.update("INSERT INTO erp_leave_balance(tenant_id,employee_id,leave_year,total_days,used_days,frozen_days,version) VALUES('tenant-a','emp-1',2026,10,1,1,0)");
        client = newClient(token("get_leave_balance"));
    }

    private synchronized void initializeSchema() {
        if (schemaInitialized) return;
        var populator = new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V1__organization.sql"),
                new ClassPathResource("db/migration/V2__leave.sql"),
                new ClassPathResource("db/migration/V3__erp_operation_audit.sql"));
        populator.execute(dataSource);
        schemaInitialized = true;
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        jdbc.update("DELETE FROM erp_leave_balance");
        jdbc.update("DELETE FROM erp_employee_permission");
        jdbc.update("DELETE FROM erp_employee");
        jdbc.update("DELETE FROM erp_department");
    }

    @Test
    void discoversToolsAndReturnsTheCurrentEmployeesLeaveBalance() {
        client.initialize();

        assertThat(client.listTools().tools()).extracting(tool -> tool.name())
                .contains("get_leave_balance", "create_leave_request", "manager_decide_leave");
        var result = client.callTool(new CallToolRequest("get_leave_balance", Map.of("year", 2026)));

        assertThat(result.isError()).isFalse();
        assertThat(result.content().toString()).contains("availableDays", "8");
    }

    @Test
    void rejectsCallsOutsideTheSignedToolScope() {
        client.initialize();

        var result = client.callTool(new CallToolRequest("get_my_profile", Map.of()));

        assertThat(result.isError()).isTrue();
    }

    private McpSyncClient newClient(String bearerToken) {
        String endpoint = "http://127.0.0.1:" + port + "/mcp";
        var transport = HttpClientStreamableHttpTransport.builder(endpoint)
                .customizeRequest(builder -> builder.header("Authorization", "Bearer " + bearerToken))
                .build();
        return McpClient.sync(transport).clientInfo(new Implementation("erp-mcp-test", "1.0")).build();
    }

    private String token(String toolName) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer("my-agent")
                .subject("emp-1")
                .audience().add("internal-erp").and()
                .claim("tenant_id", "tenant-a")
                .claim("tool_id", toolName)
                .claim("trace_id", "trace-mcp-test")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(KEY_PAIR.getPrivate())
                .compact();
    }
}
