import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { ActionCard } from "@/components/agent/EnterpriseCards";
import { enterprise, label, type Action, type Detail, type Employee, type LeaveRequest, type Task } from "@/services/enterpriseService";
import "@/styles/enterprise.css";

const session = "enterprise-workbench";
export function EnterprisePage() {
  const [search] = useSearchParams();
  const [me, setMe] = useState<Employee>();
  const [balance, setBalance] = useState<{ available: number; frozen: number; used: number }>();
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [actions, setActions] = useState<Action[]>([]);
  const [detail, setDetail] = useState<Detail>();
  const [error, setError] = useState(""); const [busy, setBusy] = useState(false);
  const [form, setForm] = useState({ leaveType: "ANNUAL", startDate: "", endDate: "", reason: "" });
  const [comment, setComment] = useState("");
  async function load() {
    try {
      const [m, b, r, t, a] = await Promise.all([enterprise.get<Employee>("/me"), enterprise.get<typeof balance>(`/leave-balances?year=${new Date().getFullYear()}`), enterprise.get<LeaveRequest[]>("/leave-requests"), enterprise.get<Task[]>("/approval-tasks"), enterprise.get<Action[]>(`/actions?conversationId=${session}`)]);
      setMe(m); setBalance(b); setRequests(r); setTasks(t); setActions(a); setError("");
      if (detail) await show(detail.request.id);
    } catch (e) { setError(e instanceof Error ? e.message : "无法加载企业工作台"); }
  }
  async function show(id: string) { try { setDetail(await enterprise.get<Detail>(`/leave-requests/${id}`)); setComment(""); } catch (e) { setError(e instanceof Error ? e.message : "无权查看"); } }
  useEffect(() => { void load(); if (search.get("request")) void show(search.get("request")!); }, []);
  async function prepare(path: string, values: object) {
    setBusy(true); setError("");
    try { const a = await enterprise.post<Action>(path, { ...values, conversationId: session, requestKey: crypto.randomUUID() }); setActions(prev => [a, ...prev.filter(x => x.id !== a.id)]); await load(); }
    catch (e) { setError(e instanceof Error ? e.message : "准备操作失败"); } finally { setBusy(false); }
  }
  return <main className="enterprise-page">
    <header className="flex flex-wrap items-center justify-between gap-4"><div><p className="text-sm text-slate-500">企业服务 / 请假与审批</p><h1 className="mt-2 text-3xl font-semibold">工作台</h1><p className="mt-2 text-slate-500">{me ? `${me.displayName} · ${me.department} · ${label(me.businessRole)}` : "企业能力需启用 enterprise 配置并配置员工档案"}</p></div><div className="flex gap-4"><Link to="/chat" className="enterprise-secondary">返回智能体</Link><button onClick={load} className="enterprise-secondary">刷新</button></div></header>
    {error && <p role="alert" className="my-4 rounded-lg bg-red-50 p-4 text-red-700">{error}</p>}
    <section className="my-8 grid grid-cols-3 gap-4">{[["可用年假", balance?.available], ["审批冻结", balance?.frozen], ["已用年假", balance?.used]].map(([name, value]) => <div key={name} className="enterprise-card"><p className="text-sm text-slate-500">{name}</p><p className="mt-3 text-3xl font-semibold">{value ?? "—"}<span className="ml-2 text-sm font-normal">天</span></p></div>)}</section>
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="enterprise-card"><h2 className="text-xl font-semibold">申请请假</h2><p className="mt-2 text-sm text-slate-500">演示规则：整天申请，主管与人事两级审批。</p><form className="mt-5 space-y-4" onSubmit={e => { e.preventDefault(); void prepare("/leave-requests/prepare", form); }}>
        <label className="block">假期类型<select className="enterprise-input" value={form.leaveType} onChange={e => setForm({ ...form, leaveType: e.target.value })}><option value="ANNUAL">年假</option><option value="PERSONAL">事假</option></select></label>
        <div className="grid grid-cols-2 gap-4"><label>开始日期<input required type="date" className="enterprise-input" value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} /></label><label>结束日期<input required type="date" className="enterprise-input" value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} /></label></div>
        <label className="block">申请原因<textarea required maxLength={1000} className="enterprise-input" value={form.reason} onChange={e => setForm({ ...form, reason: e.target.value })} /></label><button disabled={busy || !me} className="enterprise-primary">生成确认卡</button>
      </form></section>
      <section className="space-y-4"><h2 className="text-xl font-semibold">确认与办理结果</h2>{actions.length ? actions.slice(0, 12).map(a => <ActionCard key={a.id} actionId={a.id} onChanged={load} />) : <p className="enterprise-card text-slate-500">生成申请或审批决定后，在这里核对并确认。</p>}</section>
    </div>
    <div className="mt-8 grid gap-6 lg:grid-cols-2"><section className="enterprise-card"><h2 className="text-xl font-semibold">我的申请</h2>{requests.length === 0 && <p className="mt-4 text-slate-500">暂无申请</p>}{requests.map(r => <button key={r.id} onClick={() => show(r.id)} className="flex w-full justify-between gap-3 border-b py-4 text-left"><span>{label(r.leaveType)} · {r.startDate} / {r.days} 天</span><span>{label(r.status)}</span></button>)}</section><section className="enterprise-card"><h2 className="text-xl font-semibold">待办审批</h2>{tasks.length === 0 && <p className="mt-4 text-slate-500">暂无待办</p>}{tasks.map(t => <button key={t.id} onClick={() => show(t.requestId)} className="block w-full border-b py-4 text-left">{label(t.stage)}审批 · 查看申请 {t.requestId.slice(0, 8)}</button>)}</section></div>
    {detail && <section className="enterprise-card mt-8"><h2 className="text-xl font-semibold">申请详情 · {label(detail.request.status)}</h2><p className="mt-3">{detail.request.startDate} 至 {detail.request.endDate} / {detail.request.days} 天</p><p className="mt-2 whitespace-pre-wrap">{detail.request.reason}</p><ol className="my-5 space-y-3 border-l-2 pl-4">{detail.timeline.map(t => <li key={t.id}><strong>{label(t.event)}</strong><span className="ml-3 text-sm text-slate-500">{t.createdAt.replace("T", " ")}</span>{t.comment && <p>{t.comment}</p>}</li>)}</ol>
      {tasks.some(t => t.requestId === detail.request.id) && <div><label>审批意见<textarea className="enterprise-input" maxLength={1000} value={comment} onChange={e => setComment(e.target.value)} /></label><div className="mt-3 flex gap-3">{["APPROVE", "REJECT"].map(decision => <button key={decision} disabled={busy} className="enterprise-secondary" onClick={() => prepare(`/approval-tasks/${detail.request.id}/decision/prepare`, { decision, comment })}>准备{label(decision)}</button>)}</div></div>}
      {detail.request.userId === me?.userId && detail.request.status.startsWith("PENDING_") && <button disabled={busy} className="enterprise-secondary mt-4" onClick={() => prepare(`/leave-requests/${detail.request.id}/withdrawal/prepare`, {})}>准备撤回</button>}
    </section>}
  </main>;
}
