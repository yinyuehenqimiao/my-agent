package com.myagent.enterprise.service;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.dto.EnterpriseTypes.*;
import com.myagent.framework.context.LoginUser;
import com.myagent.framework.context.UserContext;
import com.myagent.framework.exception.ClientException;
import com.myagent.system.auth.service.PolicyDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Deny by default. No grant means an existing KB is visible only to users with knowledge:authorize. */
@Service
@RequiredArgsConstructor
public class KnowledgeAccessService {
    private final EnterpriseMapper db;
    private final PolicyDecisionService policy;
    @Value("${myagent.enterprise.enabled:false}") private boolean enabled;
    public boolean enabled() { return enabled; }
    public LoginUser identity(String userId) {
        LoginUser user = db.one(LoginUser.class, "SELECT CAST(id AS VARCHAR) AS user_id,username,role FROM t_user WHERE CAST(id AS VARCHAR)=? AND deleted=0", userId);
        if (user == null) throw new ClientException("登录身份已失效");
        return user;
    }
    /** 知识授权需要 knowledge:authorize 权限码（替代旧 admin 字符串判断） */
    public void requireAdmin() {
        policy.requirePermission(UserContext.requireIdentity(), "knowledge:authorize");
    }
    public List<String> allowedCollections() {
        if (!enabled) return null;
        LoginUser user = UserContext.requireUser();
        record Collection(String collectionName) { }
        // 拥有 knowledge:authorize 权限可读全部知识库（替代旧 admin 旁路）
        if (policy.hasPermission(user, "knowledge:authorize")) {
            return db.list(Collection.class, "SELECT collection_name FROM t_knowledge_base WHERE deleted=0")
                    .stream().map(Collection::collectionName).toList();
        }
        Employee e = db.employee(user.getUserId(), false);
        if (e == null || !e.active()) return List.of();
        return db.list(Collection.class, "SELECT DISTINCT k.collection_name FROM t_knowledge_base k JOIN ent_kb_grant g ON g.kb_id=k.id WHERE k.deleted=0 AND (g.audience='ALL' OR (g.audience='DEPARTMENT' AND g.subject=?) OR (g.audience='ROLE' AND g.subject=?))", e.department(), e.businessRole()).stream().map(Collection::collectionName).toList();
    }
    public void requireKb(String kbId) {
        if (!enabled) return;
        record Kb(String collectionName) { }
        Kb kb = db.one(Kb.class, "SELECT collection_name FROM t_knowledge_base WHERE id=? AND deleted=0", kbId);
        if (kb == null || !allowedCollections().contains(kb.collectionName())) throw new ClientException("知识库不存在或无权访问");
    }
    public void requireDocument(String docId) {
        if (!enabled) return;
        record Doc(String kbId, int enabled) { }
        Doc doc = db.one(Doc.class, "SELECT kb_id,enabled FROM t_knowledge_document WHERE id=? AND deleted=0", docId);
        if (doc == null) throw new ClientException("文档不存在或无权访问");
        requireKb(doc.kbId());
        // 仅 knowledge:authorize 可读停用文档（替代旧 admin 判断）
        if (doc.enabled() != 1 && !policy.hasPermission(UserContext.requireIdentity(), "knowledge:authorize")) {
            throw new ClientException("文档已停用");
        }
    }
    public Policy policy(String docId) { return db.one(Policy.class, "SELECT version,effective_date FROM ent_document_policy WHERE doc_id=?", docId); }
    @Transactional public void grants(String kbId, List<Grant> grants) {
        requireAdmin(); requireKb(kbId);
        if (grants == null || grants.size() > 100) throw new ClientException("授权条目不能超过 100 条");
        for (Grant g : grants) {
            if (g == null || g.audience() == null || !java.util.Set.of("ALL","DEPARTMENT","ROLE").contains(g.audience())
                    || g.subject() == null || g.subject().isBlank() || g.subject().length()>100) throw new ClientException("授权配置不合法");
            if ("ROLE".equals(g.audience()) && !java.util.Set.of("EMPLOYEE","MANAGER","HR").contains(g.subject())) throw new ClientException("企业角色不合法");
        }
        db.update("DELETE FROM ent_kb_grant WHERE kb_id=?", kbId);
        for (Grant g : grants) db.update("INSERT INTO ent_kb_grant(kb_id,audience,subject) VALUES(?,?,?)", kbId, g.audience(), g.subject());
    }
}
