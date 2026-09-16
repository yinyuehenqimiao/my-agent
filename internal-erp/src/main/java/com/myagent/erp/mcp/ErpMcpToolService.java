package com.myagent.erp.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.erp.leave.LeaveService;
import com.myagent.erp.organization.EmployeeService;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/** 将既有 ERP 领域服务投影为 MCP 工具，不在协议层复制数据库或权限规则。 */
@Service
public class ErpMcpToolService {
    private final EmployeeService employeeService;
    private final LeaveService leaveService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ErpMcpToolService(EmployeeService employeeService, LeaveService leaveService) {
        this.employeeService = employeeService;
        this.leaveService = leaveService;
    }

    public List<SyncToolSpecification> specifications() {
        return List.of(
                read("get_my_profile", "查询我的档案", emptySchema(), identity -> employeeService.me(identity)),
                read("get_my_tenure", "查询我的入职年限", emptySchema(), identity -> employeeService.tenure(identity)),
                read("get_my_permissions", "查询我的业务权限", emptySchema(), identity -> employeeService.permissions(identity)),
                read("get_leave_balance", "查询我的年假余额", yearSchema(), identity -> null, (identity, arguments) ->
                        leaveService.balance(identity, requiredYear(arguments))),
                read("list_my_leave_requests", "查询我的请假记录", emptySchema(), identity -> leaveService.listMine(identity)),
                read("get_my_leave_request", "查询我的单个请假单", requestIdSchema(), identity -> null, (identity, arguments) ->
                        leaveService.find(identity, requiredString(arguments, "requestId"))),
                read("list_my_approval_tasks", "查询我的待审批任务", emptySchema(), identity -> leaveService.listApprovalTasks(identity)),
                write("create_leave_request", "提交请假申请", createLeaveSchema(), (identity, arguments) -> leaveService.create(identity,
                        new LeaveService.CreateLeaveCommand(LocalDate.parse(requiredString(arguments, "startDate")),
                                LocalDate.parse(requiredString(arguments, "endDate")), requiredString(arguments, "reason"),
                                requiredString(arguments, "idempotencyKey")))),
                write("withdraw_leave_request", "撤回我的请假申请", requestIdSchema(), (identity, arguments) -> {
                    leaveService.withdraw(identity, requiredString(arguments, "requestId")); return Map.of("withdrawn", true);
                }),
                write("manager_decide_leave", "主管处理请假审批", decisionSchema(), (identity, arguments) -> {
                    leaveService.managerApprove(identity, requiredString(arguments, "requestId"), requiredBoolean(arguments, "approved"));
                    return Map.of("processed", true);
                }),
                write("hr_decide_leave", "人事处理请假审批", decisionSchema(), (identity, arguments) -> {
                    leaveService.hrApprove(identity, requiredString(arguments, "requestId"), requiredBoolean(arguments, "approved"));
                    return Map.of("processed", true);
                })
        );
    }

    private SyncToolSpecification read(String name, String title, JsonSchema schema, Function<com.myagent.erp.identity.ErpIdentity, Object> action) {
        return read(name, title, schema, action, (identity, ignored) -> action.apply(identity));
    }

    private SyncToolSpecification read(String name, String title, JsonSchema schema,
            Function<com.myagent.erp.identity.ErpIdentity, Object> ignored,
            BiFunction<com.myagent.erp.identity.ErpIdentity, Map<String, Object>, Object> action) {
        return specification(name, title, schema, true, action);
    }

    private SyncToolSpecification write(String name, String title, JsonSchema schema,
            BiFunction<com.myagent.erp.identity.ErpIdentity, Map<String, Object>, Object> action) {
        return specification(name, title, schema, false, action);
    }

    private SyncToolSpecification specification(String name, String title, JsonSchema schema, boolean readOnly,
            BiFunction<com.myagent.erp.identity.ErpIdentity, Map<String, Object>, Object> action) {
        Tool tool = Tool.builder().name(name).title(title).description(title)
                .inputSchema(schema).annotations(new ToolAnnotations(title, readOnly, !readOnly, !readOnly, false, false)).build();
        return new SyncToolSpecification(tool, (exchange, request) -> invoke(exchange, request, name, action));
    }

    private CallToolResult invoke(McpSyncServerExchange exchange, CallToolRequest request, String expectedTool,
            BiFunction<com.myagent.erp.identity.ErpIdentity, Map<String, Object>, Object> action) {
        try {
            ErpMcpIdentity scoped = (ErpMcpIdentity) exchange.transportContext().get(McpBearerIdentityFilter.IDENTITY_ATTRIBUTE);
            if (scoped == null || !(expectedTool.equals(scoped.allowedToolId()) || "*".equals(scoped.allowedToolId()))) {
                return failure("当前服务身份不允许调用该工具");
            }
            Object result = action.apply(scoped.identity(), request.arguments() == null ? Map.of() : request.arguments());
            return success(result);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return failure(exception.getMessage());
        } catch (Exception exception) {
            return failure("ERP 工具执行失败");
        }
    }

    private CallToolResult success(Object value) throws Exception {
        return CallToolResult.builder().content(List.of(new TextContent(objectMapper.writeValueAsString(value)))).isError(false).build();
    }

    private CallToolResult failure(String message) {
        return CallToolResult.builder().content(List.of(new TextContent(message))).isError(true).build();
    }

    private static JsonSchema emptySchema() { return schema(Map.of(), List.of()); }
    private static JsonSchema yearSchema() { return schema(Map.of("year", Map.of("type", "integer", "minimum", 2000)), List.of("year")); }
    private static JsonSchema requestIdSchema() { return schema(Map.of("requestId", Map.of("type", "string", "minLength", 1)), List.of("requestId")); }
    private static JsonSchema createLeaveSchema() {
        return schema(Map.of("startDate", Map.of("type", "string", "format", "date"), "endDate", Map.of("type", "string", "format", "date"),
                "reason", Map.of("type", "string", "minLength", 1), "idempotencyKey", Map.of("type", "string", "minLength", 1)),
                List.of("startDate", "endDate", "reason", "idempotencyKey"));
    }
    private static JsonSchema decisionSchema() {
        return schema(Map.of("requestId", Map.of("type", "string", "minLength", 1), "approved", Map.of("type", "boolean")),
                List.of("requestId", "approved"));
    }
    private static JsonSchema schema(Map<String, Object> properties, List<String> required) {
        return new JsonSchema("object", new LinkedHashMap<>(properties), required, false, null, null);
    }
    private static String requiredString(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        return text;
    }
    private static int requiredYear(Map<String, Object> arguments) {
        Object value = arguments.get("year");
        if (!(value instanceof Number number) || number.intValue() < 2000) throw new IllegalArgumentException("year 无效");
        return number.intValue();
    }
    private static boolean requiredBoolean(Map<String, Object> arguments, String name) {
        Object value = arguments.get(name);
        if (!(value instanceof Boolean approved)) throw new IllegalArgumentException(name + " 必须为布尔值");
        return approved;
    }
}
