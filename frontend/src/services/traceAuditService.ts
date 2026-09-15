import { api } from "@/services/api";

export interface TraceRun {
  traceId: string;
  traceName?: string | null;
  entryMethod?: string | null;
  conversationId?: string | null;
  taskId?: string | null;
  userId?: string | null;
  username?: string | null;
  status: string;
  errorMessage?: string | null;
  durationMs?: number | null;
  question?: string | null;
  startTime?: string | null;
  endTime?: string | null;
}
export interface TraceNode {
  traceId?: string;
  nodeId: string;
  parentNodeId?: string | null;
  depth?: number | null;
  nodeType?: string | null;
  nodeName?: string | null;
  className?: string | null;
  methodName?: string | null;
  status: string;
  errorMessage?: string | null;
  durationMs?: number | null;
  startTime?: string | null;
  endTime?: string | null;
}
export interface TraceEvent {
  id: string;
  category: "PLATFORM" | "AUTHORIZATION" | "TOOL" | "BUSINESS";
  eventCode: string;
  actorId?: string | null;
  actorName?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  traceId: string;
  detail?: string | null;
  createdTime?: string | null;
}
export interface TraceDetail { run: TraceRun; nodes: TraceNode[]; events: TraceEvent[]; }
export interface TracePage { records: TraceRun[]; total: number; current: number; size: number; }
export interface TraceFilters {
  traceId?: string;
  conversationId?: string;
  taskId?: string;
  userId?: string;
  status?: string;
  current?: number;
  size?: number;
}
export interface TraceStats {
  total: number;
  successCount: number;
  errorCount: number;
  runningCount: number;
  cancelledCount: number;
  successRate: number;
}
export interface AuditHealth {
  status: "UP" | "DEGRADED";
  reasoningTraceWriteFailures: number;
  toolAuditWriteFailures: number;
  securityAuditWriteFailures: number;
}

function toParams(filters: TraceFilters = {}) {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && String(value).trim()) {
      params.set(key, String(value));
    }
  });
  return params.toString();
}

export const traceAuditService = {
  listRuns: (filters: TraceFilters = {}) =>
    api.get<TracePage>(`/rag/traces/runs?${toParams(filters)}`),
  getStats: (filters: TraceFilters = {}) => {
    const { current: _c, size: _s, ...rest } = filters;
    return api.get<TraceStats>(`/rag/traces/stats?${toParams(rest)}`);
  },
  getDetail: (traceId: string) =>
    api.get<TraceDetail>(`/rag/traces/runs/${encodeURIComponent(traceId)}`),
  getHealth: () => api.get<AuditHealth>("/api/admin/audit/health"),
};
