package com.myagent.rag.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.rag.core.mcp.McpIntentBindingService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rag/admin/mcp-intent-bindings")
public class McpIntentBindingController {
    private final McpIntentBindingService service;
    public McpIntentBindingController(McpIntentBindingService service) { this.service = service; }
    @GetMapping public Result<List<McpIntentBindingService.BindingView>> list() { return Results.success(service.list(UserContext.requireIdentity())); }
    @PutMapping public Result<Void> bind(@RequestBody McpIntentBindingService.BindingInput input) { service.bind(UserContext.requireIdentity(), input); return Results.success(); }
    @PostMapping("/{intentCode}/publish") public Result<Void> publish(@PathVariable String intentCode) { service.publish(UserContext.requireIdentity(), intentCode); return Results.success(); }
}
