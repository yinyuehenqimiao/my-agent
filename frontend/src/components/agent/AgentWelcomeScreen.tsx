import * as React from "react";

import { AgentTurnItem, type AgentTurn } from "@/components/agent/AgentTurn";
import { useAgentChatStore } from "@/stores/agentChatStore";

/** 待机页取几条示例问句 */
const SAMPLE_QUESTIONS = [
  "公司的数据安全是怎么做的？",
  "帮我总结一下最近上传的文档要点",
  "知识库里有哪些关于权限管理的内容？"
];

/** 图注带：四格按时间顺序讲一次问答的四段 ▷○●▮ 只在这里解释一次 */
const STEPS = [
  ["▷", "提问", "你说的原话"],
  ["○", "思考", "它怎么想的"],
  ["●", "工具", "调了什么、返回什么"],
  ["▮", "答复", "最后怎么答"]
];

const DEMO_TURN: AgentTurn = {
  id: "demo",
  index: 1,
  user: {
    id: "demo-u",
    role: "user",
    content: "公司的数据安全是怎么做的？传输、权限、离职这几块分别有什么要求",
    createdAt: "2026-01-01T09:41:03"
  },
  assistant: {
    id: "demo-a",
    role: "assistant",
    content: "",
    status: "done",
    elapsedMs: 4200,
    createdAt: "2026-01-01T09:41:03",
    blocks: [
      {
        id: 1,
        kind: "reasoning",
        at: "09:41:03",
        text: "问的是制度类问题，答案属于知识库里沉淀的静态规范，先按传输与权限这两块检索原文，不必调业务系统的实时接口。"
      },
      {
        id: 2,
        kind: "tool",
        at: "09:41:04",
        name: "search_knowledge",
        displayName: "知识库检索",
        status: "done",
        durationMs: 1200,
        result:
          "## 数据安全管理规范（2025 修订）\n\n- 传输：全链路 TLS 1.3，内网服务之间互相调用同样不走明文，证书每年轮换一次\n- 权限：按岗位最小化授权，每季度复核一次，转岗当天回收原岗位的多余权限"
      },
      {
        id: 3,
        kind: "reasoning",
        at: "09:41:05",
        text: "传输和权限有了，离职回收落在另一份操作手册里，再检索一次把这块补齐再作答。"
      },
      {
        id: 4,
        kind: "tool",
        at: "09:41:06",
        name: "search_knowledge",
        displayName: "知识库检索",
        status: "done",
        durationMs: 900,
        result:
          "## 员工离职操作手册（2025 修订）\n\n- 账号回收：离职当日回收全部系统账号，含第三方 SaaS 与外部协作平台，回收记录留存半年备查"
      },
      {
        id: 5,
        kind: "answer",
        at: "09:41:07",
        text: "按《数据安全管理规范（2025 修订）》与《员工离职操作手册》，这件事拆成三层来做：\n\n- **传输加密**：全链路 TLS 1.3，内网服务之间互相调用同样不走明文，证书每年轮换一次\n- **权限最小化**：按岗位授权，每季度复核一次，转岗当天回收原岗位的多余权限\n- **离职回收**：当日回收全部系统账号，含第三方 SaaS 与外部协作平台"
      }
    ]
  }
};

function AgentSampleQuestions() {
  const setDraft = useAgentChatStore((store) => store.setDraft);

  return (
    <section className="agent-empty-try">
      <span className="agent-empty-try-label">试试这些</span>
      <div className="agent-empty-chips">
        {SAMPLE_QUESTIONS.map((question) => (
          <button
            key={question}
            type="button"
            className="agent-empty-chip"
            title="点击填入输入框"
            onClick={() => setDraft(question)}
          >
            <span>{question}</span>
          </button>
        ))}
      </div>
    </section>
  );
}

export function AgentWelcomeScreen() {
  return (
    <div className="agent-stream-empty">
      <div className="agent-empty-wrap">
        <div className="agent-empty-card">
          <figure className="agent-empty-figure">
            <div className="agent-empty-demo" aria-hidden="true">
              <AgentTurnItem turn={DEMO_TURN} note="示例" />
            </div>
            <figcaption className="agent-empty-cap">
              {STEPS.map(([glyph, name, desc]) => (
                <span key={glyph} className="agent-empty-step">
                  <span className="agent-empty-step-head">
                    <span
                      className="agent-empty-step-glyph"
                      data-answer={glyph === "▮" ? "true" : undefined}
                    >
                      {glyph}
                    </span>
                    {name}
                  </span>
                  <span className="agent-empty-step-desc">{desc}</span>
                </span>
              ))}
            </figcaption>
          </figure>
          <AgentSampleQuestions />
        </div>
      </div>
    </div>
  );
}
