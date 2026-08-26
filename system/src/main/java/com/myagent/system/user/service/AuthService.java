package com.myagent.system.user.service;

import com.myagent.system.user.controller.request.LoginRequest;
import com.myagent.system.user.controller.request.RegisterRequest;
import com.myagent.system.user.controller.vo.CurrentUserVO;
import com.myagent.system.user.controller.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginRequest request);

    LoginVO register(RegisterRequest request);

    void logout();

    CurrentUserVO currentUser();
}
