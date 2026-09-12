// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  listTools: vi.fn(),
  simulateRoute: vi.fn(),
}));

vi.mock("@/services/agentAdminService", () => ({
  agentAdminService: {
    listTools: mocks.listTools,
    simulateRoute: mocks.simulateRoute,
  },
  ROUTE_DECISION_LABEL: {
    WORKFLOW: "固定流程",
    REACT: "开放式智能体",
    SYSTEM: "系统或知识库回答",
    CLARIFY: "需要补充信息",
    DENY: "拒绝执行",
  },
  TOOL_RISK_LABEL: { LOW: "低风险", MEDIUM: "中风险", HIGH: "高风险" },
}));

vi.mock("sonner", () => ({ toast: { error: vi.fn(), success: vi.fn() } }));

import { RouteSimulationPage, ToolCenterPage } from "./WorkflowAdminPages";

describe("工作流管理辅助页面", () => {
  beforeEach(() => vi.clearAllMocks());
  afterEach(cleanup);

  it("工具中心展示 Tool Gateway 返回的真实中文工具信息", async () => {
    mocks.listTools.mockResolvedValue([{
      name: "get_leave_balance",
      description: "查询本人年假余额",
      readOnly: true,
      riskLevel: "LOW",
      timeoutMs: 5000,
      maxRetries: 0,
      idempotent: false,
      requiresConfirmation: false,
      sensitiveFields: [],
      allowedSources: ["REACT", "WORKFLOW"],
      permissionCode: null,
      enabled: true,
      inputSchema: { type: "object" },
    }]);

    render(<ToolCenterPage />);

    expect(await screen.findByText("查询本人年假余额")).toBeTruthy();
    expect(screen.getByText("低风险")).toBeTruthy();
    expect(screen.getByText("只读")).toBeTruthy();
    expect(mocks.listTools).toHaveBeenCalledTimes(1);
  });

  it("路由模拟调用后端真实决策而不是前端关键词判断", async () => {
    mocks.simulateRoute.mockResolvedValue({
      intentCode: "leave-approval",
      intentName: "请假审批",
      intentKind: "WORKFLOW",
      confidence: 0.93,
      decision: "WORKFLOW",
      target: "leave-approval",
      reason: "高置信固定流程",
    });

    render(<RouteSimulationPage />);
    fireEvent.change(screen.getByPlaceholderText("例如：我今年还剩多少天年假？"), {
      target: { value: "我想请年假" },
    });
    fireEvent.click(screen.getByRole("button", { name: "开始模拟" }));

    await waitFor(() => expect(mocks.simulateRoute).toHaveBeenCalledWith({
      question: "我想请年假",
      hasPendingAction: false,
    }));
    expect(await screen.findByText("固定流程")).toBeTruthy();
    expect(screen.getByText("请假审批")).toBeTruthy();
  });
});
