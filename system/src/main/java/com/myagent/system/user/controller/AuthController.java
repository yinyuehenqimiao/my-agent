package com.myagent.system.user.controller;

import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.user.controller.request.LoginRequest;
import com.myagent.system.user.controller.request.RegisterRequest;
import com.myagent.system.user.controller.vo.CurrentUserVO;
import com.myagent.system.user.controller.vo.LoginVO;
import com.myagent.system.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证与用户")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录（返回 Sa-Token）")
    @SecurityRequirements
    @PostMapping("/auth/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Results.success(authService.login(request));
    }

    @Operation(summary = "注册并自动登录")
    @SecurityRequirements
    @PostMapping("/auth/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request) {
        return Results.success(authService.register(request));
    }

    @Operation(summary = "登出")
    @PostMapping("/auth/logout")
    public Result<Void> logout() {
        authService.logout();
        return Results.success();
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/user/me")
    public Result<CurrentUserVO> me() {
        return Results.success(authService.currentUser());
    }
}
