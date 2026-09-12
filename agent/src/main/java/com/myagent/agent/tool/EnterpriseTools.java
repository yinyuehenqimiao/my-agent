package com.myagent.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.framework.exception.ClientException;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultState;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/** Native tools prepare mutations only. Confirmation endpoints are never exposed to the model. */
@Component
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name="myagent.enterprise.enabled", havingValue="true")
public class EnterpriseTools {
    private final EnterpriseService service;
    private final ObjectMapper json;
    private final Clock enterpriseClock;
    public List<AgentTool> tools() {
        return List.of(
            tool("get_my_profile","查询当前员工和审批关系",Map.of(),List.of()),
            tool("get_leave_balance","查询本人年度年假余额",Map.of("year",prop("integer","年份，不填使用本年")),List.of()),
            tool("prepare_leave_request","生成请假确认卡，尚未提交。缺少类型、日期或原因应先询问；用户点击卡片后才提交。",Map.of("leaveType",Map.of("type","string","enum",List.of("ANNUAL","PERSONAL")),"startDate",prop("string","开始日期 YYYY-MM-DD"),"endDate",prop("string","结束日期 YYYY-MM-DD"),"reason",prop("string","申请原因")),List.of("leaveType","startDate","endDate","reason")),
            tool("list_my_leave_requests","查询本人的请假申请和当前状态",Map.of(),List.of()),
            tool("get_leave_request_detail","查询有权限访问的申请详情与审批时间线",Map.of("requestId",prop("string","申请ID")),List.of("requestId")),
            tool("list_my_approval_tasks","查询分配给本人的待办审批",Map.of(),List.of()),
            tool("prepare_approval_decision","生成同意或拒绝的确认卡，不会立即审批。拒绝必须填写意见。",Map.of("requestId",prop("string","申请ID"),"decision",Map.of("type","string","enum",List.of("APPROVE","REJECT")),"comment",prop("string","审批意见")),List.of("requestId","decision")),
            tool("prepare_leave_withdrawal","生成本人申请撤回确认卡，点击确认才撤回",Map.of("requestId",prop("string","申请ID")),List.of("requestId")));
    }
    private Map<String,Object> prop(String type,String description) { return Map.of("type",type,"description",description); }
    private AgentTool tool(String name,String description,Map<String,?> properties,List<String> required) {
        return new AgentTool() {
            public String getName() { return name; }
            public String getDescription() { return description; }
            public Map<String,Object> getParameters() { return Map.of("type","object","properties",properties,"required",required,"additionalProperties",false); }
            public boolean isReadOnly() { return !name.startsWith("prepare_"); }
            public Mono<ToolResultBlock> callAsync(ToolCallParam p) {
                return Mono.fromCallable(() -> execute(name,p)).subscribeOn(Schedulers.boundedElastic());
            }
        };
    }
    private ToolResultBlock execute(String name, ToolCallParam p) throws Exception {
        long started = System.nanoTime();
        String trace = p == null || p.getToolUseBlock() == null || p.getToolUseBlock().getId() == null ? UUID.randomUUID().toString() : p.getToolUseBlock().getId();
        try {
            if (p == null || p.getRuntimeContext() == null || p.getRuntimeContext().getUserId() == null || p.getRuntimeContext().getSessionId() == null) throw new ClientException("缺少已认证的执行身份");
            String actor = p.getRuntimeContext().getUserId(), session = p.getRuntimeContext().getSessionId();
            Map<String,Object> in = p.getInput() == null ? Map.of() : p.getInput();
            Object data = switch (name) {
                case "get_my_profile" -> service.profile(actor);
                case "get_leave_balance" -> service.balance(actor, in.containsKey("year") ? Integer.parseInt(in.get("year").toString()) : LocalDate.now(enterpriseClock).getYear());
                case "list_my_leave_requests" -> service.requests(actor);
                case "get_leave_request_detail" -> service.detail(actor,str(in,"requestId"));
                case "list_my_approval_tasks" -> service.tasks(actor);
                case "prepare_leave_request" -> service.prepareLeave(actor,new LeaveInput(session,trace,str(in,"leaveType"),LocalDate.parse(str(in,"startDate")),LocalDate.parse(str(in,"endDate")),str(in,"reason")));
                case "prepare_approval_decision" -> service.prepareDecision(actor,str(in,"requestId"),new DecisionInput(session,trace,str(in,"decision"),Objects.toString(in.get("comment"),"")));
                case "prepare_leave_withdrawal" -> service.prepareWithdrawal(actor,str(in,"requestId"),new WithdrawInput(session,trace));
                default -> throw new ClientException("未知工具");
            };
            return result(name,trace,Map.of("status",data instanceof Action ? "AWAITING_CONFIRMATION":"SUCCESS","data",data,"errorCode","","traceId",trace),false);
        } catch (ClientException | IllegalArgumentException ex) {
            return result(name,trace,Map.of("status","ERROR","errorCode","VALIDATION_OR_PERMISSION","message",Objects.toString(ex.getMessage(),"参数错误"),"traceId",trace),true);
        } catch (Exception ex) {
            log.error("Enterprise tool failed tool={} traceId={} type={}", name, trace, ex.getClass().getSimpleName());
            return result(name,trace,Map.of("status","ERROR","errorCode","INTERNAL_ERROR","message","业务工具暂不可用，操作未确认时不会提交，请稍后查询状态","traceId",trace),true);
        } finally {
            log.info("Enterprise tool completed tool={} traceId={} elapsedMs={}", name, trace, (System.nanoTime()-started)/1_000_000);
        }
    }
    private String str(Map<String,Object> in,String key) { Object value=in.get(key); if (!(value instanceof String s) || s.isBlank()) throw new ClientException("缺少参数："+key); return s; }
    private ToolResultBlock result(String name,String id,Object value,boolean error) throws Exception {
        return ToolResultBlock.builder().id(id).name(name).output(TextBlock.builder().text(json.writeValueAsString(value)).build()).state(error?ToolResultState.ERROR:ToolResultState.SUCCESS).build();
    }
}
