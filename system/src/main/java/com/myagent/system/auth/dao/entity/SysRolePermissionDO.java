package com.myagent.system.auth.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台角色-权限码关联 */
@Data
@TableName("sys_role_permission")
public class SysRolePermissionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String permCode;
    private LocalDateTime createTime;
}
