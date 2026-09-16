import { useEffect, useMemo, useState } from "react";
import {
  Activity,
  CheckCircle2,
  Copy,
  Cpu,
  GitBranch,
  Search,
  Shield,
  Sparkles,
  Timer,
  XCircle,
  Zap,
} from "lucide-react";
import { toast } from "sonner";
import { AdminPagination } from "@/components/admin/AdminPagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AUDIT_CATEGORY_LABEL, AUDIT_EVENT_LABEL } from "@/services/workflowService";
import {
  traceAuditService,
  type AuditHealth,
  type TraceDetail,
  type TraceFilters,
  type TraceNode,
  type TraceRun,
  type TraceStats,
} from "@/services/traceAuditService";
import { getErrorMessage } from "@/utils/error";

const initialFilters: TraceFilters = {
  traceId: "", userId: "", conversationId: "", taskId: "", status: "", current: 1, size: 20,
};

const statusVariant = (status: string) =>
  status === "SUCCESS" ? "default" : status === "RUNNING" ? "secondary" : "destructive";

const statusTone = (status?: string | null) => {
  if (status === "SUCCESS") return "success";
  if (status === "RUNNING") return "running";
  if (status === "CANCELLED") return "cancelled";
  if (status === "ERROR" || status === "FAILED" || status === "TIMEOUT") return "failed";
  return "neutral";
};

function userLabel(run: Pick<TraceRun, "username" | "userId">) {
  return run.username || run.userId || "-";
}

function nodeIcon(type?: string | null) {
  const key = (type || "").toUpperCase();
  if (key.includes("LLM") || key.includes("PROVIDER")) return Cpu;
  if (key.includes("INTENT")) return Sparkles;
  if (key.includes("ROUT")) return GitBranch;
  if (key.includes("TOOL") || key.includes("AUTH") || key.includes("SECURITY")) return Shield;
  return Zap;
}

const NODE_TYPE_LABEL: Record<string, string> = {
  INTENT: "意图识别",
  ROUTING: "路由规划",
  ROUTING_DECISION: "路由决策",
  LLM_ROUTING: "LLM 路由层",
  LLM_PROVIDER: "模型供应商调用",
  LLM_TTFT: "首包耗时",
  WORKFLOW: "固定流程",
  REACT: "开放式智能体",
  REWRITE: "问题改写",
  RETRIEVE: "知识检索",
  STREAM: "流式执行",
};

function nodeTypeLabel(type?: string | null) {
  if (!type) return "节点";
  return NODE_TYPE_LABEL[type.toUpperCase()] || type;
}

function nodeTitle(node: TraceNode) {
  const raw = node.nodeName || node.nodeId || "未命名节点";
  if (raw.startsWith("workflow-agent:")) {
    return `固定流程：${raw.slice("workflow-agent:".length)}`;
  }
  if (raw.startsWith("react-agent:")) {
    return `开放智能体：${raw.slice("react-agent:".length)}`;
  }
  const labels: Record<string, string> = {
    "intent-classify": "意图识别（打分）",
    "intent-resolve": "意图解析",
    "chat-route-plan": "聊天路由规划",
    "routing-decision": "路由策略判定",
    "llm-chat-routing": "LLM 路由（选择可用模型）",
    "llm-stream-routing": "LLM 流式路由",
    "siliconflow-chat": "SiliconFlow 模型调用",
    "bailian-chat": "百炼模型调用",
    "ollama-chat": "Ollama 模型调用",
    "aihubmix-chat": "AIHubMix 模型调用",
    "query-rewrite": "问题改写",
    "query-rewrite-and-split": "问题改写与拆分",
    "retrieval-engine": "知识库检索",
    "llm-first-packet": "等待模型首包",
  };
  return labels[raw] || raw;
}

function nodeSubtitle(node: TraceNode) {
  const parts = [nodeTypeLabel(node.nodeType)];
  if (node.parentNodeId) parts.push(`父节点 …${node.parentNodeId.slice(-6)}`);
  if (node.depth != null) parts.push(`深度 ${node.depth}`);
  if (node.nodeName && nodeTitle(node) !== node.nodeName) {
    parts.push(node.nodeName);
  }
  return parts.join(" · ");
}


function toEpoch(value?: string | null) {
  if (!value) return null;
  const time = new Date(value).getTime();
  return Number.isFinite(time) ? time : null;
}

