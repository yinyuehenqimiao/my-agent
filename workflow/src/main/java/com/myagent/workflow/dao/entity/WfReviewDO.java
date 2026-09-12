package com.myagent.workflow.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 版本审核记录，编辑者不得审核自己提交。 */
@Data
@TableName("wf_review")
public class WfReviewDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long versionId;
    private String reviewerId;
    /** APPROVE/REJECT */
    private String action;
    private String comment;
    private LocalDateTime createdTime;
}
