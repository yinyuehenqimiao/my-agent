package com.myagent.system.auth.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台角色（RBAC） */
@Data
@TableName("sys_role")
public class SysRoleDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Boolean enabled;
    private String tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
