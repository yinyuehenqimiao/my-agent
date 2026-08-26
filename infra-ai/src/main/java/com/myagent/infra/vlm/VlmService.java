package com.myagent.infra.vlm;

/**
 * 视觉大模型（VLM）访问接口：入库期图生文
 */
public interface VlmService {

    String describeImage(byte[] imageBytes, String mime, String prompt, Integer maxOutputTokens);
}
