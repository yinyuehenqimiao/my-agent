import { useEffect, useState } from "react";
import { Route, Shield, Trash2, Wrench } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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

/** 工具中心：展示工具 Schema、风险、超时、重试、敏感字段、权限。 */
export function ToolCenterPage() {
  const [tools, setTools] = useState<ToolCatalogItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    agentAdminService.listTools()
      .then((items) => setTools(Array.isArray(items) ? items : []))
      .catch((error) => toast.error(getErrorMessage(error, "加载工具目录失败")))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="space-y-4 p-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Wrench className="h-5 w-5" /> 工具与连接器</h2>
        <p className="mt-1 text-sm text-slate-500">这里展示系统当前实际注册的工具。调用仍会经过身份、权限、参数、超时、脱敏和审计检查。</p>
      </div>
      {loading && <Card><CardContent className="p-8 text-center text-sm text-slate-400">正在读取工具目录…</CardContent></Card>}
      {!loading && tools.length === 0 && (
        <Card><CardContent className="p-8 text-center text-sm text-slate-500">当前没有已注册工具，请先启用企业能力或连接器。</CardContent></Card>
      )}
      <div className="grid gap-3 xl:grid-cols-2">
        {tools.map((tool) => (
          <Card key={tool.name} className={!tool.enabled ? "opacity-60" : undefined}>
            <CardContent className="space-y-3 p-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-medium text-slate-900">{tool.description || tool.name}</div>
                  <div className="mt-1 font-mono text-xs text-slate-400">{tool.name}</div>
                </div>
                <Badge variant={tool.enabled ? "default" : "secondary"}>{tool.enabled ? "已启用" : "已停用"}</Badge>
              </div>
              <div className="flex flex-wrap gap-2 text-xs">
                <Badge variant={riskVariant(tool.riskLevel)}>{TOOL_RISK_LABEL[tool.riskLevel] ?? tool.riskLevel}</Badge>
                <Badge variant="outline">{tool.readOnly ? "只读" : "会修改数据"}</Badge>
                {tool.requiresConfirmation && <Badge variant="secondary">需要用户确认</Badge>}
                {tool.idempotent && <Badge variant="outline">支持防重复提交</Badge>}
              </div>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <div><dt className="text-slate-400">超时时间</dt><dd>{tool.timeoutMs} 毫秒</dd></div>
                <div><dt className="text-slate-400">失败重试</dt><dd>{tool.maxRetries > 0 ? `${tool.maxRetries} 次` : "不重试"}</dd></div>
                <div className="col-span-2"><dt className="text-slate-400">使用权限</dt><dd>{tool.permissionCode || "登录用户"}</dd></div>
                <div className="col-span-2"><dt className="text-slate-400">允许来源</dt><dd>{tool.allowedSources?.map((source) => SOURCE_LABEL[source] ?? source).join("、") || "未限制"}</dd></div>
              </dl>
              <details className="rounded-md bg-slate-50 p-3 text-xs">
                <summary className="cursor-pointer font-medium text-slate-600">高级配置（参数结构与敏感字段）</summary>
                <div className="mt-2 space-y-2 text-slate-500">
                  <p>敏感字段：{tool.sensitiveFields?.join("、") || "无"}</p>
                  <pre className="max-h-48 overflow-auto whitespace-pre-wrap break-all">{JSON.stringify(tool.inputSchema || {}, null, 2)}</pre>
                </div>
              </details>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

/** 统一审计查询页：按当前登录租户聚合平台、权限、工具与业务流水。 */
export function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [category, setCategory] = useState<AuditCategory>("ALL");
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    setLoading(true);
    workflowService.listAudit(100, category)
      .then((list) => setEntries(Array.isArray(list) ? list : []))
      .catch((e) => toast.error(getErrorMessage(e, "加载审计失败")))
      .finally(() => setLoading(false));
  }, [category]);
  return (
    <div className="space-y-4 p-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="flex items-center gap-2 text-lg font-semibold"><Shield className="h-4 w-4" /> 统一安全审计</h2>
        <p className="mt-1 text-sm text-slate-500">
            仅展示当前登录租户的数据，统一查看平台变更、权限判定、工具调用和企业业务事件。
        </p>
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
      </div>
      <Card>
        <CardContent className="p-0">
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
                  <TableCell colSpan={7} className="py-10 text-center text-slate-400">
                    当前分类下暂无审计记录
                  </TableCell>
                </TableRow>
              )}
              {loading && <TableRow><TableCell colSpan={7} className="py-10 text-center text-slate-400">正在加载审计记录…</TableCell></TableRow>}
              {entries.map((e) => (
                <TableRow key={`${e.category}-${e.id}`}>
                  <TableCell><div>{AUDIT_EVENT_LABEL[e.eventCode] ?? e.eventCode}</div><div className="font-mono text-xs text-slate-400">{e.eventCode}</div></TableCell>
                  <TableCell><Badge variant="outline">{AUDIT_CATEGORY_LABEL[e.category]}</Badge></TableCell>
                  <TableCell>{e.actorId ?? "-"}</TableCell>
                  <TableCell className="text-slate-500">{[e.targetType, e.targetId].filter(Boolean).join("/") || "-"}</TableCell>
                  <TableCell className="max-w-xs truncate text-xs text-slate-500">{e.detail ?? "-"}</TableCell>
                  <TableCell className="font-mono text-xs text-slate-400">{e.traceId ?? "-"}</TableCell>
                  <TableCell className="text-slate-500">{e.createdTime}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
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

  const loadRoutes = async () => {
    try {
      const [bindings, available] = await Promise.all([
        agentAdminService.listRoutes(),
        agentAdminService.getRouteOptions(),
      ]);
      setRoutes(Array.isArray(bindings) ? bindings : []);
      setOptions(available || { intents: [], workflows: [], reactConfigs: [] });
    } catch (error) {
      toast.error(getErrorMessage(error, "加载执行管线失败"));
    }
  };

  useEffect(() => { void loadRoutes(); }, []);

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
    <div className="space-y-4 p-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Route className="h-5 w-5" /> 执行管线与路由模拟</h2>
        <p className="mt-1 text-sm text-slate-500">先把业务意图绑定到已发布固定流程或受控开放式智能体，再用当前登录身份验证实际路由结果。</p>
      </div>
      <Card>
        <CardContent className="space-y-4 p-4">
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
          <Button type="button" disabled={saving} onClick={() => void saveRoute()}>{saving ? "正在保存…" : "保存执行管线"}</Button>
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
        </CardContent>
      </Card>
      <Card>
        <CardContent className="space-y-4 p-4">
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
            <div className="space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm">
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
              <p className="border-t border-slate-200 pt-3 text-slate-600">判断依据：{result.reason}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
