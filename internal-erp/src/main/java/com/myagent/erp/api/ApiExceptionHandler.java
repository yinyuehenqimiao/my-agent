package com.myagent.erp.api;

import com.myagent.erp.identity.ErpIdentityContext;
import com.myagent.erp.identity.UnauthorizedException;
import com.myagent.erp.organization.EmployeeNotFoundException;
import com.myagent.erp.leave.InsufficientLeaveBalanceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiResponse<Void>> unauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("ERP_UNAUTHORIZED", ex.getMessage(), request.getHeader("X-Trace-Id")));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    ResponseEntity<ApiResponse<Void>> employeeNotFound(EmployeeNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("EMPLOYEE_NOT_FOUND", ex.getMessage(), traceId()));
    }

    @ExceptionHandler({InsufficientLeaveBalanceException.class, IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("ERP_BUSINESS_REJECTED", ex.getMessage(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> internal(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("ERP_INTERNAL_ERROR", "ERP 服务暂时不可用", traceId()));
    }

    private String traceId() {
        try { return ErpIdentityContext.require().traceId(); }
        catch (RuntimeException ignored) { return null; }
    }
}
