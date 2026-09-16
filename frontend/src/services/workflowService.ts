import { api } from "@/services/api";

export type WfVersionStatus =
  | "DRAFT"
  | "IN_REVIEW"
  | "APPROVED"
  | "PUBLISHED"
  | "REJECTED"
  | "RETIRED";

export interface WfDefinition {
  id: number;
  defKey: string;
  name: string;
  description?: string | null;
  currentPublishedVersionId?: number | null;
  publishedVersionNumber?: number | null;
  latestVersionNumber?: number | null;
  latestStatus?: WfVersionStatus | null;
  latestCreatedBy?: string | null;
  latestCreatedByName?: string | null;
  updateTime?: string | null;
}

export interface WfVersion {
  id: number;
  versionNumber: number;
  status: WfVersionStatus;
  createdBy: string;
  createdByName?: string | null;
  createdTime: string;
  publishedTime?: string | null;
  publishedBy?: string | null;
  publishedByName?: string | null;
  bpmnXml?: string | null;
  processJson?: string | null;
  contentHash?: string | null;
  lockVersion?: number;
  sourceType?: "PROCESS_JSON" | "LEGACY_BPMN";
}

export interface WfDefinitionDetail {
  definition: WfDefinition;
  versions: WfVersion[];
}

export interface CreateDefinitionPayload {
  defKey: string;
  name: string;
  description?: string;
  bpmnXml: string;
}

export interface NewVersionPayload {
  sourceVersionId: number;
  bpmnXml: string;
}

export interface ReviewPayload {
  approve: boolean;
  comment?: string;
}

export interface WorkflowProcess {
  schemaVersion: number;
  nodes: Array<{ id: string; type: string; version?: number; position?: { x: number; y: number }; config?: Record<string, unknown> }>;
  edges: Array<{ id: string; source: string; target: string; outlet?: string; condition?: { field: string; operator: string; value: string } }>;
  variables: Array<{ name: string; type: string; source?: string; sensitivity?: string }>;
  dependencies: Array<{ kind: string; connectorId?: string; resourceId: string; contractVersion?: string; contentHash?: string }>;
}

export interface AuditEntry {
  id: string;
  category: AuditCategory;
  eventCode: string;
  actorId?: string | null;
  actorName?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  traceId?: string | null;
  detail?: string | null;
  createdTime: string;
}

export type AuditCategory = "ALL" | "PLATFORM" | "AUTHORIZATION" | "TOOL" | "BUSINESS";

export interface PageResult<T> {
  records: T[];
  total: number;
  current: number;
  size: number;
}

export const AUDIT_CATEGORY_LABEL: Record<AuditCategory, string> = {
  ALL: "全部类型",
  PLATFORM: "平台操作",
  AUTHORIZATION: "权限判定",
  TOOL: "工具调用",
  BUSINESS: "业务流程"
};

export const AUDIT_EVENT_LABEL: Record<string, string> = {
  WF_CREATE: "创建工作流",
  WF_SUBMIT_REVIEW: "提交审核",
  WF_APPROVE: "审核通过",
  WF_REJECT: "审核驳回",
  WF_PUBLISH: "发布工作流",
  WF_RETIRE: "停用工作流",
  WF_ROLLBACK: "回滚工作流",
  TOOL_EXECUTION: "工具执行",
  AUTH_ALLOW: "权限允许",
  AUTH_DENY: "权限拒绝",
  CREATED: "业务单据已创建",
  APPROVED: "业务审批通过",
  REJECTED: "业务审批驳回",
  CANCELLED: "业务单据已取消"
};

export const WF_STATUS_LABEL: Record<WfVersionStatus, string> = {
  DRAFT: "草稿",
  IN_REVIEW: "待审核",
  APPROVED: "审核通过",
  PUBLISHED: "已发布",
  REJECTED: "已驳回",
  RETIRED: "已停用"
};

export const workflowService = {
  listDefinitions: (params: { current?: number; size?: number; keyword?: string } = {}) => {
    const query = new URLSearchParams();
    query.set("current", String(params.current ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    return api.get<PageResult<WfDefinition>>(`/api/workflow/definitions?${query.toString()}`);
  },

  getDefinition: (defKey: string) =>
    api.get<WfDefinitionDetail>(`/api/workflow/definitions/${defKey}`),

  createDefinition: (payload: CreateDefinitionPayload) =>
    api.post<number>("/api/workflow/definitions", payload),

  newVersion: (payload: NewVersionPayload) =>
    api.post<number>("/api/workflow/versions", payload),

  compile: (defKey: string, process: WorkflowProcess) =>
    api.post<{ bpmnXml: string; contentHash: string }>("/api/workflow/compile", { defKey, process }),

  createProcessDefinition: (payload: { defKey: string; name: string; description?: string; process: WorkflowProcess }) =>
    api.post<{ versionId: number; lockVersion: number; contentHash: string }>("/api/workflow/process-definitions", payload),

  saveProcessVersion: (versionId: number, lockVersion: number, process: WorkflowProcess) =>
    api.put<{ versionId: number; lockVersion: number; contentHash: string }>(`/api/workflow/process-versions/${versionId}`, { lockVersion, process }),

  validateProcess: (defKey: string, process: WorkflowProcess) =>
    api.post<{ bpmnXml: string; contentHash: string }>("/api/workflow/processes/validate", { defKey, process }),

  submitReview: (versionId: number) =>
    api.post<void>(`/api/workflow/versions/${versionId}/submit-review`),

  review: (versionId: number, payload: ReviewPayload) =>
    api.post<void>(`/api/workflow/versions/${versionId}/review`, payload),

  publish: (versionId: number) =>
    api.post<void>(`/api/workflow/versions/${versionId}/publish`),

  retire: (versionId: number) =>
    api.post<void>(`/api/workflow/versions/${versionId}/retire`),

  rollback: (defId: number, versionId: number) =>
    api.post<void>(`/api/workflow/definitions/${defId}/rollback/${versionId}`),

  listAudit: (params: { current?: number; size?: number; category?: AuditCategory } = {}) => {
    const query = new URLSearchParams();
    query.set("current", String(params.current ?? 1));
    query.set("size", String(params.size ?? 20));
    query.set("category", params.category ?? "ALL");
    return api.get<PageResult<AuditEntry>>(`/api/workflow/audit?${query.toString()}`);
  }
};
