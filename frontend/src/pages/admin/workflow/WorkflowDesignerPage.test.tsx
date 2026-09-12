// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  createDefinition: vi.fn(),
  newVersion: vi.fn(),
  getDefinition: vi.fn(),
  submitReview: vi.fn(),
  importXML: vi.fn(),
  saveXML: vi.fn(),
}));

vi.mock("bpmn-js/lib/Modeler", () => ({
  default: class MockBpmnModeler {
    importXML = mocks.importXML;
    saveXML = mocks.saveXML;
    get = vi.fn().mockReturnValue({ zoom: vi.fn() });
    destroy = vi.fn();
  },
}));

vi.mock("@/services/workflowService", () => ({
  WF_STATUS_LABEL: {
    DRAFT: "草稿",
    IN_REVIEW: "待审核",
    APPROVED: "审核通过",
    PUBLISHED: "已发布",
    REJECTED: "已驳回",
    RETIRED: "已停用",
  },
  workflowService: {
    createDefinition: mocks.createDefinition,
    newVersion: mocks.newVersion,
    getDefinition: mocks.getDefinition,
    submitReview: mocks.submitReview,
    review: vi.fn(),
    publish: vi.fn(),
    retire: vi.fn(),
    rollback: vi.fn(),
  },
}));

vi.mock("sonner", () => ({
  toast: { success: vi.fn(), error: vi.fn() },
}));

import { WorkflowDesignerPage } from "./WorkflowDesignerPage";

describe("WorkflowDesignerPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.importXML.mockResolvedValue(undefined);
    mocks.saveXML.mockResolvedValue({ xml: "<definitions id=\"latest-canvas\" />" });
    mocks.createDefinition.mockResolvedValue(1);
  });

  it("内置空白流程是命名空间完整的有效 XML", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/workflow/new"]}>
        <Routes>
          <Route path="/admin/workflow/new" element={<WorkflowDesignerPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(mocks.importXML).toHaveBeenCalled());
    const initialXml = mocks.importXML.mock.calls[0][0] as string;
    const parsed = new DOMParser().parseFromString(initialXml, "application/xml");
    expect(parsed.querySelector("parsererror")).toBeNull();
  });

  afterEach(cleanup);

  it("创建流程时提交画布刚刚导出的 XML，而不是旧的 React 状态", async () => {
    render(
      <MemoryRouter initialEntries={["/admin/workflow/new"]}>
        <Routes>
          <Route path="/admin/workflow/new" element={<WorkflowDesignerPage />} />
          <Route path="/admin/workflow" element={<div>工作流列表</div>} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.change(screen.getByPlaceholderText("流程标识（如 leave-approval）"), {
      target: { value: "expense-approval" },
    });
    fireEvent.change(screen.getByPlaceholderText("流程名称"), {
      target: { value: "费用报销审批" },
    });
    fireEvent.click(screen.getByRole("button", { name: "创建草稿" }));

    await waitFor(() => expect(mocks.createDefinition).toHaveBeenCalledTimes(1));
    expect(mocks.createDefinition).toHaveBeenCalledWith({
      defKey: "expense-approval",
      name: "费用报销审批",
      bpmnXml: "<definitions id=\"latest-canvas\" />",
    });
  });

  it("存在新草稿时默认打开最新版本而不是旧的已发布版本", async () => {
    mocks.getDefinition.mockResolvedValue({
      definition: {
        id: 9,
        defKey: "leave-approval",
        name: "请假审批",
        currentPublishedVersionId: 91,
      },
      versions: [
        {
          id: 91,
          versionNumber: 1,
          status: "PUBLISHED",
          createdBy: "editor",
          createdTime: "2026-09-01T08:00:00",
          bpmnXml: "<definitions id=\"published-v1\" />",
        },
        {
          id: 92,
          versionNumber: 2,
          status: "DRAFT",
          createdBy: "editor",
          createdTime: "2026-09-02T08:00:00",
          bpmnXml: "<definitions id=\"draft-v2\" />",
        },
      ],
    });

    render(
      <MemoryRouter initialEntries={["/admin/workflow/leave-approval"]}>
        <Routes>
          <Route path="/admin/workflow/:defKey" element={<WorkflowDesignerPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() => expect(mocks.getDefinition).toHaveBeenCalledWith("leave-approval"));
    await waitFor(() => expect(mocks.importXML).toHaveBeenLastCalledWith("<definitions id=\"draft-v2\" />"));
  });

  it("提交审核成功后重新加载详情并显示中文状态", async () => {
    const draft = {
      definition: { id: 10, defKey: "expense", name: "报销审批", currentPublishedVersionId: null },
      versions: [{
        id: 101,
        versionNumber: 1,
        status: "DRAFT",
        createdBy: "editor",
        createdTime: "2026-09-01T08:00:00",
        bpmnXml: "<definitions id=\"expense-v1\" />",
      }],
    };
    mocks.getDefinition
      .mockResolvedValueOnce(draft)
      .mockResolvedValueOnce({
        ...draft,
        versions: [{ ...draft.versions[0], status: "IN_REVIEW" }],
      });
    mocks.submitReview.mockResolvedValue(undefined);

    render(
      <MemoryRouter initialEntries={["/admin/workflow/expense"]}>
        <Routes>
          <Route path="/admin/workflow/:defKey" element={<WorkflowDesignerPage />} />
        </Routes>
      </MemoryRouter>,
    );

    fireEvent.click(await screen.findByRole("button", { name: "提交当前版本审核" }));

    await waitFor(() => expect(mocks.submitReview).toHaveBeenCalledWith(101));
    await waitFor(() => expect(mocks.getDefinition).toHaveBeenCalledTimes(2));
    expect(await screen.findAllByText("待审核")).not.toHaveLength(0);
  });
});
