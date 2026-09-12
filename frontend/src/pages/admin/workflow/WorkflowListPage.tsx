import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { GitBranch, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { getErrorMessage } from "@/utils/error";
import {
  WF_STATUS_LABEL,
  workflowService,
  type WfDefinition,
  type WfVersionStatus
} from "@/services/workflowService";

function statusVariant(status?: WfVersionStatus | null): "default" | "secondary" | "outline" | "destructive" {
  switch (status) {
    case "PUBLISHED":
      return "default";
    case "IN_REVIEW":
    case "APPROVED":
      return "secondary";
    case "REJECTED":
    case "RETIRED":
      return "destructive";
    default:
      return "outline";
  }
}

function formatTime(value?: string | null) {
  if (!value) return "-";
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;
  return d.toLocaleString("zh-CN", { hour12: false });
}

export function WorkflowListPage() {
  const navigate = useNavigate();
  const [defs, setDefs] = useState<WfDefinition[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const list = await workflowService.listDefinitions();
      setDefs(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.error(getErrorMessage(e, "加载工作流列表失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold">工作流中心</h2>
          <p className="mt-1 text-sm text-slate-500">
            管理流程定义与版本生命周期：草稿 → 审核 → 发布。点击行进入设计器。
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
            <RefreshCw className="mr-1 h-4 w-4" /> 刷新
          </Button>
          <Button size="sm" onClick={() => navigate("/admin/workflow/new")}>
            <Plus className="mr-1 h-4 w-4" /> 新建工作流
          </Button>
        </div>
      </div>

      <Card>
        <CardContent className="p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>流程键</TableHead>
                <TableHead>最新版本</TableHead>
                <TableHead>运行中版本</TableHead>
                <TableHead>更新时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {!loading && defs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-slate-400">
                    暂无工作流，点击「新建工作流」创建
                  </TableCell>
                </TableRow>
              )}
              {loading && (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-slate-400">
                    加载中…
                  </TableCell>
                </TableRow>
              )}
              {defs.map((d) => (
                <TableRow key={d.id} className="cursor-pointer" onClick={() => navigate(`/admin/workflow/${d.defKey}`)}>
                  <TableCell>
                    <div className="font-medium text-slate-900">{d.name || "-"}</div>
                    {d.description ? (
                      <div className="mt-0.5 max-w-xs truncate text-xs text-slate-400">{d.description}</div>
                    ) : null}
                  </TableCell>
                  <TableCell className="font-mono text-xs text-slate-600">{d.defKey}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <span className="text-sm text-slate-700">
                        {d.latestVersionNumber != null ? `v${d.latestVersionNumber}` : "-"}
                      </span>
                      {d.latestStatus ? (
                        <Badge variant={statusVariant(d.latestStatus)}>
                          {WF_STATUS_LABEL[d.latestStatus] ?? d.latestStatus}
                        </Badge>
                      ) : null}
                    </div>
                  </TableCell>
                  <TableCell className="text-slate-600">
                    {d.publishedVersionNumber != null
                      ? `v${d.publishedVersionNumber}`
                      : <span className="text-slate-400">未发布</span>}
                  </TableCell>
                  <TableCell className="text-sm text-slate-500">{formatTime(d.updateTime)}</TableCell>
                  <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                    <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/workflow/${d.defKey}`)}>
                      <GitBranch className="mr-1 h-4 w-4" /> 编辑
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
