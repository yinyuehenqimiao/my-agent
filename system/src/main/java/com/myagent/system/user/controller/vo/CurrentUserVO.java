package com.myagent.system.user.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserVO {
    private String userId;
    private String username;
    private String role;
    private String nickname;
}
