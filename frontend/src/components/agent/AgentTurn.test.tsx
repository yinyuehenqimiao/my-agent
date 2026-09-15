// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { AgentTurnItem } from "./AgentTurn";

describe("AgentTurn knowledge source presentation", () => {
  afterEach(cleanup);

  it("shows a document summary instead of a generic object or chunk excerpts", () => {
    const result = JSON.stringify({
      status: "SUCCESS",
      summary: "已参考 2 份文档",
      sources: [
        { docId: "d1", docName: "人事制度.md", version: "2026.1", matchedFragments: 3 },
        { docId: "d2", docName: "公司规章制度.md", version: "2026.1", matchedFragments: 1 }
      ]
    });

    render(
      <AgentTurnItem
        turn={{
          id: "turn-1",
          index: 1,
          assistant: {
            id: "m-1",
            role: "assistant",
            content: "请假制度回答",
            status: "done",
            blocks: [
              {
                id: 1,
                kind: "tool",
                at: "2026-09-15T18:00:00",
                name: "search_knowledge",
                displayName: "知识来源",
                status: "done",
                result
              }
            ]
          }
        }}
      />
    );

    expect(screen.getByText("已参考 2 份文档 · 人事制度.md、公司规章制度.md")).toBeTruthy();
    expect(screen.queryByText(/对象 ·/)).toBeNull();
  });
});
