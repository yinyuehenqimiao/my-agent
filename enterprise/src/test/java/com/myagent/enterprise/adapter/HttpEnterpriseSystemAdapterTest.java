package com.myagent.enterprise.adapter;

import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpEnterpriseSystemAdapterTest {
    private HttpServer server;
    private HttpEnterpriseSystemAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/erp/v1/employees/me", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-ERP-Service-Token")).isEqualTo("test-token");
            assertThat(exchange.getRequestHeaders().getFirst("X-Tenant-Id")).isEqualTo("tenant-a");
            assertThat(exchange.getRequestHeaders().getFirst("X-Employee-Id")).isEqualTo("u1");
            byte[] body = "{\"code\":\"0\",\"data\":{\"employeeId\":\"u1\",\"fullName\":\"张三\",\"departmentName\":\"技术部\",\"positionName\":\"工程师\",\"managerId\":\"m1\",\"status\":\"ACTIVE\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/erp/v1/leave-balances/me", exchange -> {
            byte[] body = "{\"code\":\"0\",\"data\":{\"year\":2026,\"totalDays\":10,\"frozenDays\":2,\"usedDays\":1,\"availableDays\":7}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/erp/v1/employees/me/tenure", exchange -> {
            byte[] body = "{\"code\":\"0\",\"data\":{\"hireDate\":\"2020-09-13\",\"asOfDate\":\"2026-09-13\",\"completedYears\":6}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/erp/v1/employees/me/permissions", exchange -> {
            byte[] body = "{\"code\":\"0\",\"data\":[\"查询本人年假\",\"发起请假\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/erp/v1/leave-requests", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Trace-Id")).isEqualTo("trace-write-1");
            assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("idempotent-1");
            byte[] body = "{\"code\":\"0\",\"data\":{\"id\":\"leave-1\",\"status\":\"PENDING_MANAGER\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/api/erp/v1/leave-requests/me", exchange -> {
            byte[] body = "{\"code\":\"0\",\"data\":[{\"id\":\"leave-1\",\"startDate\":\"2026-09-14\",\"endDate\":\"2026-09-15\",\"leaveDays\":2,\"status\":\"PENDING_MANAGER\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        adapter = new HttpEnterpriseSystemAdapter("http://127.0.0.1:" + server.getAddress().getPort(), "test-token", 2000);
    }

    @AfterEach void stop() { server.stop(0); }

    @Test void mapsAuthenticatedEmployeeAndBalance() {
        var identity = LoginUser.builder().userId("u1").tenantId("tenant-a").build();
        assertThat(adapter.getCurrentEmployee(identity).displayName()).isEqualTo("张三");
        assertThat(adapter.getLeaveBalance(identity, 2026).getAvailable()).isEqualTo(7);
    }

    @Test void mapsTenureAndBusinessPermissionsFromAuthenticatedEmployee() {
        var identity = LoginUser.builder().userId("u1").tenantId("tenant-a").build();

        assertThat(adapter.getCurrentEmployeeTenure(identity).completedYears()).isEqualTo(6);
        assertThat(adapter.getCurrentEmployeePermissions(identity).permissions())
                .containsExactly("查询本人年假", "发起请假");
    }

    @Test void mapsCurrentEmployeeLeaveRequests() {
        var identity = LoginUser.builder().userId("u1").tenantId("tenant-a").build();

        assertThat(adapter.getMyLeaveRequests(identity))
                .extracting(request -> request.requestId(), request -> request.status())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("leave-1", "PENDING_MANAGER"));
    }

    @Test void unavailableErpFailsClosed() {
        var unavailable = new HttpEnterpriseSystemAdapter("http://127.0.0.1:1", "test-token", 100);
        assertThatThrownBy(() -> unavailable.getCurrentEmployee(LoginUser.builder().userId("u1").tenantId("tenant-a").build()))
                .isInstanceOf(ClientException.class).hasMessageContaining("ERP 服务暂时不可用");
    }

    @Test void writeRequestPreservesTraceAndIdempotencyKey() {
        var identity = LoginUser.builder().userId("u1").tenantId("tenant-a").build();
        var command = LeaveRequestCommand.builder().leaveType("ANNUAL").startDate("2026-09-14").endDate("2026-09-15")
                .reason("年假").idempotencyKey("idempotent-1").traceId("trace-write-1").build();
        assertThat(adapter.createLeaveRequest(identity, command).requestId()).isEqualTo("leave-1");
    }
}
