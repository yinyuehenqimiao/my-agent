import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Link2, PlugZap, RefreshCw, ShieldCheck, ShieldOff } from "lucide-react";

import {
  mcpConnectorService,
  mcpIntentBindingService,
  type McpCatalogTool,
  type McpConnector,
  type McpConnectorInput,
  type McpIntentBinding
} from "@/services/mcpConnectorService";
import { getIntentTree, type IntentNodeTree } from "@/services/intentTreeService";
import "@/styles/enterprise.css";

const emptyForm: McpConnectorInput = {
  connectorId: "erp",
  displayName: "企业 ERP",
  endpoint: "http://127.0.0.1:18081/mcp",
  secretRef: "vault:erp"
};

type IntentOption = { intentCode: string; name: string; kind?: number | null };

function statusLabel(status: string) {
  if (status === "DISCOVERED") return "已发现工具";
  if (status === "DRAFT") return "草稿";
  if (status === "PUBLISHED") return "已发布";
  return status || "未知";
}

function reviewLabel(status: string) {
  if (status === "APPROVED") return "已批准";
  if (status === "DISABLED") return "已停用";
  if (status === "PENDING_REVIEW") return "待审核";
  return status || "未知";
}

function tokenEnvHint(secretRef: string) {
  const normalized = secretRef
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "_");
  return `MCP_DISCOVERY_TOKEN_${normalized || "…"}`;
}

function flattenIntents(nodes: IntentNodeTree[], out: IntentOption[] = []): IntentOption[] {
  for (const node of nodes) {
    out.push({ intentCode: node.intentCode, name: node.name, kind: node.kind });
    if (node.children?.length) flattenIntents(node.children, out);
  }
  return out;
}

