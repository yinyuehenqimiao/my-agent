package com.myagent.rag.core.mcp;

/** 从密钥服务或部署环境解析一次性发现令牌；接口本身不返回或持久化密钥。 */
public interface McpDiscoveryTokenProvider {
    String resolve(String secretRef);
}
