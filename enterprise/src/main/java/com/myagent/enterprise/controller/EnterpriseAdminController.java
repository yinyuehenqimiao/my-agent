package com.myagent.enterprise.controller;

import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.enterprise.service.EnterpriseAdminService;
import com.myagent.enterprise.service.KnowledgeAccessService;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/enterprise/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(name="myagent.enterprise.enabled", havingValue="true")
public class EnterpriseAdminController {
    private final EnterpriseAdminService service;
    private final KnowledgeAccessService access;
    @GetMapping("/erp/status") public Result<ErpConnectionStatus> erpStatus() { return Results.success(service.erpStatus()); }
    @GetMapping("/employees") public Result<List<Employee>> employees() {return Results.success(service.employees());}
    @PutMapping("/employees/{id}") public Result<Void> employee(@PathVariable String id,@RequestBody Employee e) {service.employee(id,e);return Results.success();}
    @GetMapping("/knowledge/{id}/grants") public Result<List<Grant>> grants(@PathVariable String id) {return Results.success(service.grants(id));}
    @PutMapping("/knowledge/{id}/grants") public Result<Void> grants(@PathVariable String id,@Valid @RequestBody List<@Valid Grant> grants) {access.grants(id,grants);return Results.success();}
    @PutMapping("/documents/{id}/policy") public Result<Void> policy(@PathVariable String id,@Valid @RequestBody Policy p) {service.policy(id,p);return Results.success();}
    @GetMapping("/calendar") public Result<List<Workday>> calendar() {return Results.success(service.calendar());}
    @PutMapping("/calendar/{day}") public Result<Void> calendar(@PathVariable LocalDate day,@RequestParam boolean working) {service.calendar(day,working);return Results.success();}
    @DeleteMapping("/calendar/{day}") public Result<Void> resetCalendar(@PathVariable LocalDate day) {service.resetCalendar(day);return Results.success();}
}
