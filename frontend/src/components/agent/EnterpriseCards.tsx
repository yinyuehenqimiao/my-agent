import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { enterprise, label, type Action, type ErpPendingAction, type Evidence, type LeaveRequest } from "@/services/enterpriseService";

export function ActionCard({ actionId, external = false, onChanged }: { actionId: string; external?: boolean; onChanged?: () => void }) {
  const [action, setAction] = useState<Action | ErpPendingAction>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    let alive = true;
    const prefix = external ? "/erp-actions" : "/actions";
    enterprise.get<Action | ErpPendingAction>(`${prefix}/${encodeURIComponent(actionId)}`).then(a => { if (alive) setAction(a); }).catch(e => { if (alive) setError(e.message); });
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => { alive = false; window.clearInterval(timer); };
  }, [actionId, external]);
  async function execute(operation: "confirm" | "cancel") {
    setBusy(true); setError("");
    try { setAction(await enterprise.post<Action | ErpPendingAction>(`${external ? "/erp-actions" : "/actions"}/${actionId}/${operation}`)); onChanged?.(); }
    catch (e) { setError(e instanceof Error ? e.message : "操作失败，请刷新状态"); }
    finally { setBusy(false); }
  }
  if (!action) return <div className="enterprise-card">{error || "加载确认信息…"}</div>;
  let summary: Partial<LeaveRequest> = {}; let comment = "";
  try { const p = JSON.parse(action.payload); summary = p.summary || p; comment = p.comment || ""; } catch { /* server remains authority */ }
  const expired = now >= new Date(action.expiresAt + "+08:00").getTime();
  return <section className="enterprise-card" aria-label="业务确认卡">
    <div className="flex items-center justify-between gap-3"><strong>{label(external ? (action as ErpPendingAction).actionType : (action as Action).kind)}</strong><span>{label(action.status)}</span></div>
    <p className="mt-3">{label(summary.leaveType || "")} · {summary.startDate} 至 {summary.endDate}{summary.days ? ` · ${summary.days} 个工作日` : ""}</p>
    <p className="mt-1 whitespace-pre-wrap">{summary.reason}</p>
    {comment && <p className="mt-2">审批意见：{comment}</p>}
    <p className="mt-2 text-xs opacity-60">主管：{summary.managerId} → 人事：{summary.hrId}</p>
    {action.status === "PENDING" && <>
      <p className="mt-3 text-sm">{expired ? "确认已过期，请重新生成。" : "尚未办理，请核对后确认。修改内容需取消后重新生成。"}</p>
      <div className="mt-3 flex gap-3"><button className="enterprise-primary" disabled={busy || expired} onClick={() => execute("confirm")}>{busy ? "处理中…" : "确认执行"}</button><button className="enterprise-secondary" disabled={busy} onClick={() => execute("cancel")}>取消</button></div>
    </>}
    {action.status === "DONE" && <p className="mt-3 text-emerald-600">操作已完成，ERP 已返回受理结果。</p>}
    {error && <p role="alert" className="mt-2 text-red-600">{error}</p>}
    {!external && <Link className="mt-3 inline-block underline" to={`/enterprise?request=${(action as Action).requestId}`}>查看申请与进度</Link>}
  </section>;
}

function SourceCard({ source }: { source: Evidence }) {
  const [content, setContent] = useState(""); const [error, setError] = useState("");
  async function open() {
    if (!source.chunkId) return;
    try { const s = await enterprise.get<{ content: string }>(`/knowledge/sources/${source.docId}/${source.chunkId}`); setContent(s.content); setError(""); }
    catch (e) { setError(e instanceof Error ? e.message : "来源不可用"); }
  }
  return <article className="enterprise-card">
    <button className="font-medium underline" onClick={open}>{source.docName || "制度文档"} · 片段 {source.chunkIndex}</button>
    <p className="text-xs opacity-60">版本 {source.version} {source.effectiveDate && `· 生效 ${source.effectiveDate}`}</p>
    <p className="mt-2 whitespace-pre-wrap text-sm">{source.excerpt}</p>
    {content && <div className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap border-t pt-3 text-sm">{content}<button className="mt-2 block underline" onClick={() => setContent("")}>收起</button></div>}
    {error && <p role="alert" className="text-red-600">{error}</p>}
  </article>;
}

export function EnterpriseToolResult({ raw }: { raw: string }) {
  let result: { status?: string; data?: Action | ErpPendingAction; sources?: Evidence[] };
  try { result = JSON.parse(raw); } catch { return null; }
  if (result.status === "AWAITING_CONFIRMATION" && result.data?.id) return <ActionCard actionId={result.data.id} external={"actionType" in result.data} />;
  if (Array.isArray(result.sources) && result.sources.some(source => source.chunkId)) {
    return <div className="mt-3 space-y-3">{result.sources.filter(source => source.chunkId).map(s => <SourceCard key={`${s.docId}:${s.chunkId}`} source={s} />)}</div>;
  }
  return null;
}
