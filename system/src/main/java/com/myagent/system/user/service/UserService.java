package com.myagent.system.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.myagent.system.user.controller.request.UserCreateRequest;
import com.myagent.system.user.controller.request.UserUpdateRequest;
import com.myagent.system.user.controller.vo.UserVO;

public interface UserService {

    IPage<UserVO> page(Page<UserVO> page, String keyword);

    Long create(UserCreateRequest request);

    void update(Long id, UserUpdateRequest request);

    void delete(Long id);
}
