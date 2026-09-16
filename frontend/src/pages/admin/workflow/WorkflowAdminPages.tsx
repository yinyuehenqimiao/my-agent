import { useEffect, useState } from "react";
import { Route, Shield, Trash2, Wrench } from "lucide-react";
import { AdminPagination } from "@/components/admin/AdminPagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import {
  AUDIT_CATEGORY_LABEL,
  AUDIT_EVENT_LABEL,
  type AuditCategory,
  type AuditEntry,
  workflowService
} from "@/services/workflowService";
import { toast } from "sonner";
import { getErrorMessage } from "@/utils/error";
import {
  agentAdminService,
  ROUTE_DECISION_LABEL,
  TOOL_RISK_LABEL,
  type RouteBinding,
  type RouteOptions,
  type RouteSimulationResult,
  type RouteTargetType,
  type ToolCatalogItem,
  type ToolSource,
} from "@/services/agentAdminService";

const SOURCE_LABEL: Record<ToolSource, string> = {
  REACT: "开放式智能体",
  WORKFLOW: "固定流程",
  MCP: "外部连接器",
  ADMIN: "管理后台",
};

function riskVariant(risk: ToolCatalogItem["riskLevel"]) {
  return risk === "HIGH" ? "destructive" : risk === "MEDIUM" ? "secondary" : "outline";
}

function actorLabel(entry: { actorName?: string | null; actorId?: string | null }) {
  return entry.actorName || entry.actorId || "-";
}

