import { api } from "@/services/api";

export type McpConnectorStatus = "DRAFT" | "DISCOVERED" | string;
export type McpToolReviewStatus = "PENDING_REVIEW" | "APPROVED" | "DISABLED" | string;
export type McpBindingStatus = "DRAFT" | "PUBLISHED" | string;

export interface McpConnector {
  tenantId: string;
  connectorId: string;
  displayName: string;
  endpoint: string;
  status: McpConnectorStatus;
  updatedAt?: string;
}

export interface McpCatalogTool {
  toolId: string;
  title: string;
  description: string;
  reviewStatus: McpToolReviewStatus;
  enabled: boolean;
  discoveredAt?: string;
}

export interface McpConnectorInput {
  connectorId: string;
  displayName: string;
  endpoint: string;
  secretRef: string;
}

export interface McpIntentBinding {
  intentCode: string;
  connectorId: string;
  toolId: string;
  status: McpBindingStatus;
  createdBy?: string;
  updatedAt?: string;
}

export interface McpIntentBindingInput {
  intentCode: string;
  connectorId: string;
  toolId: string;
}

const connectorBase = "/api/rag/admin/mcp-connectors";
const bindingBase = "/api/rag/admin/mcp-intent-bindings";

export const mcpConnectorService = {
  list: () => api.get<never, McpConnector[]>(connectorBase),
  save: (input: McpConnectorInput) => api.put<never, void>(connectorBase, input),
  discover: (connectorId: string) =>
    api.post<never, McpCatalogTool[]>(`${connectorBase}/${encodeURIComponent(connectorId)}/discover`),
  tools: (connectorId: string) =>
    api.get<never, McpCatalogTool[]>(`${connectorBase}/${encodeURIComponent(connectorId)}/tools`),
  review: (connectorId: string, toolId: string, reviewStatus: "APPROVED" | "DISABLED", enabled: boolean) =>
    api.put<never, void>(
      `${connectorBase}/${encodeURIComponent(connectorId)}/tools/${encodeURIComponent(toolId)}/review`,
      { reviewStatus, enabled }
    )
};

export const mcpIntentBindingService = {
  list: () => api.get<never, McpIntentBinding[]>(bindingBase),
  bind: (input: McpIntentBindingInput) => api.put<never, void>(bindingBase, input),
  publish: (intentCode: string) =>
    api.post<never, void>(`${bindingBase}/${encodeURIComponent(intentCode)}/publish`)
};
