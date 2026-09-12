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
  id: number;
  operation: string;
  operatorId?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  traceId?: string | null;
  detail?: string | null;
  createdTime: string;
}

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

  listAudit: (limit = 100) =>
    api.get<AuditEntry[]>(`/api/workflow/audit?limit=${limit}`)
};
