package com.myagent.rag.controller;

import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.rag.core.mcp.McpConnectorAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MCP 管理端；身份和租户只取服务端 UserContext。 */
@RestController
@RequestMapping("/api/rag/admin/mcp-connectors")
public class McpConnectorAdminController {
    private final McpConnectorAdminService service;
    public McpConnectorAdminController(McpConnectorAdminService service) { this.service = service; }

    @GetMapping public Result<List<McpConnectorAdminService.ConnectorView>> connectors() {
        return Results.success(service.connectors(UserContext.requireIdentity()));
    }
    @PutMapping public Result<Void> save(@RequestBody McpConnectorAdminService.ConnectorInput input) {
        service.save(UserContext.requireIdentity(), input); return Results.success();
    }
    @PostMapping("/{connectorId}/discover") public Result<List<McpConnectorAdminService.ToolView>> discover(@PathVariable String connectorId) {
        return Results.success(service.discover(UserContext.requireIdentity(), connectorId));
    }
    @GetMapping("/{connectorId}/tools") public Result<List<McpConnectorAdminService.ToolView>> tools(@PathVariable String connectorId) {
        return Results.success(service.tools(UserContext.requireIdentity(), connectorId));
    }
    @PutMapping("/{connectorId}/tools/{toolId}/review") public Result<Void> review(@PathVariable String connectorId, @PathVariable String toolId,
            @RequestBody McpConnectorAdminService.ReviewInput input) {
        service.review(UserContext.requireIdentity(), connectorId, toolId, input); return Results.success();
    }
}
