// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  listRuns: vi.fn(),
  getDetail: vi.fn(),
  getHealth: vi.fn(),
  getStats: vi.fn(),
}));

vi.mock("@/services/traceAuditService", () => ({
  traceAuditService: {
    listRuns: mocks.listRuns,
    getDetail: mocks.getDetail,
    getHealth: mocks.getHealth,
    getStats: mocks.getStats,
  },
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

import { TraceAuditPage } from "./TraceAuditPage";

describe("推理链路审计页", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listRuns.mockResolvedValue({
      records: [{
        traceId: "trace-1", question: "查询我的年假", userId: "1", username: "admin",
        conversationId: "conversation-1", taskId: "task-1", status: "SUCCESS",
        durationMs: 36, startTime: "2026-09-15T09:00:00",
      }],
      total: 1, current: 1, size: 20,
    });
    mocks.getStats.mockResolvedValue({
      total: 10, successCount: 8, errorCount: 2, runningCount: 0, cancelledCount: 0, successRate: 80,
    });
    mocks.getDetail.mockResolvedValue({
      run: {
        traceId: "trace-1", status: "SUCCESS", question: "查询我的年假", username: "admin", userId: "1",
        durationMs: 36, startTime: "2026-09-15T09:00:00.000Z", endTime: "2026-09-15T09:00:00.036Z",
      },
      nodes: [{
        nodeId: "node-1", nodeType: "INTENT", nodeName: "意图识别", status: "SUCCESS", depth: 0,
        durationMs: 8, startTime: "2026-09-15T09:00:00.002Z",
        resultSummary: "WORKFLOW → leave-approval (leave)",
        extraData: "{\"resultSummary\":\"WORKFLOW → leave-approval (leave)\"}",
      }],
      events: [{ id: "event-1", category: "TOOL", eventCode: "TOOL_EXECUTION", actorId: "1", actorName: "admin", targetType: "tool", targetId: "get_leave_balance", traceId: "trace-1", detail: "phase=COMPLETED", createdTime: "2026-09-15T09:00:01" }],
    });
    mocks.getHealth.mockResolvedValue({
      status: "DEGRADED", reasoningTraceWriteFailures: 2,
      toolAuditWriteFailures: 1, securityAuditWriteFailures: 3,
    });
  });
  afterEach(cleanup);

  it("按组合条件查询并展开同一 trace 的推理与跨域事件", async () => {
    render(<TraceAuditPage />);
    expect(await screen.findByText("查询我的年假")).toBeTruthy();
    expect(await screen.findByText("成功次数")).toBeTruthy();
    expect(screen.getByText("8")).toBeTruthy();

    fireEvent.change(screen.getByLabelText("链路标识"), { target: { value: "trace-1" } });
    fireEvent.change(screen.getByLabelText("用户标识"), { target: { value: "employee" } });
    fireEvent.click(screen.getByRole("button", { name: "查询" }));
    await waitFor(() => expect(mocks.listRuns).toHaveBeenLastCalledWith(expect.objectContaining({
      traceId: "trace-1", userId: "employee", current: 1,
    })));

    fireEvent.click(screen.getByRole("button", { name: "查看链路" }));
    expect((await screen.findAllByText("意图识别")).length).toBeGreaterThan(0);
    expect(screen.getByText(/get_leave_balance/)).toBeTruthy();
    expect(screen.getByText(/按墙钟对齐/)).toBeTruthy();
    expect(screen.getByText(/WORKFLOW → leave-approval/)).toBeTruthy();
    expect(screen.queryByText("链路完成度")).toBeNull();
    expect(screen.queryByText(/相对耗时瀑布图/)).toBeNull();
    expect(mocks.getDetail).toHaveBeenCalledWith("trace-1");
  });
});
