package com.myagent.system.user.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.myagent.system.user.dao.entity.UserDO;
import com.myagent.system.user.dao.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 首次启动写入演示账号（BCrypt）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserDataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // admin 固定对齐 ragent：账号 admin / 密码 admin / 角色 admin（已存在则同步密码与角色）
        ensureUser("admin", "admin", "管理员", "admin", true);
        ensureUser("demo", "user123", "演示用户", "user", false);
    }

    private void ensureUser(String username, String rawPassword, String nickname, String role, boolean syncIfExists) {
        UserDO existing = userMapper.selectOne(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username));
        if (existing != null) {
            if (!syncIfExists) {
                return;
            }
            boolean changed = false;
            if (!passwordEncoder.matches(rawPassword, existing.getPassword())) {
                existing.setPassword(passwordEncoder.encode(rawPassword));
                changed = true;
            }
            if (!role.equals(existing.getRole())) {
                existing.setRole(role);
                changed = true;
            }
            if (changed) {
                existing.setUpdateTime(LocalDateTime.now());
                userMapper.updateById(existing);
                log.info("已同步账号 {} 密码/角色 (role={})", username, role);
            }
            return;
        }
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setRole(role);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("已初始化演示账号 {} / {} (role={})", username, rawPassword, role);
    }
}
