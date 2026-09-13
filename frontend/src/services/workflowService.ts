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
  updateTime?: string | null;
}

export interface WfVersion {
  id: number;
  versionNumber: number;
  status: WfVersionStatus;
  createdBy: string;
  createdTime: string;
  publishedTime?: string | null;
  publishedBy?: string | null;
  bpmnXml?: string | null;
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

export interface AuditEntry {
  id: string;
  category: AuditCategory;
  eventCode: string;
  actorId?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  traceId?: string | null;
  detail?: string | null;
  createdTime: string;
}

export type AuditCategory = "ALL" | "PLATFORM" | "AUTHORIZATION" | "TOOL" | "BUSINESS";

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
  listDefinitions: () =>
    api.get<WfDefinition[]>("/api/workflow/definitions"),

  getDefinition: (defKey: string) =>
    api.get<WfDefinitionDetail>(`/api/workflow/definitions/${defKey}`),

  createDefinition: (payload: CreateDefinitionPayload) =>
    api.post<number>("/api/workflow/definitions", payload),

  newVersion: (payload: NewVersionPayload) =>
    api.post<number>("/api/workflow/versions", payload),

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

  listAudit: (limit = 100, category: AuditCategory = "ALL") =>
    api.get<AuditEntry[]>(`/api/workflow/audit?limit=${limit}&category=${encodeURIComponent(category)}`)
};
