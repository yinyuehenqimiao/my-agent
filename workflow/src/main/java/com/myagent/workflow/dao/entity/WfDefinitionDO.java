package com.myagent.workflow.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作流定义，按业务键唯一。 */
@Data
@TableName("wf_definition")
public class WfDefinitionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String defKey;
    private String name;
    private String description;
    private String tenantId;
    private Long currentPublishedVersionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
