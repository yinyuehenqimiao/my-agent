package com.myagent.erp.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** ERP MCP 调用方签发的短期 JWT 校验参数。私钥始终不进入 ERP。 */
@ConfigurationProperties(prefix = "erp.mcp.jwt")
public class ErpMcpJwtProperties {
    private String publicKey;
    private String issuer;
    private String audience;

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
}
