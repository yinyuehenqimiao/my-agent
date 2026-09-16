package com.myagent.erp.mcp;

import com.myagent.erp.identity.ErpIdentity;
import com.myagent.erp.identity.UnauthorizedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "erp.mcp.jwt.public-key")
public class ErpMcpJwtVerifier {
    private final ErpMcpJwtProperties properties;
    private final PublicKey publicKey;

    public ErpMcpJwtVerifier(ErpMcpJwtProperties properties) {
        this.properties = properties;
        this.publicKey = readPublicKey(properties.getPublicKey());
    }

    ErpMcpIdentity verifyBearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new UnauthorizedException("MCP 调用缺少服务身份令牌");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .requireAudience(properties.getAudience())
                    .build().parseSignedClaims(authorization.substring(7)).getPayload();
            String employeeId = claims.getSubject();
            String tenantId = required(claims, "tenant_id");
            String toolId = required(claims, "tool_id");
            String traceId = required(claims, "trace_id");
            if (employeeId == null || employeeId.isBlank()) throw new UnauthorizedException("MCP 员工身份无效");
            return new ErpMcpIdentity(new ErpIdentity(tenantId, employeeId, traceId), toolId);
        } catch (UnauthorizedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("MCP 服务身份令牌无效");
        }
    }

    private static String required(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) throw new UnauthorizedException("MCP 服务身份令牌缺少必要范围");
        return value;
    }

    private static PublicKey readPublicKey(String encoded) {
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("ERP MCP 公钥配置无效", exception);
        }
    }
}
