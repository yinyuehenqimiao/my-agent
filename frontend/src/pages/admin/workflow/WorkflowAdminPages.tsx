import { useEffect, useState } from "react";
import { Route, Shield, Wrench } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Textarea } from "@/components/ui/textarea";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { type AuditEntry, workflowService } from "@/services/workflowService";
import { toast } from "sonner";
import { getErrorMessage } from "@/utils/error";
import {
  agentAdminService,
  ROUTE_DECISION_LABEL,
  TOOL_RISK_LABEL,
  type RouteSimulationResult,
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

/** 审计查询页：平台 operation_audit（工作流生命周期等），不含请假/权限拒绝流水。 */
export function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  useEffect(() => {
    workflowService.listAudit(100)
      .then((list) => setEntries(Array.isArray(list) ? list : []))
      .catch((e) => toast.error(getErrorMessage(e, "加载审计失败")));
  }, []);
  return (
    <div className="space-y-4 p-4">
      <div>
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Shield className="h-4 w-4" /> 平台操作审计</h2>
        <p className="mt-1 text-sm text-slate-500">
          当前查看的是工作流平台操作（创建/审核/发布等）。请假业务流水在企业审批侧，权限拒绝流水在认证审计表，尚未合并到本页。
        </p>
      </div>
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>操作</TableHead>
                <TableHead>操作人</TableHead>
                <TableHead>目标</TableHead>
                <TableHead>详情</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="py-10 text-center text-slate-400">
                    暂无平台审计记录；对工作流执行创建/提交/发布后会出现在这里
                  </TableCell>
                </TableRow>
              )}
              {entries.map((e) => (
                <TableRow key={e.id}>
                  <TableCell className="font-mono text-xs">{e.operation}</TableCell>
                  <TableCell>{e.operatorId ?? "-"}</TableCell>
                  <TableCell className="text-slate-500">{[e.targetType, e.targetId].filter(Boolean).join("/") || "-"}</TableCell>
                  <TableCell className="max-w-xs truncate text-xs text-slate-500">{e.detail ?? e.traceId ?? "-"}</TableCell>
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
  const [question, setQuestion] = useState("");
  const [hasPendingAction, setHasPendingAction] = useState(false);
  const [result, setResult] = useState<RouteSimulationResult | null>(null);
  const [loading, setLoading] = useState(false);

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
        <h2 className="flex items-center gap-2 text-lg font-semibold"><Route className="h-5 w-5" /> 路由模拟</h2>
        <p className="mt-1 text-sm text-slate-500">使用当前登录人的真实权限和后端意图识别结果，只做决策预览，不执行流程或工具。</p>
      </div>
      <Card>
        <CardContent className="space-y-4 p-4">
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
