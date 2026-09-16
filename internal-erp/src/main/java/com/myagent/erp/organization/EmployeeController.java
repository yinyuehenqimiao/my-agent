package com.myagent.erp.organization;

import com.myagent.erp.api.ApiResponse;
import com.myagent.erp.identity.ErpIdentityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/erp/v1/employees/me")
public class EmployeeController {
    private final EmployeeService service;

    public EmployeeController(EmployeeService service) { this.service = service; }

    @GetMapping
    public ApiResponse<EmployeeService.EmployeeProfile> me() {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.me(identity), identity.traceId());
    }

    @GetMapping("/tenure")
    public ApiResponse<EmployeeService.Tenure> tenure() {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.tenure(identity), identity.traceId());
    }

    @GetMapping("/permissions")
    public ApiResponse<List<String>> permissions() {
        var identity = ErpIdentityContext.require();
        return ApiResponse.success(service.permissions(identity), identity.traceId());
    }
}