/** 按整次请求墙钟摆放节点条；父子嵌套会导致节点耗时之和大于总耗时。 */
function buildWaterfall(nodes: TraceNode[], run?: TraceRun | null) {
  if (!nodes.length) return [];
  const runStart = toEpoch(run?.startTime) ?? toEpoch(nodes[0]?.startTime) ?? 0;
  const runDuration = Math.max(
    1,
    run?.durationMs
      ?? ((toEpoch(run?.endTime) ?? runStart) - runStart)
      ?? 1,
  );
  const maxDuration = Math.max(1, ...nodes.map((node) => Math.max(node.durationMs ?? 0, 1)));

  return nodes.map((node, index) => {
    const start = toEpoch(node.startTime);
    const duration = Math.max(node.durationMs ?? 0, 0);
    const offset = start == null ? 0 : Math.max(0, start - runStart);
    const left = Math.min(97, (offset / runDuration) * 100);
    const width = Math.max(Math.min((Math.max(duration, 1) / runDuration) * 100, 100 - left), 2.5);
    const relative = Math.round((Math.max(duration, 1) / maxDuration) * 100);
    return { node, left, width, duration, relative, index, offset };
  });
}

function copyText(value: string) {
  void navigator.clipboard?.writeText(value).then(
    () => toast.success("已复制"),
    () => toast.error("复制失败"),
  );
}

