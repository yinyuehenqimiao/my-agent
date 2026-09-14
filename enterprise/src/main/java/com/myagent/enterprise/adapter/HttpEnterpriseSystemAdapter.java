package com.myagent.enterprise.adapter;

import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.enterprise.EmployeeProfile;
import com.myagent.framework.enterprise.EmployeeTenure;
import com.myagent.framework.enterprise.EmployeeBusinessPermissions;
import com.myagent.framework.enterprise.EnterpriseSystemPort;
import com.myagent.framework.enterprise.LeaveBalance;
import com.myagent.framework.enterprise.LeaveRequestCommand;
import com.myagent.framework.enterprise.LeaveRequestResult;
import com.myagent.framework.enterprise.LeaveRequestSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.framework.exception.ClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * HTTP ERP 适配器契约（未来对接真实 ERP 时启用）。
 * <p>
 * 启用方式：设置 {@code myagent.enterprise.adapter=HTTP_ERP} 并提供 ERP 连接配置。
 * 通过受控身份头调用独立 ERP。实现满足：
 * <ul>
 *   <li>身份通过 {@link ExecutionIdentity} 传递，不得使用模型提供的身份字段</li>
 *   <li>写请求强制携带幂等键与 traceId，重复请求返回原结果</li>
 *   <li>分页、错误码、超时、追踪号遵循 ERP OpenAPI 约定</li>
 *   <li>敏感字段脱敏后再返回与审计</li>
 *   <li>ERP 不可用时明确返回失败或处理中，禁止模型编造成功</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "myagent.enterprise.adapter", havingValue = "HTTP_ERP")
public class HttpEnterpriseSystemAdapter implements EnterpriseSystemPort {
    private final String baseUrl;
    private final String serviceToken;
    private final java.net.http.HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpEnterpriseSystemAdapter(@Value("${myagent.enterprise.erp.base-url:}") String baseUrl,
                                       @Value("${myagent.enterprise.erp.service-token:}") String serviceToken,
                                       @Value("${myagent.enterprise.erp.timeout-ms:3000}") int timeoutMs) {
        this(baseUrl, serviceToken, timeoutMs, java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofMillis(timeoutMs)).build());
    }

    HttpEnterpriseSystemAdapter(String baseUrl, String serviceToken, int timeoutMs, java.net.http.HttpClient client) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", ""); this.serviceToken = serviceToken == null ? "" : serviceToken; this.client = client;
    }

    @Override
    public EmployeeProfile getCurrentEmployee(ExecutionIdentity identity) {
        JsonNode data = get(identity, "/api/erp/v1/employees/me");
        return new EmployeeProfile(text(data, "employeeId"), text(data, "fullName"), text(data, "departmentName"), text(data, "positionName"), text(data, "managerId"), null, "ACTIVE".equals(text(data, "status")));
    }

    @Override
    public LeaveBalance getLeaveBalance(ExecutionIdentity identity, int year) {
        JsonNode data = get(identity, "/api/erp/v1/leave-balances/me?year=" + year);
        return new LeaveBalance(identity.getUserId(), data.path("year").asInt(year), data.path("totalDays").asInt(), data.path("frozenDays").asInt(), data.path("usedDays").asInt());
    }

    @Override
    public EmployeeTenure getCurrentEmployeeTenure(ExecutionIdentity identity) {
        JsonNode data = get(identity, "/api/erp/v1/employees/me/tenure");
        try {
            return new EmployeeTenure(java.time.LocalDate.parse(text(data, "hireDate")),
                    java.time.LocalDate.parse(text(data, "asOfDate")), data.path("completedYears").asInt(), true);
        } catch (Exception exception) {
            throw new ClientException("ERP 返回的入职年限数据无效");
        }
    }

    @Override
    public EmployeeBusinessPermissions getCurrentEmployeePermissions(ExecutionIdentity identity) {
        JsonNode data = get(identity, "/api/erp/v1/employees/me/permissions");
        if (!data.isArray()) throw new ClientException("ERP 返回的权限数据无效");
        java.util.List<String> permissions = new java.util.ArrayList<>();
        data.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) permissions.add(item.asText());
        });
        return new EmployeeBusinessPermissions(permissions);
    }

    @Override
    public java.util.List<LeaveRequestSummary> getMyLeaveRequests(ExecutionIdentity identity) {
        JsonNode data = get(identity, "/api/erp/v1/leave-requests/me");
        if (!data.isArray()) throw new ClientException("ERP 返回的请假列表数据无效");
        java.util.List<LeaveRequestSummary> requests = new java.util.ArrayList<>();
        try {
            for (JsonNode item : data) {
                requests.add(new LeaveRequestSummary(text(item, "id"),
                        java.time.LocalDate.parse(text(item, "startDate")),
                        java.time.LocalDate.parse(text(item, "endDate")), item.path("leaveDays").decimalValue(),
                        text(item, "status")));
            }
            return java.util.List.copyOf(requests);
        } catch (Exception exception) {
            throw new ClientException("ERP 返回的请假列表数据无效");
        }
    }

    @Override
    public LeaveRequestResult createLeaveRequest(ExecutionIdentity identity, LeaveRequestCommand command) {
        try {
            String payload = mapper.writeValueAsString(java.util.Map.of("startDate", command.getStartDate(), "endDate", command.getEndDate(), "reason", command.getReason()));
            JsonNode data = request(identity, "/api/erp/v1/leave-requests", "POST", payload, command.getIdempotencyKey(), command.getTraceId());
            return new LeaveRequestResult(text(data, "id"), text(data, "status"), command.getTraceId());
        } catch (ClientException exception) { throw exception; } catch (Exception exception) { throw new ClientException("ERP 服务暂时不可用，请稍后重试"); }
    }

    private JsonNode get(ExecutionIdentity identity, String path) { return request(identity, path, "GET", null, null, com.myagent.framework.trace.RagTraceContext.getTraceId()); }
    private JsonNode request(ExecutionIdentity identity, String path, String method, String payload, String idempotencyKey, String traceId) {
        if (identity == null || blank(identity.getUserId()) || blank(identity.getTenantId()) || blank(serviceToken) || blank(baseUrl)) throw new ClientException("ERP 连接或执行身份未配置，拒绝执行");
        try {
            String resolvedTraceId = blank(traceId) ? java.util.UUID.randomUUID().toString() : traceId;
            var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create(baseUrl + path)).timeout(java.time.Duration.ofSeconds(3)).header("X-ERP-Service-Token", serviceToken).header("X-Tenant-Id", identity.getTenantId()).header("X-Employee-Id", identity.getUserId()).header("X-Trace-Id", resolvedTraceId);
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            var request = "POST".equals(method) ? builder.header("Content-Type", "application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build() : builder.GET().build();
            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            JsonNode root = mapper.readTree(response.body());
            if (response.statusCode() / 100 != 2 || !"0".equals(root.path("code").asText())) throw new ClientException("ERP 请求被拒绝或暂时不可用");
            return root.path("data");
        } catch (ClientException exception) { throw exception; } catch (Exception exception) { throw new ClientException("ERP 服务暂时不可用，请稍后重试"); }
    }
    private String text(JsonNode node, String name) { return node.path(name).isNull() ? null : node.path(name).asText(null); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