/** 工具中心：展示工具 Schema、风险、超时、重试、敏感字段、权限。 */
export function ToolCenterPage() {
  const [tools, setTools] = useState<ToolCatalogItem[]>([]);
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = async (page = current, pageSize = size, kw = query) => {
    setLoading(true);
    try {
      const result = await agentAdminService.listTools({ current: page, size: pageSize, keyword: kw });
      setTools(Array.isArray(result?.records) ? result.records : []);
      setTotal(result?.total ?? 0);
      setCurrent(result?.current ?? page);
      setSize(result?.size ?? pageSize);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载工具目录失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1, size, query);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  return (
    <div className="wf-tech-page space-y-4 p-4">
      <section className="wf-tech-hero">
        <div className="wf-tech-hero-copy">
          <div className="wf-tech-chip"><Wrench className="h-3.5 w-3.5" /> Tool Fabric</div>
          <h2 className="wf-tech-title">运行时工具目录</h2>
          <p className="wf-tech-subtitle">
            只读查看当前 Agent 已装载的工具（含本地能力与已发布 MCP）。
            ERP 连接登记、工具审核、意图绑定请到「ERP 连接」完成，不在本页配置。
          </p>
        </div>
      </section>

      <section className="wf-tech-panel space-y-4">
        <div className="wf-tech-toolbar">
          <Input
            placeholder="搜索工具名 / 描述 / 权限码"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") setQuery(keyword.trim()); }}
          />
          <Button variant="outline" onClick={() => setQuery(keyword.trim())}>查询</Button>
        </div>
        {loading && <div className="p-8 text-center text-sm text-slate-400">正在读取工具目录…</div>}
        {!loading && tools.length === 0 && (
          <div className="p-8 text-center text-sm text-slate-500">
            当前没有已装载工具。若要接入 ERP，请先到「ERP 连接」完成发现、审核与意图发布。
          </div>
        )}
        <div className="grid gap-3 xl:grid-cols-2">
          {tools.map((tool) => (
            <div key={tool.name} className={`wf-tech-tool-card ${!tool.enabled ? "opacity-60" : ""}`}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-medium text-slate-900">{tool.description || tool.name}</div>
                  <div className="mt-1 font-mono text-xs text-cyan-700/80">{tool.name}</div>
                </div>
                <Badge variant={tool.enabled ? "default" : "secondary"}>{tool.enabled ? "已启用" : "已停用"}</Badge>
              </div>
              <div className="mt-3 flex flex-wrap gap-2 text-xs">
                <Badge variant={riskVariant(tool.riskLevel)}>{TOOL_RISK_LABEL[tool.riskLevel] ?? tool.riskLevel}</Badge>
                <Badge variant="outline">{tool.readOnly ? "只读" : "会修改数据"}</Badge>
                {tool.requiresConfirmation && <Badge variant="secondary">需要用户确认</Badge>}
                {tool.idempotent && <Badge variant="outline">支持防重复提交</Badge>}
              </div>
              <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <div><dt className="text-slate-400">超时时间</dt><dd>{tool.timeoutMs} 毫秒</dd></div>
                <div><dt className="text-slate-400">失败重试</dt><dd>{tool.maxRetries > 0 ? `${tool.maxRetries} 次` : "不重试"}</dd></div>
                <div className="col-span-2"><dt className="text-slate-400">使用权限</dt><dd>{tool.permissionCode || "登录用户"}</dd></div>
                <div className="col-span-2"><dt className="text-slate-400">允许来源</dt><dd>{tool.allowedSources?.map((source) => SOURCE_LABEL[source] ?? source).join("、") || "未限制"}</dd></div>
              </dl>
            </div>
          ))}
        </div>
        <AdminPagination
          page={{ current, size, total }}
          disabled={loading}
          onChange={(next) => {
            setCurrent(next.current);
            setSize(next.size);
            void load(next.current, next.size, query);
          }}
        />
      </section>
    </div>
  );
}

/** 统一审计查询页：按当前登录租户聚合平台、权限、工具与业务流水。 */
export function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [category, setCategory] = useState<AuditCategory>("ALL");
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = async (page = current, pageSize = size, cat = category) => {
    setLoading(true);
    try {
      const result = await workflowService.listAudit({ current: page, size: pageSize, category: cat });
      setEntries(Array.isArray(result?.records) ? result.records : []);
      setTotal(result?.total ?? 0);
      setCurrent(result?.current ?? page);
      setSize(result?.size ?? pageSize);
    } catch (e) {
      toast.error(getErrorMessage(e, "加载审计失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1, size, category);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category]);

  return (
    <div className="wf-tech-page space-y-4 p-4">
      <section className="wf-tech-hero">
        <div className="wf-tech-hero-copy">
          <div className="wf-tech-chip"><Shield className="h-3.5 w-3.5" /> Security Telemetry</div>
          <h2 className="wf-tech-title">统一安全审计</h2>
          <p className="wf-tech-subtitle">仅展示当前登录租户的数据，统一查看平台变更、权限判定、工具调用和企业业务事件。</p>
        </div>
        <label className="grid gap-1 text-sm text-slate-600">
          审计分类
          <select aria-label="审计分类" className="h-10 min-w-40 rounded-md border border-input bg-background px-3"
            value={category} onChange={(event) => setCategory(event.target.value as AuditCategory)}>
            {(Object.keys(AUDIT_CATEGORY_LABEL) as AuditCategory[]).map((value) => (
              <option key={value} value={value}>{AUDIT_CATEGORY_LABEL[value]}</option>
            ))}
          </select>
        </label>
      </section>

      <section className="wf-tech-panel">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>操作</TableHead>
              <TableHead>分类</TableHead>
              <TableHead>操作人</TableHead>
              <TableHead>目标</TableHead>
              <TableHead>详情</TableHead>
              <TableHead>链路标识</TableHead>
              <TableHead>时间</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {!loading && entries.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-slate-400">当前分类下暂无审计记录</TableCell>
              </TableRow>
            )}
            {loading && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">正在加载审计记录…</TableCell></TableRow>}
            {entries.map((e) => (
              <TableRow key={`${e.category}-${e.id}`}>
                <TableCell>
                  <div>{AUDIT_EVENT_LABEL[e.eventCode] ?? e.eventCode}</div>
                  <div className="font-mono text-xs text-slate-400">{e.eventCode}</div>
                </TableCell>
                <TableCell><Badge variant="outline">{AUDIT_CATEGORY_LABEL[e.category]}</Badge></TableCell>
                <TableCell>
                  <div className="font-medium text-slate-800">{actorLabel(e)}</div>
                  {e.actorName && e.actorId && e.actorName !== e.actorId && (
                    <div className="font-mono text-[11px] text-slate-400">{e.actorId}</div>
                  )}
                </TableCell>
                <TableCell className="text-slate-500">{[e.targetType, e.targetId].filter(Boolean).join("/") || "-"}</TableCell>
                <TableCell className="max-w-xs truncate text-xs text-slate-500">{e.detail ?? "-"}</TableCell>
                <TableCell className="font-mono text-xs text-slate-400">{e.traceId ?? "-"}</TableCell>
                <TableCell className="text-slate-500">{e.createdTime}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <AdminPagination
          page={{ current, size, total }}
          disabled={loading}
          onChange={(next) => {
            setCurrent(next.current);
            setSize(next.size);
            void load(next.current, next.size, category);
          }}
        />
      </section>
    </div>
  );
}

/** 路由模拟页：选择模拟用户与输入问题，对比路由决策。 */
export function RouteSimulationPage() {
  const [routes, setRoutes] = useState<RouteBinding[]>([]);
  const [options, setOptions] = useState<RouteOptions>({ intents: [], workflows: [], reactConfigs: [] });
  const [routeKey, setRouteKey] = useState("");
  const [intentCode, setIntentCode] = useState("");
  const [targetType, setTargetType] = useState<RouteTargetType>("WORKFLOW");
  const [target, setTarget] = useState("");
  const [saving, setSaving] = useState(false);
  const [question, setQuestion] = useState("");
  const [hasPendingAction, setHasPendingAction] = useState(false);
  const [result, setResult] = useState<RouteSimulationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);

  const loadRoutes = async (page = current, pageSize = size, kw = query) => {
    try {
      const [bindings, available] = await Promise.all([
        agentAdminService.listRoutes({ current: page, size: pageSize, keyword: kw }),
        agentAdminService.getRouteOptions(),
      ]);
      setRoutes(Array.isArray(bindings?.records) ? bindings.records : []);
      setTotal(bindings?.total ?? 0);
      setCurrent(bindings?.current ?? page);
      setSize(bindings?.size ?? pageSize);
      setOptions(available || { intents: [], workflows: [], reactConfigs: [] });
    } catch (error) {
      toast.error(getErrorMessage(error, "加载执行管线失败"));
    }
  };

  useEffect(() => {
    void loadRoutes(1, size, query);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  const saveRoute = async () => {
    if (!routeKey || !intentCode || !target) {
      toast.error("请完整选择业务意图、执行方式和执行目标");
      return;
    }
    setSaving(true);
    try {
      await agentAdminService.saveRoute(routeKey, { intentCode, targetType, target });
      toast.success("执行管线已保存");
      await loadRoutes();
    } catch (error) {
      toast.error(getErrorMessage(error, "保存执行管线失败"));
    } finally {
      setSaving(false);
    }
  };

  const editRoute = (route: RouteBinding) => {
    setRouteKey(route.routeKey);
    setIntentCode(route.intentCode);
    setTargetType(route.targetType);
    setTarget(route.target);
  };

  const deleteRoute = async (key: string) => {
    if (!window.confirm("确定删除这条生产执行管线吗？删除后相关意图将无法进入固定流程。")) return;
    try {
      await agentAdminService.deleteRoute(key);
      toast.success("执行管线已删除");
      await loadRoutes();
    } catch (error) {
      toast.error(getErrorMessage(error, "删除执行管线失败"));
    }
  };

  const targetOptions = targetType === "WORKFLOW" ? options.workflows : options.reactConfigs;

  const simulate = async () => {
    if (!question.trim()) {
      toast.error("请输入要模拟的问题");
      return;
    }
    setLoading(true);
    setResult(null);
    try {
      setResult(await agentAdminService.simulateRoute({ question: question.trim(), hasPendingAction }));
    } catch (error) {
      toast.error(getErrorMessage(error, "路由模拟失败"));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="wf-tech-page space-y-4 p-4">
      <section className="wf-tech-hero">
        <div className="wf-tech-hero-copy">
          <div className="wf-tech-chip"><Route className="h-3.5 w-3.5" /> Routing Lab</div>
          <h2 className="wf-tech-title">执行管线与路由模拟</h2>
          <p className="wf-tech-subtitle">先把业务意图绑定到已发布固定流程或受控开放式智能体，再用当前登录身份验证实际路由结果。</p>
        </div>
      </section>

      <section className="wf-tech-panel space-y-4">
        <div>
          <h3 className="font-medium">生产执行管线</h3>
          <p className="text-sm text-slate-500">固定流程必须已发布且已接入运行时；每个业务意图只能绑定一条管线。</p>
        </div>
        <div className="grid gap-3 lg:grid-cols-4">
          <Input aria-label="路由标识" placeholder="路由标识，如 leave-route" value={routeKey}
            onChange={(event) => setRouteKey(event.target.value.trim().toLowerCase())} />
          <select aria-label="业务意图" className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={intentCode} onChange={(event) => {
              const value = event.target.value;
              setIntentCode(value);
              if (!routeKey) setRouteKey(`${value}-route`);
            }}>
            <option value="">选择业务意图</option>
            {options.intents.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
          <select aria-label="执行方式" className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={targetType} onChange={(event) => { setTargetType(event.target.value as RouteTargetType); setTarget(""); }}>
            <option value="WORKFLOW">固定审批流程</option>
            <option value="REACT">开放式智能体</option>
          </select>
          <select aria-label="执行目标" className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            value={target} onChange={(event) => setTarget(event.target.value)}>
            <option value="">选择执行目标</option>
            {targetOptions.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
          </select>
        </div>
        <div className="wf-tech-toolbar">
          <Button type="button" disabled={saving} onClick={() => void saveRoute()}>{saving ? "正在保存…" : "保存执行管线"}</Button>
          <Input
            placeholder="搜索路由 / 意图"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => { if (e.key === "Enter") setQuery(keyword.trim()); }}
          />
          <Button variant="outline" onClick={() => setQuery(keyword.trim())}>筛选</Button>
        </div>
        <Table>
          <TableHeader><TableRow><TableHead>业务意图</TableHead><TableHead>执行方式</TableHead><TableHead>执行目标</TableHead><TableHead className="w-36">操作</TableHead></TableRow></TableHeader>
          <TableBody>
            {routes.length === 0 && <TableRow><TableCell colSpan={4} className="py-8 text-center text-slate-400">尚未配置生产执行管线</TableCell></TableRow>}
            {routes.map((route) => (
              <TableRow key={route.routeKey}>
                <TableCell><div>{route.intentName || route.intentCode}</div><div className="font-mono text-xs text-slate-400">{route.routeKey}</div></TableCell>
                <TableCell>{route.targetType === "WORKFLOW" ? "固定审批流程" : "开放式智能体"}</TableCell>
                <TableCell className="font-mono text-xs">{route.target}</TableCell>
                <TableCell><div className="flex gap-2"><Button type="button" variant="outline" size="sm" onClick={() => editRoute(route)}>编辑</Button><Button aria-label={`删除 ${route.intentName || route.intentCode}`} type="button" variant="ghost" size="sm" onClick={() => void deleteRoute(route.routeKey)}><Trash2 className="h-4 w-4" /></Button></div></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
        <AdminPagination
          page={{ current, size, total }}
          onChange={(next) => {
            setCurrent(next.current);
            setSize(next.size);
            void loadRoutes(next.current, next.size, query);
          }}
        />
      </section>

      <section className="wf-tech-panel space-y-4">
        <h3 className="font-medium">使用真实身份模拟</h3>
        <Textarea
          rows={4}
          maxLength={2000}
          placeholder="例如：我今年还剩多少天年假？"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
        />
        <label className="flex items-center gap-2 text-sm text-slate-600">
          <Checkbox checked={hasPendingAction} onCheckedChange={(checked) => setHasPendingAction(checked === true)} />
          当前会话存在待确认或待审批操作
        </label>
        <Button type="button" onClick={() => void simulate()} disabled={loading || !question.trim()}>
          {loading ? "正在分析…" : "开始模拟"}
        </Button>
        {result && (
          <div className="wf-tech-result space-y-3 p-4 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="text-slate-500">最终决策</span>
              <Badge variant={result.decision === "DENY" ? "destructive" : "default"}>
                {ROUTE_DECISION_LABEL[result.decision] ?? result.decision}
              </Badge>
            </div>
            <dl className="grid gap-2 sm:grid-cols-2">
              <div><dt className="text-slate-400">识别意图</dt><dd>{result.intentName || "未识别"}</dd></div>
              <div><dt className="text-slate-400">置信度</dt><dd>{`${(result.confidence * 100).toFixed(1)}%`}</dd></div>
              <div><dt className="text-slate-400">执行目标</dt><dd>{result.target || "无"}</dd></div>
              <div><dt className="text-slate-400">意图标识</dt><dd className="font-mono text-xs">{result.intentCode || "-"}</dd></div>
            </dl>
            <p className="border-t border-cyan-100/80 pt-3 text-slate-600">判断依据：{result.reason}</p>
          </div>
        )}
      </section>
    </div>
  );
}
