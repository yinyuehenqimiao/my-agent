import { useEffect, useState } from "react";
import { Activity, Search } from "lucide-react";
import { toast } from "sonner";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AUDIT_CATEGORY_LABEL, AUDIT_EVENT_LABEL } from "@/services/workflowService";
import { traceAuditService, type AuditHealth, type TraceDetail, type TraceFilters, type TraceRun } from "@/services/traceAuditService";
import { getErrorMessage } from "@/utils/error";

const initialFilters: TraceFilters = { traceId: "", userId: "", conversationId: "", taskId: "", status: "", current: 1, size: 20 };
const statusVariant = (status: string) => status === "SUCCESS" ? "default" : status === "RUNNING" ? "secondary" : "destructive";

export function TraceAuditPage() {
  const [filters, setFilters] = useState<TraceFilters>(initialFilters);
  const [runs, setRuns] = useState<TraceRun[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [health, setHealth] = useState<AuditHealth | null>(null);

  const load = async (next: TraceFilters) => {
    setLoading(true);
    try {
      const page = await traceAuditService.listRuns(next);
      setRuns(Array.isArray(page?.records) ? page.records : []);
      setTotal(page?.total ?? 0);
    } catch (error) { toast.error(getErrorMessage(error, "加载推理链路失败")); }
    finally { setLoading(false); }
  };
  useEffect(() => {
    void load(initialFilters);
    traceAuditService.getHealth().then(setHealth)
      .catch((error) => toast.error(getErrorMessage(error, "加载审计健康状态失败")));
  }, []);
  const update = (key: keyof TraceFilters, value: string) => setFilters((current) => ({ ...current, [key]: value }));
  const openDetail = async (traceId: string) => {
    try { setDetail(await traceAuditService.getDetail(traceId)); }
    catch (error) { toast.error(getErrorMessage(error, "加载链路详情失败")); }
  };

  return <div className="space-y-4 p-4">
    <div><h2 className="flex items-center gap-2 text-lg font-semibold"><Activity className="h-5 w-5" /> 推理链路审计</h2>
      <p className="mt-1 text-sm text-slate-500">按租户隔离查询路由、意图、Workflow、ReAct、工具调用和权限判定的完整链路。</p></div>
    {health && <Card className={health.status === "UP" ? "border-emerald-200" : "border-amber-300 bg-amber-50/60"}><CardContent className="flex flex-wrap items-center gap-3 p-4 text-sm">
      <Badge variant={health.status === "UP" ? "default" : "destructive"}>{health.status}</Badge>
      <span className="font-medium">{health.status === "UP" ? "审计持久化正常" : "审计持久化异常"}</span>
      <span className="text-slate-500">推理 {health.reasoningTraceWriteFailures}</span>
      <span className="text-slate-500">工具 {health.toolAuditWriteFailures}</span>
      <span className="text-slate-500">权限 {health.securityAuditWriteFailures}</span>
      {health.status === "DEGRADED" && <span className="ml-auto text-amber-700">请检查数据库与告警日志；写操作可能已按安全策略阻断。</span>}
    </CardContent></Card>}
    <Card><CardContent className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-6">
      <Input aria-label="链路标识" placeholder="traceId" value={filters.traceId} onChange={(event) => update("traceId", event.target.value)} />
      <Input aria-label="用户标识" placeholder="用户 ID" value={filters.userId} onChange={(event) => update("userId", event.target.value)} />
      <Input aria-label="会话标识" placeholder="会话 ID" value={filters.conversationId} onChange={(event) => update("conversationId", event.target.value)} />
      <Input aria-label="任务标识" placeholder="taskId" value={filters.taskId} onChange={(event) => update("taskId", event.target.value)} />
      <select aria-label="运行状态" className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={filters.status} onChange={(event) => update("status", event.target.value)}>
        <option value="">全部状态</option><option value="RUNNING">执行中</option><option value="SUCCESS">成功</option><option value="ERROR">失败</option><option value="CANCELLED">已取消</option>
      </select>
      <Button onClick={() => void load({ ...filters, current: 1, size: 20 })}><Search className="mr-2 h-4 w-4" />查询</Button>
    </CardContent></Card>
    <Card><CardContent className="p-0"><div className="border-b px-4 py-3 text-sm text-slate-500">共 {total} 条请求链路</div>
      <Table><TableHeader><TableRow><TableHead>问题</TableHead><TableHead>用户</TableHead><TableHead>会话 / 任务</TableHead><TableHead>状态</TableHead><TableHead>耗时</TableHead><TableHead>traceId</TableHead><TableHead>操作</TableHead></TableRow></TableHeader><TableBody>
        {loading && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">正在加载推理链路…</TableCell></TableRow>}
        {!loading && runs.length === 0 && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">没有符合条件的推理链路</TableCell></TableRow>}
        {runs.map((run) => <TableRow key={run.traceId}>
          <TableCell className="max-w-xs"><div className="truncate">{run.question || "-"}</div><div className="text-xs text-slate-400">{run.startTime || "-"}</div></TableCell>
          <TableCell>{run.userId || "-"}</TableCell><TableCell className="font-mono text-xs"><div>{run.conversationId || "-"}</div><div className="text-slate-400">{run.taskId || "-"}</div></TableCell>
          <TableCell><Badge variant={statusVariant(run.status)}>{run.status}</Badge></TableCell><TableCell>{run.durationMs == null ? "-" : `${run.durationMs} ms`}</TableCell>
          <TableCell className="max-w-44 truncate font-mono text-xs text-slate-500">{run.traceId}</TableCell><TableCell><Button size="sm" variant="outline" onClick={() => void openDetail(run.traceId)}>查看链路</Button></TableCell>
        </TableRow>)}
      </TableBody></Table>
    </CardContent></Card>
    <Dialog open={detail !== null} onOpenChange={(open) => { if (!open) setDetail(null); }}><DialogContent className="max-h-[85vh] max-w-5xl overflow-y-auto">
      <DialogHeader><DialogTitle>完整审计链路</DialogTitle><DialogDescription className="font-mono">{detail?.run.traceId}</DialogDescription></DialogHeader>
      <section className="space-y-2"><h3 className="font-medium">推理节点</h3>{(detail?.nodes ?? []).map((node) => <div key={node.nodeId} className="rounded-lg border p-3 text-sm" style={{ marginLeft: `${Math.min(node.depth ?? 0, 5) * 16}px` }}>
        <div className="flex justify-between gap-3"><span>{node.nodeName || node.nodeType || node.nodeId}</span><Badge variant={statusVariant(node.status)}>{node.status}</Badge></div><div className="mt-1 text-xs text-slate-500">{node.nodeType || "NODE"} · {node.durationMs ?? "-"} ms</div>
      </div>)}</section>
      <section className="space-y-2"><h3 className="font-medium">工具、权限与业务事件</h3>{(detail?.events ?? []).map((event) => <div key={`${event.category}-${event.id}`} className="rounded-lg border p-3 text-sm">
        <div className="flex flex-wrap items-center gap-2"><Badge variant="outline">{AUDIT_CATEGORY_LABEL[event.category]}</Badge><span>{AUDIT_EVENT_LABEL[event.eventCode] ?? event.eventCode}</span><span className="ml-auto text-xs text-slate-400">{event.createdTime || "-"}</span></div>
        <div className="mt-1 flex gap-1 text-xs text-slate-500"><span>{event.actorId || "-"}</span><span>→</span><span>{event.targetType || "-"}</span>{event.targetId && <><span>/</span><span>{event.targetId}</span></>}</div>{event.detail && <div className="mt-1 break-all text-xs text-slate-400">{event.detail}</div>}
      </div>)}</section>
    </DialogContent></Dialog>
  </div>;
}
