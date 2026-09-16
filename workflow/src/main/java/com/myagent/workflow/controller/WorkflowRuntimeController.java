package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.runtime.DynamicWorkflowRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow/runtime")
public class WorkflowRuntimeController {
    private final DynamicWorkflowRuntimeService runtime;
    private final PolicyDecisionService policy;

    @PostMapping("/instances")
    public Result<DynamicWorkflowRuntimeService.InstanceView> start(@RequestBody DynamicWorkflowRuntimeService.StartCommand command) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run");
        return Results.success(runtime.start(identity, command));
    }
    @GetMapping("/instances/{id}")
    public Result<DynamicWorkflowRuntimeService.InstanceView> get(@PathVariable String id) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run"); return Results.success(runtime.get(identity, id));
    }
    @PostMapping("/instances/{id}/cancel")
    public Result<DynamicWorkflowRuntimeService.InstanceView> cancel(@PathVariable String id) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run"); return Results.success(runtime.cancel(identity, id));
    }
    @GetMapping("/waits")
    public Result<List<DynamicWorkflowRuntimeService.WaitView>> waits() {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run"); return Results.success(runtime.waits(identity));
    }
    @PostMapping("/waits/{id}/resume")
    public Result<DynamicWorkflowRuntimeService.InstanceView> resume(@PathVariable String id, @RequestBody ResumeRequest request) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:run");
        return Results.success(runtime.resume(identity, id, request.lockVersion(), request.input()));
    }
    public record ResumeRequest(long lockVersion, Map<String,Object> input) { }
}
