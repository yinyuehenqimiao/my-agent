import { useEffect, useState } from "react";
import { Shield, Wrench } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { type AuditEntry, workflowService } from "@/services/workflowService";
import { toast } from "sonner";
import { getErrorMessage } from "@/utils/error";

/** 工具中心：展示工具 Schema、风险、超时、重试、敏感字段、权限。 */
export function ToolCenterPage() {
  return (
    <div className="space-y-4 p-4">
      <h2 className="text-lg font-semibold">工具与连接器</h2>
      <Card>
        <CardContent className="p-4 text-sm text-slate-600">
          <p className="flex items-center gap-2"><Wrench className="h-4 w-4" /> 企业工具经 Tool Gateway 统一安全管道调用。</p>
          <ul className="mt-2 list-disc space-y-1 pl-6">
            <li>输入输出 Schema 校验、读写属性、风险等级、超时、重试、幂等要求、确认要求、敏感字段脱敏</li>
            <li>模型传入的 userId/角色不可信；权限判断由 Tool Gateway 执行时二次鉴权</li>
            <li>写工具必须确认且携带幂等键</li>
          </ul>
        </CardContent>
      </Card>
    </div>
  );
}

/** 审计查询页。 */
export function AuditPage() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  useEffect(() => {
    workflowService.listAudit(100).then(setEntries).catch((e) => toast.error(getErrorMessage(e)));
  }, []);
  return (
    <div className="space-y-4 p-4">
      <h2 className="flex items-center gap-2 text-lg font-semibold"><Shield className="h-4 w-4" /> 审计查询</h2>
      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>操作</TableHead>
                <TableHead>操作人</TableHead>
                <TableHead>目标</TableHead>
                <TableHead>追踪号</TableHead>
                <TableHead>时间</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {entries.length === 0 && (
                <TableRow><TableCell colSpan={5} className="text-center text-slate-400">暂无审计记录</TableCell></TableRow>
              )}
              {entries.map((e) => (
                <TableRow key={e.id}>
                  <TableCell className="font-mono text-xs">{e.operation}</TableCell>
                  <TableCell>{e.operatorId ?? "-"}</TableCell>
                  <TableCell className="text-slate-500">{[e.targetType, e.targetId].filter(Boolean).join("/")}</TableCell>
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
  const [question, setQuestion] = useState("");
  const [decision, setDecision] = useState<string>("");

  // 路由模拟调用后端 RoutingService（通过聊天接口或专用接口；此处为前端演示，实际由后端决策）
  const simulate = () => {
    // 简化：实际应调用后端路由接口；此处仅展示决策结果占位
    if (question.includes("请假") || question.includes("年假")) setDecision("WORKFLOW → leave-approval");
    else if (question.includes("制度") || question.includes("政策")) setDecision("SYSTEM（RAG 知识库）");
    else if (question.trim()) setDecision("REACT（受控开放）");
    else setDecision("CLARIFY（参数不足）");
  };

  return (
    <div className="space-y-4 p-4">
      <h2 className="text-lg font-semibold">路由模拟</h2>
      <Card>
        <CardContent className="space-y-3 p-4">
          <p className="text-sm text-slate-600">选择模拟用户与输入问题，对比路由决策（实际由后端 RoutingService 决策）。</p>
          <textarea
            className="w-full rounded-md border border-slate-200 p-2 text-sm"
            rows={3}
            placeholder="输入问题，如：我今年剩几天年假？"
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
          />
          <button type="button" className="rounded-md bg-indigo-600 px-3 py-1.5 text-sm text-white" onClick={simulate}>
            模拟路由
          </button>
          {decision && (
            <p className="rounded-md bg-slate-50 p-2 text-sm">决策结果：<span className="font-mono">{decision}</span></p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
