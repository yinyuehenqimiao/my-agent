import { api } from "@/services/api";

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
  listTools: () => api.get<ToolCatalogItem[]>("/api/agent/admin/tools"),
  simulateRoute: (payload: RouteSimulationRequest) =>
    api.post<RouteSimulationResult>("/api/agent/admin/routes/simulate", payload),
};
