package com.myagent.enterprise.controller;

import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.service.EnterpriseService;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/enterprise")
@RequiredArgsConstructor
@ConditionalOnProperty(name="myagent.enterprise.enabled", havingValue="true")
public class EnterpriseController {
    private final EnterpriseService service;
    private String actor() { return UserContext.requireUser().getUserId(); }
    @GetMapping("/me") public Result<Employee> me() { return Results.success(service.profile(actor())); }
    @GetMapping("/leave-balances") public Result<Account> balance(@RequestParam int year) { return Results.success(service.balance(actor(), year)); }
    @GetMapping("/leave-requests") public Result<List<LeaveRequest>> requests() { return Results.success(service.requests(actor())); }
    @GetMapping("/leave-requests/{id}") public Result<Detail> detail(@PathVariable String id) { return Results.success(service.detail(actor(), id)); }
    @PostMapping("/leave-requests/prepare") public Result<Action> prepare(@Valid @RequestBody LeaveInput input) { return Results.success(service.prepareLeave(actor(), input)); }
    @PostMapping("/leave-requests/{id}/withdrawal/prepare") public Result<Action> withdraw(@PathVariable String id, @Valid @RequestBody WithdrawInput input) { return Results.success(service.prepareWithdrawal(actor(), id, input)); }
    @GetMapping("/approval-tasks") public Result<List<ApprovalTask>> tasks() { return Results.success(service.tasks(actor())); }
    @PostMapping("/approval-tasks/{id}/decision/prepare") public Result<Action> decision(@PathVariable String id, @Valid @RequestBody DecisionInput input) { return Results.success(service.prepareDecision(actor(), id, input)); }
    @GetMapping("/actions") public Result<List<Action>> actions(@RequestParam String conversationId) { return Results.success(service.actions(actor(), conversationId)); }
    @GetMapping("/actions/{id}") public Result<Action> action(@PathVariable String id) { return Results.success(service.getAction(actor(), id)); }
    @PostMapping("/actions/{id}/confirm") public Result<Action> confirm(@PathVariable String id) { return Results.success(service.confirm(actor(), id)); }
    @PostMapping("/actions/{id}/cancel") public Result<Action> cancel(@PathVariable String id) { return Results.success(service.cancel(actor(), id)); }
}
