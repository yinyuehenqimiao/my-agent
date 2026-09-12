package com.myagent.workflow.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作流版本，发布后不可编辑，修改创建新版本。 */
@Data
@TableName("wf_version")
public class WfVersionDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long definitionId;
    private Integer versionNumber;
    private String bpmnXml;
    /** DRAFT/IN_REVIEW/APPROVED/PUBLISHED/REJECTED/RETIRED */
    private String status;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    private LocalDateTime publishedTime;
    private String publishedBy;
    private Long parentVersionId;
}
