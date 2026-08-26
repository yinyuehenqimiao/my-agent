package com.myagent.system.user.controller.request;

import lombok.Data;

@Data
public class UserUpdateRequest {
    private String nickname;
    private String role;
    private String password;
}
