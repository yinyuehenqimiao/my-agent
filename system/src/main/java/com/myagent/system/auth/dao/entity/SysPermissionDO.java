package com.myagent.system.auth.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 平台权限码 */
@Data
@TableName("sys_permission")
public class SysPermissionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String permCode;
    private String permName;
    private String description;
    private String category;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