export function EnterpriseAdminPage() {
  const [connectors, setConnectors] = useState<McpConnector[]>([]);
  const [selectedId, setSelectedId] = useState("");
  const [tools, setTools] = useState<McpCatalogTool[]>([]);
  const [bindings, setBindings] = useState<McpIntentBinding[]>([]);
  const [intents, setIntents] = useState<IntentOption[]>([]);
  const [form, setForm] = useState<McpConnectorInput>(emptyForm);
  const [bindIntentCode, setBindIntentCode] = useState("");
  const [bindToolId, setBindToolId] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const selected = useMemo(
    () => connectors.find((item) => item.connectorId === selectedId) || null,
    [connectors, selectedId]
  );

  const approvedTools = useMemo(
    () => tools.filter((tool) => tool.reviewStatus === "APPROVED" && tool.enabled),
    [tools]
  );

  const mcpIntents = useMemo(() => {
    const mcp = intents.filter((item) => item.kind === 2);
    return mcp.length > 0 ? mcp : intents;
  }, [intents]);

  useEffect(() => {
    let active = true;
    void (async () => {
      try {
        setError("");
        const [list, tree, bindingRows] = await Promise.all([
          mcpConnectorService.list(),
          getIntentTree().catch(() => [] as IntentNodeTree[]),
          mcpIntentBindingService.list().catch(() => [] as McpIntentBinding[])
        ]);
        if (!active) return;
        setConnectors(list);
        setSelectedId(list[0]?.connectorId || "");
        setIntents(flattenIntents(Array.isArray(tree) ? tree : []));
        setBindings(Array.isArray(bindingRows) ? bindingRows : []);
      } catch (err) {
        if (active) setError(err instanceof Error ? err.message : "加载 ERP 连接失败");
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!selectedId) {
      setTools([]);
      return;
    }
    let active = true;
    void mcpConnectorService
      .tools(selectedId)
      .then((rows) => {
        if (active) setTools(rows);
      })
      .catch((err) => {
        if (active) setError(err instanceof Error ? err.message : "加载工具目录失败");
      });
    return () => {
      active = false;
    };
  }, [selectedId]);

  useEffect(() => {
    if (!selected) return;
    setForm((prev) => ({
      connectorId: selected.connectorId,
      displayName: selected.displayName,
      endpoint: selected.endpoint,
      secretRef: prev.secretRef || emptyForm.secretRef
    }));
  }, [selected]);

  useEffect(() => {
    if (!approvedTools.some((tool) => tool.toolId === bindToolId)) {
      setBindToolId(approvedTools[0]?.toolId || "");
    }
  }, [approvedTools, bindToolId]);

  async function refreshConnectors(preferId?: string) {
    const list = await mcpConnectorService.list();
    setConnectors(list);
    const nextId =
      (preferId && list.some((item) => item.connectorId === preferId) && preferId) ||
      (selectedId && list.some((item) => item.connectorId === selectedId) && selectedId) ||
      list[0]?.connectorId ||
      "";
    setSelectedId(nextId);
  }

  async function refreshTools(connectorId: string) {
    if (!connectorId) {
      setTools([]);
      return;
    }
    setTools(await mcpConnectorService.tools(connectorId));
  }

  async function refreshBindings() {
    setBindings(await mcpIntentBindingService.list());
  }

  async function run(job: () => Promise<void>, success: string) {
    setBusy(true);
    setError("");
    setMessage("");
    try {
      await job();
      setMessage(success);
    } catch (err) {
      setError(err instanceof Error ? err.message : "操作失败");
    } finally {
      setBusy(false);
    }
  }

  function startCreate() {
    setSelectedId("");
    setTools([]);
    setForm(emptyForm);
    setMessage("");
    setError("");
  }

  return (
    <div className="p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">ERP 连接</h1>
        <p className="mt-1 text-slate-500">
          完整链路：登记 MCP 连接 → 发现并审核工具 → 绑定到意图并发布。员工与审批关系仍在 ERP 内管理。
        </p>
      </div>

      <section className="enterprise-card">
        <ol className="grid gap-3 md:grid-cols-3 text-sm">
          <li className="rounded-xl border border-cyan-200 bg-cyan-50/70 p-3">
            <div className="font-semibold text-cyan-900">① 连接 ERP</div>
            <p className="mt-1 text-cyan-800/80">登记 endpoint 与 secretRef，不保存真实令牌。</p>
          </li>
          <li className="rounded-xl border border-slate-200 bg-slate-50 p-3">
            <div className="font-semibold text-slate-800">② 发现并审核工具</div>
            <p className="mt-1 text-slate-600">从 ERP 拉目录；仅「批准启用」后可绑定。</p>
          </li>
          <li className="rounded-xl border border-slate-200 bg-slate-50 p-3">
            <div className="font-semibold text-slate-800">③ 绑定意图并发布</div>
            <p className="mt-1 text-slate-600">把工具挂到意图；发布后才进入运行装配。</p>
          </li>
        </ol>
        <p className="mt-3 text-xs text-slate-500">
          工作流中心里的「运行时工具目录」只是只读清单，不负责登记 ERP；路由模拟负责把意图接到 Workflow/ReAct，与本页 MCP 绑定互补。
          {" "}
          <Link className="text-cyan-700 underline" to="/admin/workflow/tools">
            查看运行时工具目录
          </Link>
        </p>
      </section>

      {error ? <p role="alert" className="text-red-600">{error}</p> : null}
      {message ? <p role="status" className="text-emerald-600">{message}</p> : null}

      <section className="enterprise-card">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold">① 已登记的 ERP 连接器</h2>
            <p className="mt-1 text-sm text-slate-500">选择一条连接后，继续发现工具、审核与绑定。</p>
          </div>
          <button type="button" className="enterprise-secondary" onClick={startCreate}>
            新建连接
          </button>
        </div>

        {connectors.length === 0 ? (
          <p className="mt-4 text-sm text-slate-500">还没有 ERP 连接，请先在下方填写并保存。</p>
        ) : (
          <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {connectors.map((item) => {
              const active = item.connectorId === selectedId;
              return (
                <button
                  key={item.connectorId}
                  type="button"
                  onClick={() => setSelectedId(item.connectorId)}
                  className={`rounded-xl border p-4 text-left transition ${
                    active
                      ? "border-cyan-500 bg-cyan-50 shadow-sm"
                      : "border-slate-200 bg-white hover:border-slate-300"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <PlugZap className="h-4 w-4 text-cyan-700" />
                    <span className="font-medium">{item.displayName}</span>
                  </div>
                  <p className="mt-2 truncate text-xs text-slate-500">{item.connectorId}</p>
                  <p className="mt-1 truncate text-xs text-slate-500">{item.endpoint}</p>
                  <span className="mt-3 inline-flex rounded-full bg-slate-100 px-2 py-0.5 text-xs text-slate-600">
                    {statusLabel(item.status)}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </section>

      <section className="enterprise-card">
        <div className="flex items-center gap-2">
          <Link2 className="h-5 w-5 text-slate-500" />
          <h2 className="text-lg font-semibold">{selected ? "编辑 ERP 连接" : "登记 ERP 连接"}</h2>
        </div>
        <p className="mt-2 text-sm text-slate-500">
          令牌不入库，仅保存 `secretRef`。发现工具时会读取环境变量{" "}
          <code className="rounded bg-slate-100 px-1">{tokenEnvHint(form.secretRef)}</code>。
        </p>

        <form
          className="mt-4 grid gap-4 md:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault();
            void run(async () => {
              await mcpConnectorService.save(form);
              await refreshConnectors(form.connectorId.trim());
            }, "连接已保存");
          }}
        >
          <label className="text-sm">
            连接器 ID
            <input
              required
              className="enterprise-input"
              value={form.connectorId}
              disabled={Boolean(selected)}
              onChange={(event) => setForm({ ...form, connectorId: event.target.value.trim() })}
              placeholder="例如 erp"
            />
          </label>
          <label className="text-sm">
            显示名称
            <input
              required
              className="enterprise-input"
              value={form.displayName}
              onChange={(event) => setForm({ ...form, displayName: event.target.value })}
              placeholder="企业 ERP"
            />
          </label>
          <label className="text-sm md:col-span-2">
            MCP Endpoint
            <input
              required
              className="enterprise-input"
              value={form.endpoint}
              onChange={(event) => setForm({ ...form, endpoint: event.target.value.trim() })}
              placeholder="http://127.0.0.1:18081/mcp"
            />
          </label>
          <label className="text-sm md:col-span-2">
            Secret Ref
            <input
              required
              className="enterprise-input"
              value={form.secretRef}
              onChange={(event) => setForm({ ...form, secretRef: event.target.value.trim() })}
              placeholder="vault:erp"
            />
          </label>
          <div className="md:col-span-2 flex flex-wrap gap-3">
            <button type="submit" className="enterprise-primary" disabled={busy}>
              保存连接
            </button>
            {selected ? (
              <button
                type="button"
                className="enterprise-secondary inline-flex items-center gap-2"
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    const next = await mcpConnectorService.discover(selected.connectorId);
                    setTools(next);
                    await refreshConnectors(selected.connectorId);
                  }, "已从 ERP 发现工具目录")
                }
              >
                <RefreshCw className="h-4 w-4" />
                发现工具
              </button>
            ) : null}
          </div>
        </form>
      </section>

      <section className="enterprise-card">
        <h2 className="text-lg font-semibold">② 工具目录与审核</h2>
        <p className="mt-2 text-sm text-slate-500">
          发现后的工具默认待审核。只有「批准启用」后，才能在下一步绑定意图。
        </p>

        {!selectedId ? (
          <p className="mt-4 text-sm text-slate-500">请先选择或保存一条 ERP 连接。</p>
        ) : tools.length === 0 ? (
          <p className="mt-4 text-sm text-slate-500">暂无工具，点击「发现工具」从 ERP MCP 拉取目录。</p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-slate-500">
                  <th className="p-2">工具</th>
                  <th className="p-2">说明</th>
                  <th className="p-2">状态</th>
                  <th className="p-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {tools.map((tool) => (
                  <tr key={tool.toolId} className="border-b align-top">
                    <td className="p-2">
                      <div className="font-medium">{tool.title || tool.toolId}</div>
                      <div className="text-xs text-slate-500">{tool.toolId}</div>
                    </td>
                    <td className="p-2 text-slate-600">{tool.description || "—"}</td>
                    <td className="p-2">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs ${
                          tool.reviewStatus === "APPROVED" && tool.enabled
                            ? "bg-emerald-100 text-emerald-700"
                            : tool.reviewStatus === "DISABLED"
                              ? "bg-slate-100 text-slate-600"
                              : "bg-amber-100 text-amber-700"
                        }`}
                      >
                        {reviewLabel(tool.reviewStatus)}
                        {tool.reviewStatus === "APPROVED" ? (tool.enabled ? " · 启用" : " · 未启用") : ""}
                      </span>
                    </td>
                    <td className="p-2">
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          className="enterprise-primary inline-flex items-center gap-1"
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await mcpConnectorService.review(selectedId, tool.toolId, "APPROVED", true);
                              await refreshTools(selectedId);
                            }, `已批准 ${tool.toolId}`)
                          }
                        >
                          <ShieldCheck className="h-3.5 w-3.5" />
                          批准启用
                        </button>
                        <button
                          type="button"
                          className="enterprise-secondary inline-flex items-center gap-1"
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await mcpConnectorService.review(selectedId, tool.toolId, "DISABLED", false);
                              await refreshTools(selectedId);
                            }, `已停用 ${tool.toolId}`)
                          }
                        >
                          <ShieldOff className="h-3.5 w-3.5" />
                          停用
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="enterprise-card">
        <h2 className="text-lg font-semibold">③ 意图绑定与发布</h2>
        <p className="mt-2 text-sm text-slate-500">
          把已批准的 ERP 工具挂到意图节点。先保存为草稿，再显式发布后才会被运行时装载。
          若还没有 MCP 类意图，请先到{" "}
          <Link className="text-cyan-700 underline" to="/admin/intent-tree">
            意图管理
          </Link>{" "}
          创建。
        </p>

        <form
          className="mt-4 grid gap-4 md:grid-cols-3"
          onSubmit={(event) => {
            event.preventDefault();
            if (!selectedId) {
              setError("请先选择 ERP 连接");
              return;
            }
            void run(async () => {
              await mcpIntentBindingService.bind({
                intentCode: bindIntentCode,
                connectorId: selectedId,
                toolId: bindToolId
              });
              await refreshBindings();
            }, `已绑定草稿：${bindIntentCode} → ${bindToolId}`);
          }}
        >
          <label className="text-sm">
            意图
            <select
              required
              className="enterprise-input"
              value={bindIntentCode}
              onChange={(event) => setBindIntentCode(event.target.value)}
            >
              <option value="">选择意图</option>
              {mcpIntents.map((item) => (
                <option key={item.intentCode} value={item.intentCode}>
                  {item.name} ({item.intentCode})
                </option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            已批准工具
            <select
              required
              className="enterprise-input"
              value={bindToolId}
              onChange={(event) => setBindToolId(event.target.value)}
              disabled={!selectedId || approvedTools.length === 0}
            >
              <option value="">
                {!selectedId ? "先选择连接" : approvedTools.length === 0 ? "暂无已批准工具" : "选择工具"}
              </option>
              {approvedTools.map((tool) => (
                <option key={tool.toolId} value={tool.toolId}>
                  {tool.title || tool.toolId}
                </option>
              ))}
            </select>
          </label>
          <div className="flex items-end">
            <button
              type="submit"
              className="enterprise-primary"
              disabled={busy || !selectedId || !bindIntentCode || !bindToolId}
            >
              保存绑定（草稿）
            </button>
          </div>
        </form>

        {bindings.length === 0 ? (
          <p className="mt-4 text-sm text-slate-500">还没有意图绑定。</p>
        ) : (
          <div className="mt-4 overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b text-left text-slate-500">
                  <th className="p-2">意图</th>
                  <th className="p-2">连接器</th>
                  <th className="p-2">工具</th>
                  <th className="p-2">状态</th>
                  <th className="p-2">操作</th>
                </tr>
              </thead>
              <tbody>
                {bindings.map((row) => (
                  <tr key={`${row.intentCode}:${row.connectorId}:${row.toolId}`} className="border-b">
                    <td className="p-2 font-mono text-xs">{row.intentCode}</td>
                    <td className="p-2">{row.connectorId}</td>
                    <td className="p-2 font-mono text-xs">{row.toolId}</td>
                    <td className="p-2">
                      <span
                        className={`inline-flex rounded-full px-2 py-0.5 text-xs ${
                          row.status === "PUBLISHED"
                            ? "bg-emerald-100 text-emerald-700"
                            : "bg-amber-100 text-amber-700"
                        }`}
                      >
                        {statusLabel(row.status)}
                      </span>
                    </td>
                    <td className="p-2">
                      {row.status !== "PUBLISHED" ? (
                        <button
                          type="button"
                          className="enterprise-secondary"
                          disabled={busy}
                          onClick={() =>
                            void run(async () => {
                              await mcpIntentBindingService.publish(row.intentCode);
                              await refreshBindings();
                            }, `已发布绑定：${row.intentCode}`)
                          }
                        >
                          发布
                        </button>
                      ) : (
                        <span className="text-xs text-slate-400">已生效</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
