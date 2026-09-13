// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  listTools: vi.fn(),
  simulateRoute: vi.fn(),
  listRoutes: vi.fn(),
  getRouteOptions: vi.fn(),
  saveRoute: vi.fn(),
  deleteRoute: vi.fn(),
  listAudit: vi.fn(),
}));

vi.mock("@/services/agentAdminService", () => ({
  agentAdminService: {
    listTools: mocks.listTools,
    simulateRoute: mocks.simulateRoute,
    listRoutes: mocks.listRoutes,
    getRouteOptions: mocks.getRouteOptions,
    saveRoute: mocks.saveRoute,
    deleteRoute: mocks.deleteRoute,
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

vi.mock("@/services/workflowService", () => ({
  workflowService: { listAudit: mocks.listAudit },
  AUDIT_CATEGORY_LABEL: {
    ALL: "全部类型", PLATFORM: "平台操作", AUTHORIZATION: "权限判定", TOOL: "工具调用", BUSINESS: "业务流程",
  },
  AUDIT_EVENT_LABEL: { TOOL_EXECUTION: "工具执行", AUTH_DENY: "权限拒绝" },
}));

import { AuditPage, RouteSimulationPage, ToolCenterPage } from "./WorkflowAdminPages";

describe("工作流管理辅助页面", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listRoutes.mockResolvedValue([]);
    mocks.getRouteOptions.mockResolvedValue({
      intents: [{ value: "leave-approval", label: "请假申请" }],
      workflows: [{ value: "leave-approval", label: "请假审批" }],
      reactConfigs: [{ value: "default", label: "标准开放问答" }],
    });
    mocks.listAudit.mockResolvedValue([]);
  });
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
    expect(screen.getAllByText("请假审批").length).toBeGreaterThan(0);
  });

  it("管理员通过中文选项保存生产执行管线", async () => {
    mocks.saveRoute.mockResolvedValue({ routeKey: "leave-approval-route" });
    render(<RouteSimulationPage />);

    await screen.findByRole("option", { name: "请假申请" });
    fireEvent.change(screen.getByLabelText("业务意图"), { target: { value: "leave-approval" } });
    fireEvent.change(screen.getByLabelText("执行目标"), { target: { value: "leave-approval" } });
    fireEvent.click(screen.getByRole("button", { name: "保存执行管线" }));

    await waitFor(() => expect(mocks.saveRoute).toHaveBeenCalledWith("leave-approval-route", {
      intentCode: "leave-approval",
      targetType: "WORKFLOW",
      target: "leave-approval",
    }));
  });

  it("统一审计展示中文事件并按分类重新查询", async () => {
    mocks.listAudit.mockResolvedValue([{
      id: "9", category: "TOOL", eventCode: "TOOL_EXECUTION", actorId: "employee",
      targetType: "tool", targetId: "get_leave_balance", traceId: "trace-9",
      detail: "phase=COMPLETED", createdTime: "2026-09-13T11:00:00",
    }]);
    render(<AuditPage />);

    expect(await screen.findByText("工具执行")).toBeTruthy();
    expect(screen.getAllByText("工具调用").length).toBeGreaterThan(0);
    expect(screen.getByText("trace-9")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("审计分类"), { target: { value: "AUTHORIZATION" } });
    await waitFor(() => expect(mocks.listAudit).toHaveBeenLastCalledWith(100, "AUTHORIZATION"));
  });
});
