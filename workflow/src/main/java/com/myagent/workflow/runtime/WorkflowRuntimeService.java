package com.myagent.workflow.runtime;

import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.enums.VersionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工作流运行时：基于 Flowable，实例固定绑定已发布版本。
 * - 每个已发布版本部署为独立流程定义（key 含版本 id），实例按该 key 启动，确保绑定特定版本。
 * - 实例携带 tenantId、initiatorId、businessKey、traceId；流程变量只保存必要业务引用。
 * - 应用重启后 Flowable 持久化保证流程恢复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuntimeService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final JdbcTemplate jdbc;

    /** 已发布版本的流程定义 key（含版本 id，保证绑定特定版本） */
    public static String processDefinitionKey(Long definitionId, Long versionId) {
        return "wf-" + definitionId + "-v" + versionId;
    }

    /** 启动流程实例，绑定指定已发布版本 */
    public String startInstance(String defKey, Long versionId, String initiatorId,
                                 String businessKey, String traceId, Map<String, Object> variables) {
        Long defId = findDefinitionId(defKey);
        Map<String, Object> row = jdbc.queryForMap("SELECT id,definition_id,status,bpmn_xml FROM wf_version WHERE id=?", versionId);
        Long vDefId = ((Number) row.get("definition_id")).longValue();
        if (!vDefId.equals(defId)) throw new ClientException("版本不属于该定义");
        if (!VersionStatus.PUBLISHED.name().equals(row.get("status")))
            throw new ClientException("仅可启动已发布版本");
        String bpmnXml = (String) row.get("bpmn_xml");
        ensureDeployed(defKey, defId, versionId, bpmnXml);
        String procDefKey = processDefinitionKey(defId, versionId);
        Map<String, Object> vars = variables == null ? new HashMap<>() : new HashMap<>(variables);
        vars.putIfAbsent("tenantId", "default");
        vars.put("initiatorId", initiatorId);
        vars.put("traceId", traceId == null ? UUID.randomUUID().toString() : traceId);
        vars.put("versionId", versionId);
        ProcessInstance instance = runtimeService.startProcessInstanceByKey(procDefKey, businessKey, vars);
        log.info("workflow instance started def={} version={} instance={} initiator={}", defKey, versionId, instance.getId(), initiatorId);
        return instance.getId();
    }

    /**
     * 启动当前已发布版本。调用方只需要提供业务定义键，不需要感知版本号，
     * 版本切换由发布门禁统一控制。
     */
    public String startCurrentPublishedInstance(String defKey, String initiatorId,
                                                 String businessKey, String traceId,
                                                 Map<String, Object> variables) {
        Long versionId = jdbc.queryForObject(
                "SELECT current_published_version_id FROM wf_definition WHERE def_key=?",
                Long.class, defKey);
        if (versionId == null) {
            throw new ClientException("工作流没有已发布版本：" + defKey);
        }
        return startInstance(defKey, versionId, initiatorId, businessKey, traceId, variables);
    }

    /** 确保已发布版本已部署到 Flowable（幂等） */
    private void ensureDeployed(String defKey, Long defId, Long versionId, String bpmnXml) {
        String procDefKey = processDefinitionKey(defId, versionId);
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(procDefKey).latestVersion().singleResult();
        if (pd != null) return;
        Deployment dep = repositoryService.createDeployment()
                .name("wf-" + defKey + "-v" + versionId)
                .key("wf-deploy-" + versionId)
                .addString(procDefKey + ".bpmn20.xml", bpmnXml)
                .deploy();
        log.info("workflow deployed def={} version={} deployment={}", defKey, versionId, dep.getId());
    }

    private Long findDefinitionId(String defKey) {
        Integer cnt = jdbc.queryForObject("SELECT count(*) FROM wf_definition WHERE def_key=?", Integer.class, defKey);
        if (cnt == null || cnt == 0) throw new ClientException("工作流定义不存在：" + defKey);
        return jdbc.queryForObject("SELECT id FROM wf_definition WHERE def_key=?", Long.class, defKey);
    }
}
