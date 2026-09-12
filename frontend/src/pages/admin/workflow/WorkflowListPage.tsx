import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { GitBranch, Plus, RefreshCw } from "lucide-react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow
} from "@/components/ui/table";
import { getErrorMessage } from "@/utils/error";
import { workflowService, type WfDefinition } from "@/services/workflowService";

export function WorkflowListPage() {
  const navigate = useNavigate();
  const [defs, setDefs] = useState<WfDefinition[]>([]);

  const load = () => {
    workflowService.listDefinitions().then(setDefs).catch((e) => toast.error(getErrorMessage(e)));
  };

  useEffect(() => { load(); }, []);

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">工作流中心</h2>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={load}>
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
                <TableHead>流程键</TableHead>
                <TableHead>名称</TableHead>
                <TableHead>当前发布版本</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {defs.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="text-center text-slate-400">
                    暂无工作流，点击「新建工作流」创建
                  </TableCell>
                </TableRow>
              )}
              {defs.map((d) => (
                <TableRow key={d.id}>
                  <TableCell className="font-mono text-sm">{d.defKey}</TableCell>
                  <TableCell>{d.name}</TableCell>
                  <TableCell className="text-slate-500">
                    {d.currentPublishedVersionId ? `#${d.currentPublishedVersionId}` : "未发布"}
                  </TableCell>
                  <TableCell className="text-right">
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
