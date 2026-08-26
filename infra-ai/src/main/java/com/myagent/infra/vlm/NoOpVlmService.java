package com.myagent.infra.vlm;

import com.myagent.framework.exception.ServiceException;
import org.springframework.stereotype.Service;

@Service
public class NoOpVlmService implements VlmService {

    @Override
    public String describeImage(byte[] imageBytes, String mime, String prompt, Integer maxOutputTokens) {
        throw new ServiceException("暂未配置 VLM 服务，无法解析图片");
    }
}
