package com.myagent.knowledge.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 上传限制（对齐 ragent SystemSettingsVO.UploadSettings）
 */
@Data
@Builder
public class UploadLimitVO {

    /** 单文件上限（字节），对应 spring.servlet.multipart.max-file-size */
    private Long maxFileSize;

    /** 单次请求总大小上限（字节），对应 spring.servlet.multipart.max-request-size */
    private Long maxRequestSize;

    /** 上传并发上限，对应 rag.semaphore.document-upload.max-concurrent */
    private Integer maxConcurrentUploads;
}
