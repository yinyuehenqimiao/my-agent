package com.myagent.system.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.user.controller.request.UserCreateRequest;
import com.myagent.system.user.controller.request.UserUpdateRequest;
import com.myagent.system.user.controller.vo.UserVO;
import com.myagent.system.user.dao.entity.UserDO;
import com.myagent.system.user.dao.mapper.UserMapper;
import com.myagent.system.user.enums.UserRole;
import com.myagent.system.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public IPage<UserVO> page(Page<UserVO> page, String keyword) {
        Page<UserDO> mpPage = new Page<>(page.getCurrent(), page.getSize());
        IPage<UserDO> result = userMapper.selectPage(mpPage, Wrappers.lambdaQuery(UserDO.class)
                .like(StringUtils.hasText(keyword), UserDO::getUsername, keyword)
                .orderByDesc(UserDO::getCreateTime));
        return result.convert(each -> BeanUtil.toBean(each, UserVO.class));
    }

    @Override
    public Long create(UserCreateRequest request) {
        Long count = userMapper.selectCount(Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, request.getUsername()));
        if (count != null && count > 0) {
            throw new ClientException("用户名已存在");
        }
        UserDO user = new UserDO();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(StrUtil.blankToDefault(request.getNickname(), request.getUsername()));
        user.setRole(StrUtil.blankToDefault(request.getRole(), UserRole.USER.getCode()));
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        user.setDeleted(0);
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    public void update(Long id, UserUpdateRequest request) {
        UserDO user = userMapper.selectById(id);
        if (user == null) {
            throw new ClientException("用户不存在");
        }
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getRole())) {
            user.setRole(request.getRole());
        }
        if (StringUtils.hasText(request.getPassword())) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Override
    public void delete(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new ClientException("用户不存在");
        }
        userMapper.deleteById(id);
    }
}
