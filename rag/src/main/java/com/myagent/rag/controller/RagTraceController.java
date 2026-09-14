package com.myagent.rag.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myagent.framework.context.ExecutionIdentity;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.rag.controller.request.RagTraceRunPageRequest;
import com.myagent.rag.controller.vo.RagTraceDetailVO;
import com.myagent.rag.controller.vo.RagTraceNodeVO;
import com.myagent.rag.controller.vo.RagTraceRunVO;
import com.myagent.rag.trace.RagTraceAuditService;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 推理链路审计管理接口；权限和租户身份均由服务端强制决定。 */
@RestController
@RequiredArgsConstructor
public class RagTraceController {

    private final RagTraceAuditService auditService;
    private final PolicyDecisionService policy;

    @GetMapping("/rag/traces/runs")
    public Result<Page<RagTraceRunVO>> pageRuns(RagTraceRunPageRequest request) {
        ExecutionIdentity identity = authorizedIdentity();
        return Results.success(auditService.page(identity, request));
    }

    @GetMapping("/rag/traces/runs/{traceId}")
    public Result<RagTraceDetailVO> detail(@PathVariable String traceId) {
        ExecutionIdentity identity = authorizedIdentity();
        return Results.success(auditService.detail(identity, traceId));
    }

    @GetMapping("/rag/traces/runs/{traceId}/nodes")
    public Result<List<RagTraceNodeVO>> nodes(@PathVariable String traceId) {
        ExecutionIdentity identity = authorizedIdentity();
        RagTraceDetailVO detail = auditService.detail(identity, traceId);
        return Results.success(detail == null ? List.of() : detail.getNodes());
    }

    private ExecutionIdentity authorizedIdentity() {
        ExecutionIdentity identity = UserContext.requireIdentity();
        policy.requirePermission(identity, "audit:view");
        return identity;
    }
}
