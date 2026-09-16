package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.runtime.WorkflowConfirmationService;
import com.myagent.workflow.runtime.WorkflowMessageClassifier;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/runtime")
public class WorkflowInteractionController {
    private final WorkflowConfirmationService confirmations;
    private final WorkflowMessageClassifier classifier;
    private final PolicyDecisionService policy;

    @PostMapping("/messages/classify")
    public Result<WorkflowMessageClassifier.MessageKind> classify(@RequestBody MessageRequest request) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:run"); return Results.success(classifier.classify(request.message()));
    }
    @PostMapping("/confirmations/{id}/confirm")
    public Result<WorkflowConfirmationService.Confirmation> confirm(@PathVariable String id, @RequestBody ConfirmRequest request) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run");
        return Results.success(confirmations.confirm(identity, id, request.lockVersion(), request.parameters()));
    }
    public record MessageRequest(String message) { }
    public record ConfirmRequest(long lockVersion, Map<String,Object> parameters) { }
}
