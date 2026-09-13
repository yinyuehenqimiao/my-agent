import { afterEach, describe, expect, it, vi } from "vitest";

import { createAgentStreamResponse } from "./useAgentStream";

describe("useAgentStream route events", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("dispatches route, workflow progress and policy denial events", async () => {
    const body = [
      "event: route_decision",
      'data: {"decision":"WORKFLOW","target":"leave-approval","confidence":0.95}',
      "",
      "event: workflow_progress",
      'data: {"workflowDefKey":"leave-approval","node":"参数收集与确认","status":"RUNNING"}',
      "",
      "event: policy_denied",
      'data: {"reason":"未授权"}',
      "",
      "event: done",
      "data: [DONE]",
      ""
    ].join("\n");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(body, {
      headers: { "content-type": "text/event-stream;charset=UTF-8" }
    })));
    const onRouteDecision = vi.fn();
    const onWorkflowProgress = vi.fn();
    const onPolicyDenied = vi.fn();
    const onDone = vi.fn();

    await createAgentStreamResponse({ url: "/agent/v1/chat" }, {
      onRouteDecision,
      onWorkflowProgress,
      onPolicyDenied,
      onDone
    }).start();

    expect(onRouteDecision).toHaveBeenCalledWith(expect.objectContaining({ decision: "WORKFLOW" }));
    expect(onWorkflowProgress).toHaveBeenCalledWith(expect.objectContaining({ node: "参数收集与确认" }));
    expect(onPolicyDenied).toHaveBeenCalledWith({ reason: "未授权" });
    expect(onDone).toHaveBeenCalledOnce();
  });
});
