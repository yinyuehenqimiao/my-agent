// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ listRuns: vi.fn(), getDetail: vi.fn(), getHealth: vi.fn() }));

vi.mock("@/services/traceAuditService", () => ({
  traceAuditService: { listRuns: mocks.listRuns, getDetail: mocks.getDetail, getHealth: mocks.getHealth },
}));
vi.mock("sonner", () => ({ toast: { error: vi.fn() } }));

import { TraceAuditPage } from "./TraceAuditPage";

describe("推理链路审计页", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listRuns.mockResolvedValue({
      records: [{
        traceId: "trace-1", question: "查询我的年假", userId: "employee",
        conversationId: "conversation-1", taskId: "task-1", status: "SUCCESS",
        durationMs: 36, startTime: "2026-09-15T09:00:00",
      }],
      total: 1, current: 1, size: 20,
    });
    mocks.getDetail.mockResolvedValue({
      run: { traceId: "trace-1", status: "SUCCESS", question: "查询我的年假" },
      nodes: [{ nodeId: "node-1", nodeType: "INTENT", nodeName: "意图识别", status: "SUCCESS", depth: 0, durationMs: 8 }],
      events: [{ id: "event-1", category: "TOOL", eventCode: "TOOL_EXECUTION", actorId: "employee", targetType: "tool", targetId: "get_leave_balance", traceId: "trace-1", detail: "phase=COMPLETED", createdTime: "2026-09-15T09:00:01" }],
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
    expect(await screen.findByText("审计持久化异常")).toBeTruthy();
    expect(screen.getByText("推理 2")).toBeTruthy();

    fireEvent.change(screen.getByLabelText("链路标识"), { target: { value: "trace-1" } });
    fireEvent.change(screen.getByLabelText("用户标识"), { target: { value: "employee" } });
    fireEvent.click(screen.getByRole("button", { name: "查询" }));
    await waitFor(() => expect(mocks.listRuns).toHaveBeenLastCalledWith(expect.objectContaining({
      traceId: "trace-1", userId: "employee", current: 1, size: 20,
    })));

    fireEvent.click(screen.getByRole("button", { name: "查看链路" }));
    expect(await screen.findByText("意图识别")).toBeTruthy();
    expect(screen.getByText("get_leave_balance")).toBeTruthy();
    expect(mocks.getDetail).toHaveBeenCalledWith("trace-1");
  });
});
