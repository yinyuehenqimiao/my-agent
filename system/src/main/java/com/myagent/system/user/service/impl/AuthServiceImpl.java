package com.myagent.system.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.user.controller.request.LoginRequest;
import com.myagent.system.user.controller.request.RegisterRequest;
import com.myagent.system.user.controller.vo.CurrentUserVO;
import com.myagent.system.user.controller.vo.LoginVO;
import com.myagent.system.user.dao.entity.UserDO;
import com.myagent.system.user.dao.mapper.UserMapper;
import com.myagent.system.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginVO login(LoginRequest request) {
        UserDO user = findByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ClientException("用户名或密码错误");
        }
        String loginId = String.valueOf(user.getId());
        StpUtil.login(loginId);
        return new LoginVO(
                loginId,
                user.getUsername(),
                user.getRole(),
                user.getNickname(),
                StpUtil.getTokenValue()
        );
    }

    @Override
    public LoginVO register(RegisterRequest request) {
        if (findByUsername(request.getUsername()) != null) {
            throw new ClientException("用户名已存在");
        }
        UserDO user = new UserDO();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StringUtils.hasText(request.getNickname())
                ? request.getNickname().trim()
                : request.getUsername().trim());
        user.setRole("user");
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);

        String loginId = String.valueOf(user.getId());
        StpUtil.login(loginId);
        return new LoginVO(
                loginId,
                user.getUsername(),
                user.getRole(),
                user.getNickname(),
                StpUtil.getTokenValue()
        );
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public CurrentUserVO currentUser() {
        String userId = UserContext.requireUser().getUserId();
        UserDO user = userMapper.selectById(Long.valueOf(userId));
        if (user == null) {
            throw new ClientException("用户不存在");
        }
        return new CurrentUserVO(
                String.valueOf(user.getId()),
                user.getUsername(),
                user.getRole(),
                user.getNickname()
        );
    }

    private UserDO findByUsername(String username) {
        return userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username.trim()));
    }
}
