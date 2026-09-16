package com.myagent.erp.leave;

import com.myagent.erp.api.ApiResponse;
import com.myagent.erp.identity.ErpIdentityContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/erp/v1")
public class LeaveController {
    private final LeaveService service;

    public LeaveController(LeaveService service) { this.service = service; }

    @GetMapping("/leave-balances/me")
    public ApiResponse<LeaveService.LeaveBalance> balance(@RequestParam int year) {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.balance(identity, year), identity.traceId());
    }

    @PostMapping("/leave-requests")
    public ApiResponse<LeaveService.LeaveRequest> create(@Valid @RequestBody CreateLeaveRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.create(identity,
                new LeaveService.CreateLeaveCommand(request.startDate(), request.endDate(), request.reason(), idempotencyKey)), identity.traceId());
    }

    /** 仅返回当前受控身份名下的请假单，禁止以请求参数指定员工。 */
    @GetMapping("/leave-requests/me")
    public ApiResponse<List<LeaveService.LeaveRequest>> mine() {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.listMine(identity), identity.traceId());
    }

    @GetMapping("/leave-requests/{requestId}")
    public ApiResponse<LeaveService.LeaveRequest> find(@PathVariable String requestId) {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.find(identity, requestId), identity.traceId());
    }

    @PostMapping("/leave-requests/{requestId}/withdraw")
    public ApiResponse<Void> withdraw(@PathVariable String requestId) {
        var identity = ErpIdentityContext.require();
        service.withdraw(identity, requestId);
        return ApiResponse.success(null, identity.traceId());
    }

    @PostMapping("/tasks/{requestId}/manager-decision")
    public ApiResponse<Void> managerDecision(@PathVariable String requestId, @RequestBody DecisionRequest request) {
        var identity = ErpIdentityContext.require();
        service.managerApprove(identity, requestId, request.approved());
        return ApiResponse.success(null, identity.traceId());
    }

    @PostMapping("/tasks/{requestId}/hr-decision")
    public ApiResponse<Void> hrDecision(@PathVariable String requestId, @RequestBody DecisionRequest request) {
        var identity = ErpIdentityContext.require();
        service.hrApprove(identity, requestId, request.approved());
        return ApiResponse.success(null, identity.traceId());
    }

    public record CreateLeaveRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate, @NotBlank String reason) {}
    public record DecisionRequest(boolean approved) {}
}
