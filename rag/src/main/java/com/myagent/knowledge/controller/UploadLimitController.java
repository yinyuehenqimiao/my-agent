package com.myagent.knowledge.controller;

import com.myagent.framework.convention.Result;
import com.myagent.framework.web.Results;
import com.myagent.knowledge.config.RagSemaphoreProperties;
import com.myagent.knowledge.controller.vo.UploadLimitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传限制只读接口，供前端做选择文件前的本地校验（对齐 ragent /rag/settings 中的 upload 段）
 */
@RestController
@RequiredArgsConstructor
public class UploadLimitController {

    private final RagSemaphoreProperties semaphoreProperties;

    @Value("${spring.servlet.multipart.max-file-size:50MB}")
    private DataSize maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:100MB}")
    private DataSize maxRequestSize;

    @GetMapping("/knowledge-base/upload-limits")
    public Result<UploadLimitVO> uploadLimits() {
        return Results.success(UploadLimitVO.builder()
                .maxFileSize(maxFileSize.toBytes())
                .maxRequestSize(maxRequestSize.toBytes())
                .maxConcurrentUploads(semaphoreProperties.getDocumentUpload().getMaxConcurrent())
                .build());
    }
}
