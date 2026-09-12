package com.myagent.knowledge.config;

import com.myagent.enterprise.service.KnowledgeAccessService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Map;

/** Runs after authentication. Preview/file/chunks resolve the real owning KB, not client claims. */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name="myagent.enterprise.enabled", havingValue="true")
public class EnterpriseKnowledgeGuard implements WebMvcConfigurer {
    private final KnowledgeAccessService access;
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                if ("OPTIONS".equals(req.getMethod())) return true;
                if (!"GET".equals(req.getMethod())) { access.requireAdmin(); return true; }
                Object attr = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Map<?,?> vars = attr instanceof Map<?,?> map ? map : Map.of();
                Object doc = vars.containsKey("docId") ? vars.get("docId") : vars.get("doc-id");
                Object kb = vars.get("kb-id");
                if (doc != null) access.requireDocument(doc.toString());
                else if (kb != null) access.requireKb(kb.toString());
                else access.requireAdmin(); // unscoped management/search endpoints
                return true;
            }
        }).addPathPatterns("/knowledge-base", "/knowledge-base/**").order(30);
    }
}