export function TraceAuditPage() {
  const [filters, setFilters] = useState<TraceFilters>(initialFilters);
  const [runs, setRuns] = useState<TraceRun[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [health, setHealth] = useState<AuditHealth | null>(null);
  const [stats, setStats] = useState<TraceStats | null>(null);

  const load = async (next: TraceFilters) => {
    setLoading(true);
    try {
      const [page, nextStats] = await Promise.all([
        traceAuditService.listRuns(next),
        traceAuditService.getStats(next),
      ]);
      setRuns(Array.isArray(page?.records) ? page.records : []);
      setTotal(page?.total ?? 0);
      setFilters((current) => ({
        ...current,
        ...next,
        current: page?.current ?? next.current ?? 1,
        size: page?.size ?? next.size ?? 20,
      }));
      setStats(nextStats);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载推理链路失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(initialFilters);
    traceAuditService.getHealth().then(setHealth)
      .catch((error) => toast.error(getErrorMessage(error, "加载审计健康状态失败")));
  }, []);

  const update = (key: keyof TraceFilters, value: string) =>
    setFilters((current) => ({ ...current, [key]: value }));

  const openDetail = async (traceId: string) => {
    try {
      setDetail(await traceAuditService.getDetail(traceId));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载链路详情失败"));
    }
  };

  const waterfall = useMemo(
    () => buildWaterfall(detail?.nodes ?? [], detail?.run),
    [detail?.nodes, detail?.run],
  );

  const detailStats = useMemo(() => {
    const nodes = detail?.nodes ?? [];
    const success = nodes.filter((n) => n.status === "SUCCESS").length;
    const failed = nodes.filter((n) => ["ERROR", "FAILED", "TIMEOUT"].includes(n.status)).length;
    const running = nodes.filter((n) => n.status === "RUNNING").length;
    const hotspot = [...nodes].sort((a, b) => (b.durationMs ?? 0) - (a.durationMs ?? 0))[0];
    return { success, failed, running, hotspot, total: nodes.length };
  }, [detail?.nodes]);

  return (
    <div className="trace-list-page space-y-4 p-4">
      <section className="trace-list-header-card">
        <div className="trace-list-header-content">
          <div className="trace-list-header-copy">
            <div className="trace-list-header-tag"><Activity className="mr-1 inline h-3.5 w-3.5" /> Reasoning Trace</div>
            <h2 className="trace-list-header-title">推理链路审计</h2>
            <p className="trace-list-header-description">
              按租户隔离查询路由、意图、Workflow、ReAct、工具调用和权限判定的完整链路。
            </p>
          </div>
          {health && (
            <div className="trace-list-kpi-group">
              <div className={`trace-list-kpi-pill ${health.status === "UP" ? "trace-list-kpi-pill--successrate" : "trace-list-kpi-pill--p95"}`}>
                <div className="trace-list-kpi-text">
                  <div className="trace-list-kpi-label">审计健康</div>
                  <div className="trace-list-kpi-value">{health.status === "UP" ? "正常" : "降级"}</div>
                </div>
              </div>
            </div>
          )}
        </div>
      </section>

      <section className="trace-list-stat-grid">
        <div className="trace-list-stat-card">
          <div className="trace-list-stat-icon is-cyan"><Activity className="h-4 w-4" /></div>
          <div className="trace-list-stat-content">
            <div className="trace-list-stat-title">总请求</div>
            <div className="trace-list-stat-value-row">
              <div className="trace-list-stat-value">{stats?.total ?? total}</div>
            </div>
          </div>
        </div>
        <div className="trace-list-stat-card">
          <div className="trace-list-stat-icon is-emerald"><CheckCircle2 className="h-4 w-4" /></div>
          <div className="trace-list-stat-content">
            <div className="trace-list-stat-title">成功次数</div>
            <div className="trace-list-stat-value-row">
              <div className="trace-list-stat-value">{stats?.successCount ?? 0}</div>
            </div>
          </div>
        </div>
        <div className="trace-list-stat-card">
          <div className="trace-list-stat-icon is-rose"><XCircle className="h-4 w-4" /></div>
          <div className="trace-list-stat-content">
            <div className="trace-list-stat-title">失败次数</div>
            <div className="trace-list-stat-value-row">
              <div className="trace-list-stat-value">{stats?.errorCount ?? 0}</div>
            </div>
          </div>
        </div>
        <div className="trace-list-stat-card">
          <div className="trace-list-stat-icon is-indigo"><Activity className="h-4 w-4" /></div>
          <div className="trace-list-stat-content">
            <div className="trace-list-stat-title">成功率</div>
            <div className="trace-list-stat-value-row">
              <div className="trace-list-stat-value">{(stats?.successRate ?? 0).toFixed(1)}</div>
              <div className="trace-list-stat-unit">%</div>
            </div>
          </div>
        </div>
      </section>

      <section className="trace-list-filter-card">
        <div className="trace-list-filter-body grid gap-3 md:grid-cols-2 xl:grid-cols-6">
          <Input aria-label="链路标识" placeholder="traceId" value={filters.traceId || ""} onChange={(event) => update("traceId", event.target.value)} />
          <Input aria-label="用户标识" placeholder="用户 ID / 用户名关联 ID" value={filters.userId || ""} onChange={(event) => update("userId", event.target.value)} />
          <Input aria-label="会话标识" placeholder="会话 ID" value={filters.conversationId || ""} onChange={(event) => update("conversationId", event.target.value)} />
          <Input aria-label="任务标识" placeholder="taskId" value={filters.taskId || ""} onChange={(event) => update("taskId", event.target.value)} />
          <select aria-label="运行状态" className="h-10 rounded-md border border-input bg-background px-3 text-sm" value={filters.status || ""} onChange={(event) => update("status", event.target.value)}>
            <option value="">全部状态</option>
            <option value="RUNNING">执行中</option>
            <option value="SUCCESS">成功</option>
            <option value="ERROR">失败</option>
            <option value="CANCELLED">已取消</option>
          </select>
          <Button onClick={() => void load({ ...filters, current: 1 })}>
            <Search className="mr-2 h-4 w-4" />查询
          </Button>
        </div>
      </section>

      <section className="trace-list-table-card">
        <div className="trace-list-table-header px-4 py-3 text-sm text-slate-500">
          共 {total} 条请求链路
          {stats ? ` · 成功 ${stats.successCount} · 失败 ${stats.errorCount}` : ""}
        </div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>问题</TableHead>
              <TableHead>用户</TableHead>
              <TableHead>会话 / 任务</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>耗时</TableHead>
              <TableHead>traceId</TableHead>
              <TableHead>操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">正在加载推理链路…</TableCell></TableRow>}
            {!loading && runs.length === 0 && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">没有符合条件的推理链路</TableCell></TableRow>}
            {runs.map((run) => (
              <TableRow key={run.traceId}>
                <TableCell className="max-w-xs">
                  <div className="truncate">{run.question || "-"}</div>
                  <div className="text-xs text-slate-400">{run.startTime || "-"}</div>
                </TableCell>
                <TableCell>
                  <div className="font-medium text-slate-800">{userLabel(run)}</div>
                  {run.username && run.userId && run.username !== run.userId && (
                    <div className="font-mono text-[11px] text-slate-400">{run.userId}</div>
                  )}
                </TableCell>
                <TableCell className="font-mono text-xs">
                  <div>{run.conversationId || "-"}</div>
                  <div className="text-slate-400">{run.taskId || "-"}</div>
                </TableCell>
                <TableCell><Badge variant={statusVariant(run.status)}>{run.status}</Badge></TableCell>
                <TableCell>{run.durationMs == null ? "-" : `${run.durationMs} ms`}</TableCell>
                <TableCell className="max-w-44 truncate font-mono text-xs text-slate-500">{run.traceId}</TableCell>
                <TableCell>
                  <Button size="sm" variant="outline" onClick={() => void openDetail(run.traceId)}>查看链路</Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <AdminPagination
          page={{ current: filters.current ?? 1, size: filters.size ?? 20, total }}
          disabled={loading}
          onChange={(next) => void load({ ...filters, current: next.current, size: next.size })}
        />
      </section>

      <Dialog open={detail !== null} onOpenChange={(open) => { if (!open) setDetail(null); }}>
        <DialogContent className="trace-console-dialog max-h-[92vh] max-w-6xl overflow-hidden p-0">
          <div className="trace-console">
            <div className="trace-console-hero">
              <DialogHeader className="space-y-3 text-left">
                <div className="trace-console-chip">
                  <Sparkles className="h-3.5 w-3.5" />
                  Reasoning Command Center
                </div>
                <DialogTitle className="trace-console-title">完整审计链路</DialogTitle>
                <DialogDescription className="trace-console-id-row">
                  <span className="trace-console-id">{detail?.run.traceId}</span>
                  {detail?.run.traceId && (
                    <button
                      type="button"
                      className="trace-console-copy"
                      aria-label="复制 traceId"
                      onClick={() => copyText(detail.run.traceId)}
                    >
                      <Copy className="h-3.5 w-3.5" />
                    </button>
                  )}
                </DialogDescription>
              </DialogHeader>

              <div className="trace-console-progress">
                <div className="trace-console-progress-head">
                  <span>节点执行结果</span>
                  <strong>{detailStats.success} 成功 / 共 {detailStats.total}</strong>
                </div>
                <div className="trace-console-progress-track">
                  <div
                    className="trace-console-progress-fill"
                    style={{
                      width: `${detailStats.total ? Math.round((detailStats.success / detailStats.total) * 100) : 0}%`,
                    }}
                  />
                </div>
                <p className="trace-console-hint">
                  总耗时是整次请求的墙钟时间；节点可嵌套，各节点耗时相加不必等于总耗时。时间条按节点起止相对总时长对齐。
                  LLM 路由 / 模型调用在意图识别阶段返回的是分类打分，不是最终用户答复。
                </p>
                <div className="trace-console-meta">
                  <div><span>用户</span><strong>{userLabel(detail?.run ?? {})}</strong></div>
                  <div><span>状态</span><strong data-tone={statusTone(detail?.run.status)}>{detail?.run.status || "-"}</strong></div>
                  <div><span>总耗时</span><strong>{detail?.run.durationMs == null ? "-" : `${detail.run.durationMs} ms`}</strong></div>
                  <div><span>问题</span><strong title={detail?.run.question || ""}>{detail?.run.question || "-"}</strong></div>
                </div>
                {(detail?.run.status === "ERROR" || detail?.run.status === "FAILED" || detail?.run.errorMessage) && (
                  <div className="trace-console-error" role="alert">
                    <strong>运行错误</strong>
                    <span>{detail?.run.errorMessage || "未记录具体错误信息，请结合节点失败项或服务端日志排查。"}</span>
                  </div>
                )}
              </div>
            </div>

            <div className="trace-console-body">
              <div className="trace-console-kpis">
                <div className="trace-console-kpi">
                  <Timer className="h-4 w-4" />
                  <div>
                    <div className="trace-console-kpi-label">节点数</div>
                    <div className="trace-console-kpi-value">{detailStats.total}</div>
                  </div>
                </div>
                <div className="trace-console-kpi is-success">
                  <CheckCircle2 className="h-4 w-4" />
                  <div>
                    <div className="trace-console-kpi-label">成功</div>
                    <div className="trace-console-kpi-value">{detailStats.success}</div>
                  </div>
                </div>
                <div className="trace-console-kpi is-danger">
                  <XCircle className="h-4 w-4" />
                  <div>
                    <div className="trace-console-kpi-label">失败</div>
                    <div className="trace-console-kpi-value">{detailStats.failed}</div>
                  </div>
                </div>
                <div className="trace-console-kpi is-amber">
                  <Cpu className="h-4 w-4" />
                  <div>
                    <div className="trace-console-kpi-label">最耗时</div>
                    <div className="trace-console-kpi-value truncate" title={detailStats.hotspot?.nodeName || ""}>
                      {detailStats.hotspot?.nodeName || "-"}
                    </div>
                  </div>
                </div>
              </div>

              {waterfall.length > 0 && (
                <div className="trace-console-rail" aria-label="节点分步轨道">
                  {waterfall.map(({ node, index }) => {
                    const Icon = nodeIcon(node.nodeType);
                    return (
                      <div key={`rail-${node.nodeId}`} className="trace-console-rail-item" data-tone={statusTone(node.status)}>
                        <div className="trace-console-rail-dot"><Icon className="h-3.5 w-3.5" /></div>
                        <div className="trace-console-rail-label">
                          <span>STEP {index + 1}</span>
                          <strong>{nodeTitle(node)}</strong>
                        </div>
                        {index < waterfall.length - 1 && <div className="trace-console-rail-line" />}
                      </div>
                    );
                  })}
                </div>
              )}

              <section className="trace-console-timeline">
                <div className="trace-console-section-head">
                  <h3>推理节点时间线</h3>
                  <span>{detailStats.total} 个节点 · 按墙钟对齐</span>
                </div>
                <div className="trace-console-timeline-list">
                  {waterfall.length === 0 && (
                    <div className="trace-console-empty">暂无推理节点</div>
                  )}
                  {waterfall.map(({ node, left, width, duration, relative, index }) => {
                    const Icon = nodeIcon(node.nodeType);
                    const summary = node.resultSummary || node.errorMessage || null;
                    return (
                      <div
                        key={node.nodeId}
                        className="trace-console-step"
                        data-tone={statusTone(node.status)}
                        style={{ ["--depth" as string]: Math.min(node.depth ?? 0, 4) }}
                      >
                        <div className="trace-console-step-axis">
                          <div className="trace-console-step-index">{index + 1}</div>
                          <div className="trace-console-step-line" />
                        </div>
                        <div className="trace-console-step-card">
                          <div className="trace-console-step-top">
                            <div className="trace-console-step-title">
                              <span className="trace-console-step-icon"><Icon className="h-4 w-4" /></span>
                              <div>
                                <div className="trace-console-step-name">{nodeTitle(node)}</div>
                                <div className="trace-console-step-type">{nodeSubtitle(node)}</div>
                              </div>
                            </div>
                            <div className="trace-console-step-metrics">
                              <span className="trace-console-step-ms">{duration} ms</span>
                              <span className="trace-status-chip" data-status={statusTone(node.status)}>
                                <span className="trace-status-chip-dot" />
                                {node.status}
                              </span>
                            </div>
                          </div>
                          <div className="trace-console-bar-row">
                            <div className="trace-console-bar-track" title={`相对总时长偏移与宽度`}>
                              <div
                                className="trace-console-bar-span"
                                style={{ left: `${left}%`, width: `${width}%` }}
                              />
                              <div
                                className="trace-console-bar-fill"
                                style={{ width: `${relative}%` }}
                              />
                            </div>
                            <span className="trace-console-bar-share">{duration} ms</span>
                          </div>
                          {summary && (
                            <div className="trace-console-step-result">
                              <span>结果</span>
                              <code title={node.extraData || summary}>{summary}</code>
                            </div>
                          )}
                          {node.className && (
                            <div className="trace-console-step-meta">
                              {node.className.split(".").slice(-1)[0]}
                              {node.methodName ? `#${node.methodName}` : ""}
                            </div>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              </section>

              <section className="trace-console-events">
                <div className="trace-console-section-head">
                  <h3>工具、权限与业务事件</h3>
                  <span>{detail?.events?.length ?? 0} 条跨域事件</span>
                </div>
                {(detail?.events?.length ?? 0) === 0 && (
                  <div className="trace-console-empty">本链路暂无跨域事件</div>
                )}
                <div className="trace-console-event-list">
                  {(detail?.events ?? []).map((event) => (
                    <div key={`${event.category}-${event.id}`} className="trace-console-event">
                      <div className="trace-console-event-top">
                        <Badge variant="outline">{AUDIT_CATEGORY_LABEL[event.category]}</Badge>
                        <span className="trace-console-event-code">
                          {AUDIT_EVENT_LABEL[event.eventCode] ?? event.eventCode}
                        </span>
                        <span className="trace-console-event-time">{event.createdTime || "-"}</span>
                      </div>
                      <div className="trace-console-event-flow">
                        <span>{event.actorName || event.actorId || "-"}</span>
                        <span className="trace-console-arrow">→</span>
                        <span>{[event.targetType, event.targetId].filter(Boolean).join("/") || "-"}</span>
                      </div>
                      {event.detail && <div className="trace-console-event-detail">{event.detail}</div>}
                    </div>
                  ))}
                </div>
              </section>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
