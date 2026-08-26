package com.myagent.system.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.system.user.controller.request.UserCreateRequest;
import com.myagent.system.user.controller.request.UserUpdateRequest;
import com.myagent.system.user.controller.vo.UserVO;
import com.myagent.system.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/api/users")
    public Result<IPage<UserVO>> page(Page<UserVO> page,
                                      @RequestParam(value = "keyword", required = false) String keyword) {
        StpUtil.checkRole("admin");
        return Results.success(userService.page(page, keyword));
    }

    @PostMapping("/api/users")
    public Result<Long> create(@Valid @RequestBody UserCreateRequest request) {
        StpUtil.checkRole("admin");
        return Results.success(userService.create(request));
    }

    @PutMapping("/api/users/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        StpUtil.checkRole("admin");
        userService.update(id, request);
        return Results.success();
    }

    @DeleteMapping("/api/users/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        StpUtil.checkRole("admin");
        userService.delete(id);
        return Results.success();
    }
}
