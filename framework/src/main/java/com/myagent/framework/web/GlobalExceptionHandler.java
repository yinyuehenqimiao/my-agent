package com.myagent.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.myagent.framework.convention.Result;
import com.myagent.framework.errorcode.BaseErrorCode;
import com.myagent.framework.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：统一成 Result 返回前端
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError fieldError = bindingResult.getFieldError();
        String message = fieldError == null ? "请求参数不合法" : fieldError.getDefaultMessage();
        log.warn("[{}] {} [valid] {}", request.getMethod(), request.getRequestURI(), message);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), message);
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(HttpServletRequest request, NotLoginException ex) {
        log.warn("[{}] {} [auth] not-login: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "未登录或登录已过期");
    }

    @ExceptionHandler(NotRoleException.class)
    public Result<Void> handleNotRole(HttpServletRequest request, NotRoleException ex) {
        log.warn("[{}] {} [auth] no-role: {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "权限不足");
    }

    @ExceptionHandler(AbstractException.class)
    public Result<Void> handleAbstractException(HttpServletRequest request, AbstractException ex) {
        log.error("[{}] {} [biz] {}", request.getMethod(), request.getRequestURI(), ex.toString());
        return Results.failure(ex);
    }

    @ExceptionHandler(Throwable.class)
    public Result<Void> handleThrowable(HttpServletRequest request, Throwable ex) {
        log.error("[{}] {} [uncaught]", request.getMethod(), request.getRequestURI(), ex);
        return Results.failure();
    }
}
