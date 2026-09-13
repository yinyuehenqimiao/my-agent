package com.myagent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.tool.ToolDefinition;
import com.myagent.framework.tool.ToolDefinition.RiskLevel;
import com.myagent.framework.tool.ToolDefinition.Source;
import com.myagent.framework.tool.ToolGateway;
import com.myagent.framework.tool.ToolInvocation;
import com.myagent.framework.tool.ToolResult;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 企业工具：仅做 AgentScope 协议转换，业务执行统一经 {@link ToolGateway} 安全管道。
 * <p>
 * - 工具身份取自 RuntimeContext（服务端签发），模型传入的 userId/角色不可信。
 * - 写工具（prepare_）只生成确认卡，永不立即提交；幂等键由调用端生成。
 * - 失败不暴露敏感异常文本，输出经网关脱敏。
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "myagent.enterprise.enabled", havingValue = "true")
public class EnterpriseTools {
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "get_my_profile", "我的员工档案",
            "get_leave_balance", "我的年假余额",
            "list_my_leave_requests", "我的请假申请",
            "get_leave_request_detail", "请假申请详情",
            "list_my_approval_tasks", "我的审批待办",
            "prepare_leave_request", "生成请假确认卡",
            "prepare_approval_decision", "生成审批确认卡",
            "prepare_leave_withdrawal", "生成撤回确认卡");
    private final EnterpriseService service;
    private final ObjectMapper json;
    private final Clock enterpriseClock;
    private final ToolGateway toolGateway;

    @PostConstruct
    void registerTools() {
        // 只读查询工具
        toolGateway.register(def("get_my_profile", "查询当前员工和审批关系", Map.of(), List.of(),
                true, RiskLevel.LOW, null), this::getMyProfile);
        toolGateway.register(def("get_leave_balance", "查询本人年度年假余额",
                Map.of("year", prop("integer", "年份，不填使用本年")), List.of(),
                true, RiskLevel.LOW, null), this::getLeaveBalance);
        toolGateway.register(def("list_my_leave_requests", "查询本人的请假申请和当前状态", Map.of(), List.of(),
                true, RiskLevel.LOW, null), this::listMyLeaveRequests);
        toolGateway.register(def("get_leave_request_detail", "查询有权限访问的申请详情与审批时间线",
                Map.of("requestId", prop("string", "申请ID")), List.of("requestId"),
                true, RiskLevel.LOW, null), this::getLeaveRequestDetail);
        toolGateway.register(def("list_my_approval_tasks", "查询分配给本人的待办审批", Map.of(), List.of(),
                true, RiskLevel.LOW, null), this::listMyApprovalTasks);
        // 写工具（仅生成确认卡，幂等）
        toolGateway.register(def("prepare_leave_request", "生成请假确认卡，尚未提交。缺少类型、日期或原因应先询问；用户点击卡片后才提交。",
                Map.of("leaveType", Map.of("type", "string", "enum", List.of("ANNUAL", "PERSONAL")),
                        "startDate", prop("string", "开始日期 YYYY-MM-DD"),
                        "endDate", prop("string", "结束日期 YYYY-MM-DD"),
                        "reason", prop("string", "申请原因")),
                List.of("leaveType", "startDate", "endDate", "reason"),
                false, RiskLevel.MEDIUM, "leave"), this::prepareLeaveRequest);
        toolGateway.register(def("prepare_approval_decision", "生成同意或拒绝的确认卡，不会立即审批。拒绝必须填写意见。",
                Map.of("requestId", prop("string", "申请ID"),
                        "decision", Map.of("type", "string", "enum", List.of("APPROVE", "REJECT")),
                        "comment", prop("string", "审批意见")),
                List.of("requestId", "decision"),
                false, RiskLevel.MEDIUM, "leave"), this::prepareApprovalDecision);
        toolGateway.register(def("prepare_leave_withdrawal", "生成本人申请撤回确认卡，点击确认才撤回",
                Map.of("requestId", prop("string", "申请ID")), List.of("requestId"),
                false, RiskLevel.MEDIUM, "leave"), this::prepareLeaveWithdrawal);
    }

    // ===== 工具处理器：使用服务端签发身份，忽略模型传入的 userId =====

    private ToolResult getMyProfile(ToolInvocation inv) {
        return ToolResult.success(service.profile(actor(inv)), inv.getTraceId());
    }

    private ToolResult getLeaveBalance(ToolInvocation inv) {
        int year = inv.getInput() != null && inv.getInput().containsKey("year")
                ? Integer.parseInt(inv.getInput().get("year").toString())
                : LocalDate.now(enterpriseClock).getYear();
        return ToolResult.success(service.balance(actor(inv), year), inv.getTraceId());
    }

    private ToolResult listMyLeaveRequests(ToolInvocation inv) {
        return ToolResult.success(service.requests(actor(inv)), inv.getTraceId());
    }

    private ToolResult getLeaveRequestDetail(ToolInvocation inv) {
        return ToolResult.success(service.detail(actor(inv), str(inv, "requestId")), inv.getTraceId());
    }

    private ToolResult listMyApprovalTasks(ToolInvocation inv) {
        return ToolResult.success(service.tasks(actor(inv)), inv.getTraceId());
    }

    private ToolResult prepareLeaveRequest(ToolInvocation inv) {
        Action a = service.prepareLeave(actor(inv), new LeaveInput(
                sessionOf(inv), inv.getIdempotencyKey(),
                str(inv, "leaveType"), LocalDate.parse(str(inv, "startDate")),
                LocalDate.parse(str(inv, "endDate")), str(inv, "reason")));
        return ToolResult.awaiting(a, inv.getTraceId());
    }

    private ToolResult prepareApprovalDecision(ToolInvocation inv) {
        Action a = service.prepareDecision(actor(inv), str(inv, "requestId"),
                new DecisionInput(sessionOf(inv), inv.getIdempotencyKey(),
                        str(inv, "decision"), Objects.toString(inv.getInput().get("comment"), "")));
        return ToolResult.awaiting(a, inv.getTraceId());
    }

    private ToolResult prepareLeaveWithdrawal(ToolInvocation inv) {
        Action a = service.prepareWithdrawal(actor(inv), str(inv, "requestId"),
                new WithdrawInput(sessionOf(inv), inv.getIdempotencyKey()));
        return ToolResult.awaiting(a, inv.getTraceId());
    }

    // ===== AgentScope 协议适配：构建 ToolInvocation 并经网关执行 =====

    public List<AgentTool> tools() {
        return tools(Source.REACT);
    }

    /** 为不同编排管线创建带可信来源标记的工具适配器。 */
    public List<AgentTool> tools(Source source) {
        return toolGateway == null ? List.of() : registeredNames().stream()
                .map(name -> agentTool(name, source))
                .toList();
    }

    public static Map<String, String> displayNames() {
        return DISPLAY_NAMES;
    }

    private List<String> registeredNames() {
        return List.of("get_my_profile", "get_leave_balance", "list_my_leave_requests",
                "get_leave_request_detail", "list_my_approval_tasks",
                "prepare_leave_request", "prepare_approval_decision", "prepare_leave_withdrawal");
    }

    private AgentTool agentTool(String name, Source source) {
        ToolDefinition definition = toolGateway.listDefinitions().stream()
                .filter(item -> name.equals(item.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("企业工具未注册：" + name));
        return new AgentTool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return definition.getDescription(); }
            @Override public Map<String, Object> getParameters() { return definition.getInputSchema(); }
            @Override public boolean isReadOnly() { return definition.isReadOnly(); }
            @Override public Mono<ToolResultBlock> callAsync(ToolCallParam p) {
                return Mono.fromCallable(() -> invokeViaGateway(name, source, p)).subscribeOn(Schedulers.boundedElastic());
            }
        };
    }

    private ToolResultBlock invokeViaGateway(String name, Source source, ToolCallParam p) throws Exception {
        String trace = p.getToolUseBlock() != null && p.getToolUseBlock().getId() != null
                ? p.getToolUseBlock().getId() : UUID.randomUUID().toString();
        ExecutionIdentity identity = identityFrom(p);
        ToolInvocation inv = ToolInvocation.builder()
                .toolName(name)
                .input(p.getInput() == null ? Map.of() : p.getInput())
                .identity(identity)
                .source(source)
                .traceId(trace)
                .idempotencyKey(name.startsWith("prepare_") ? trace : null)
                .build();
        ToolResult result = toolGateway.invoke(inv);
        return toBlock(name, trace, result);
    }

    /** 从 RuntimeContext 取服务端签发身份；缺失则返回 null（网关 fail-closed） */
    private ExecutionIdentity identityFrom(ToolCallParam p) {
        RuntimeContext rc = p.getRuntimeContext();
        if (rc == null || rc.getUserId() == null || rc.getSessionId() == null) return null;
        return LoginUser.builder().userId(rc.getUserId()).username(rc.getUserId()).build();
    }

    private ToolResultBlock toBlock(String name, String trace, ToolResult result) throws Exception {
        Object payload = result.getOutput() == null
                ? Map.of("status", result.getState().name(), "errorCode", result.getErrorCode() == null ? "" : result.getErrorCode(),
                        "message", result.getMessage() == null ? "" : result.getMessage(), "traceId", trace)
                : Map.of("status", result.getState().name(), "data", result.getOutput(),
                        "errorCode", result.getErrorCode() == null ? "" : result.getErrorCode(), "traceId", trace);
        boolean error = result.getState() != ToolResult.State.SUCCESS && result.getState() != ToolResult.State.AWAITING_CONFIRMATION;
        return ToolResultBlock.builder().id(trace).name(name)
                .output(TextBlock.builder().text(json.writeValueAsString(payload)).build())
                .state(error ? ToolResultState.ERROR : ToolResultState.SUCCESS).build();
    }

    // ===== 辅助 =====

    private String actor(ToolInvocation inv) {
        if (inv.getIdentity() == null || inv.getIdentity().getUserId() == null) {
            throw new ClientException("缺少已认证的执行身份");
        }
        return inv.getIdentity().getUserId();
    }

    private String str(ToolInvocation inv, String key) {
        Object v = inv.getInput() == null ? null : inv.getInput().get(key);
        if (!(v instanceof String s) || s.isBlank()) throw new ClientException("缺少参数：" + key);
        return s;
    }

    private String sessionOf(ToolInvocation inv) {
        return "session";
    }

    private Map<String, Object> prop(String type, String description) {
        return Map.of("type", type, "description", description);
    }

    private ToolDefinition def(String name, String description, Map<String, ?> properties,
                               List<String> required, boolean readOnly, RiskLevel risk,
                               String sensitiveCategory) {
        return ToolDefinition.builder()
                .name(name).description(description)
                .inputSchema(Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false))
                .readOnly(readOnly).riskLevel(risk)
                .timeoutMs(30_000L).maxRetries(0)
                .idempotent(!readOnly).requiresConfirmation(!readOnly)
                .sensitiveFields(sensitiveCategory == null ? List.of() : List.of("reason", "comment"))
                .allowedSources(List.of(Source.REACT, Source.WORKFLOW, Source.ADMIN))
                .permissionCode(null) // 企业工具仅需已认证身份，业务权限由 EnterpriseService 内部 ABAC 校验
                .enabled(true)
                .build();
    }
}
