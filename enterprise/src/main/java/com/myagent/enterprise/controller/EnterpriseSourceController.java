package com.myagent.enterprise.controller;

import com.myagent.enterprise.dao.mapper.EnterpriseMapper;
import com.myagent.enterprise.service.KnowledgeAccessService;
import com.myagent.framework.convention.Result;
import com.myagent.framework.exception.ClientException;
import com.myagent.framework.web.Results;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name="myagent.enterprise.enabled", havingValue="true")
public class EnterpriseSourceController {
    private final KnowledgeAccessService access;
    private final EnterpriseMapper db;
    public record Source(String docName, int chunkIndex, String content) { }
    @GetMapping("/api/enterprise/knowledge/sources/{docId}/{chunkId}")
    public Result<Source> source(@PathVariable String docId,@PathVariable String chunkId) {
        access.requireDocument(docId);
        Source source = db.one(Source.class,"SELECT d.doc_name,c.chunk_index,c.content FROM t_knowledge_chunk c JOIN t_knowledge_document d ON d.id=c.doc_id WHERE c.id=? AND c.doc_id=? AND c.deleted=0 AND c.enabled=1",chunkId,docId);
        if (source==null) throw new ClientException("来源片段已失效");
        return Results.success(source);
    }
}
