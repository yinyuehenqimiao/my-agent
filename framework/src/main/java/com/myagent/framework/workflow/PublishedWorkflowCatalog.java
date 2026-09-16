package com.myagent.framework.workflow;

/** Agent 与工作流模块之间的只读发布目录端口。 */
public interface PublishedWorkflowCatalog {
    boolean isPublished(String definitionKey);
}
