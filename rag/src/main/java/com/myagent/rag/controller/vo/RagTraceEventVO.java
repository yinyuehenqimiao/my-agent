package com.myagent.rag.controller.vo;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

/** 推理链路关联的跨域审计事件；不包含模型原文或工具敏感参数。 */
@Data
@Builder
public class RagTraceEventVO {
    private String id;
    private String category;
    private String eventCode;
    private String actorId;
    private String targetType;
    private String targetId;
    private String traceId;
    private String detail;
    private Timestamp createdTime;
}
