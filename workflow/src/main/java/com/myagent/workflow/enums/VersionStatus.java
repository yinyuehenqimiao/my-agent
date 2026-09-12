package com.myagent.workflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 工作流版本状态。发布版本不可编辑，修改创建新版本。 */
@Getter
@RequiredArgsConstructor
public enum VersionStatus {
    DRAFT("草稿"),
    IN_REVIEW("审核中"),
    APPROVED("已批准"),
    PUBLISHED("已发布"),
    REJECTED("已驳回"),
    RETIRED("已退休");

    private final String label;
}
