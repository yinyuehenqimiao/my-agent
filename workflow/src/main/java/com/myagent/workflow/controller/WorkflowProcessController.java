package com.myagent.workflow.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.auth.service.PolicyDecisionService;
import com.myagent.workflow.model.WorkflowProcess;
import com.myagent.workflow.service.WorkflowProcessCompiler;
import com.myagent.workflow.service.WorkflowProcessVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflow")
public class WorkflowProcessController {
    private final WorkflowProcessVersionService versions;
    private final WorkflowProcessCompiler compiler;
    private final PolicyDecisionService policy;

    @PostMapping("/process-definitions")
    public Result<WorkflowProcessVersionService.VersionResult> create(@RequestBody CreateRequest request) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:edit");
        return Results.success(versions.create(request.defKey(), request.name(), request.description(), request.process(), identity.getUserId()));
    }

    @PutMapping("/process-versions/{versionId}")
    public Result<WorkflowProcessVersionService.VersionResult> save(@PathVariable long versionId, @RequestBody SaveRequest request) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:edit");
        return Results.success(versions.updateDraft(versionId, request.lockVersion(), request.process(), identity.getUserId()));
    }

    @PostMapping("/process-versions/{versionId}/copy")
    public Result<WorkflowProcessVersionService.VersionResult> copy(@PathVariable long versionId, @RequestBody ProcessRequest request) {
        var identity = UserContext.requireIdentity(); policy.requirePermission(identity, "workflow:edit");
        return Results.success(versions.copy(versionId, request.process(), identity.getUserId()));
    }

    @PostMapping("/processes/validate")
    public Result<WorkflowProcessCompiler.CompiledProcess> validate(@RequestBody ValidateRequest request) {
        policy.requirePermission(UserContext.requireIdentity(), "workflow:edit");
        return Results.success(compiler.compile(request.defKey(), request.process()));
    }

    public record CreateRequest(String defKey, String name, String description, WorkflowProcess process) { }
    public record SaveRequest(long lockVersion, WorkflowProcess process) { }
    public record ProcessRequest(WorkflowProcess process) { }
    public record ValidateRequest(String defKey, WorkflowProcess process) { }
}
