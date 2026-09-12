package com.myagent.system.auth.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户-平台角色关联 */
@Data
@TableName("sys_user_role")
public class SysUserRoleDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String roleCode;
    private String tenantId;
    private LocalDateTime createTime;
}
