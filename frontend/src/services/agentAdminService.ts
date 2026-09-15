import { api } from "@/services/api";
import type { PageResult } from "@/services/workflowService";

export type ToolRiskLevel = "LOW" | "MEDIUM" | "HIGH";
export type ToolSource = "REACT" | "WORKFLOW" | "MCP" | "ADMIN";
export type RouteDecision = "WORKFLOW" | "REACT" | "SYSTEM" | "CLARIFY" | "DENY";
export type IntentKind = "KB" | "SYSTEM" | "MCP" | "WORKFLOW";

export interface ToolCatalogItem {
  name: string;
  description?: string | null;
  inputSchema?: Record<string, unknown> | null;
  outputSchema?: Record<string, unknown> | null;
  readOnly: boolean;
  riskLevel: ToolRiskLevel;
  timeoutMs: number;
  maxRetries: number;
  idempotent: boolean;
  requiresConfirmation: boolean;
  sensitiveFields?: string[] | null;
  allowedSources?: ToolSource[] | null;
  permissionCode?: string | null;
  enabled: boolean;
}

export interface RouteSimulationRequest {
  question: string;
  hasPendingAction: boolean;
}

export interface RouteSimulationResult {
  intentCode?: string | null;
  intentName?: string | null;
  intentKind?: IntentKind | null;
  confidence: number;
  decision: RouteDecision;
  target?: string | null;
  reason: string;
}

export type RouteTargetType = "WORKFLOW" | "REACT";

export interface RouteBinding {
  routeKey: string;
  intentCode: string;
  intentName?: string | null;
  targetType: RouteTargetType;
  target: string;
  updateTime?: string | null;
}

export interface RouteOption {
  value: string;
  label: string;
}

export interface RouteOptions {
  intents: RouteOption[];
  workflows: RouteOption[];
  reactConfigs: RouteOption[];
}

export interface SaveRouteRequest {
  intentCode: string;
  targetType: RouteTargetType;
  target: string;
}

export const TOOL_RISK_LABEL: Record<ToolRiskLevel, string> = {
  LOW: "低风险",
  MEDIUM: "中风险",
  HIGH: "高风险",
};

export const ROUTE_DECISION_LABEL: Record<RouteDecision, string> = {
  WORKFLOW: "固定流程",
  REACT: "开放式智能体",
  SYSTEM: "系统或知识库回答",
  CLARIFY: "需要补充信息",
  DENY: "拒绝执行",
};

export const agentAdminService = {
  listTools: (params: { current?: number; size?: number; keyword?: string } = {}) => {
    const query = new URLSearchParams();
    query.set("current", String(params.current ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    return api.get<PageResult<ToolCatalogItem>>(`/api/agent/admin/tools?${query.toString()}`);
  },
  listRoutes: (params: { current?: number; size?: number; keyword?: string } = {}) => {
    const query = new URLSearchParams();
    query.set("current", String(params.current ?? 1));
    query.set("size", String(params.size ?? 20));
    if (params.keyword?.trim()) query.set("keyword", params.keyword.trim());
    return api.get<PageResult<RouteBinding>>(`/api/agent/admin/routes?${query.toString()}`);
  },
  getRouteOptions: () => api.get<RouteOptions>("/api/agent/admin/routes/options"),
  saveRoute: (routeKey: string, payload: SaveRouteRequest) =>
    api.put<RouteBinding>(`/api/agent/admin/routes/${encodeURIComponent(routeKey)}`, payload),
  deleteRoute: (routeKey: string) =>
    api.delete<void>(`/api/agent/admin/routes/${encodeURIComponent(routeKey)}`),
  simulateRoute: (payload: RouteSimulationRequest) =>
    api.post<RouteSimulationResult>("/api/agent/admin/routes/simulate", payload),
};
