package com.myagent.system.auth.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 鉴权审计记录，所有权限拒绝写入 */
@Data
@TableName("sys_auth_audit")
public class SysAuthAuditDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String username;
    private String tenantId;
    private String permCode;
    /** DENY / GRANT */
    private String action;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
}
