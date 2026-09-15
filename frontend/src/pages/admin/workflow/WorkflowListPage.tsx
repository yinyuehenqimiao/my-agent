import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, GitBranch, Plus, RefreshCw, Search, Zap } from "lucide-react";
import { toast } from "sonner";

import { AdminPagination } from "@/components/admin/AdminPagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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
  const [keyword, setKeyword] = useState("");
  const [query, setQuery] = useState("");
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);

  const load = async (page = current, pageSize = size, kw = query) => {
    setLoading(true);
    try {
      const result = await workflowService.listDefinitions({ current: page, size: pageSize, keyword: kw });
      setDefs(Array.isArray(result?.records) ? result.records : []);
      setTotal(result?.total ?? 0);
      setCurrent(result?.current ?? page);
      setSize(result?.size ?? pageSize);
    } catch (e) {
      toast.error(getErrorMessage(e, "加载工作流列表失败"));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load(1, size, query);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  const stats = useMemo(() => {
    const published = defs.filter((d) => d.latestStatus === "PUBLISHED").length;
    const reviewing = defs.filter((d) => d.latestStatus === "IN_REVIEW").length;
    const draft = defs.filter((d) => !d.latestStatus || d.latestStatus === "DRAFT").length;
    return { total, published, reviewing, draft };
  }, [defs, total]);

  return (
    <div className="wf-tech-page space-y-4 p-4">
      <section className="wf-tech-hero">
        <div className="wf-tech-hero-copy">
          <div className="wf-tech-chip"><Zap className="h-3.5 w-3.5" /> Workflow Control Plane</div>
          <h2 className="wf-tech-title">工作流中心</h2>
          <p className="wf-tech-subtitle">
            管理流程定义与版本生命周期：草稿 → 审核 → 发布。点击行进入设计器。
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" className="wf-tech-ghost-btn" onClick={() => void load()} disabled={loading}>
            <RefreshCw className="mr-1 h-4 w-4" /> 刷新
          </Button>
          <Button size="sm" className="wf-tech-primary-btn" onClick={() => navigate("/admin/workflow/new")}>
            <Plus className="mr-1 h-4 w-4" /> 新建工作流
          </Button>
        </div>
      </section>

      <section className="wf-tech-stat-grid">
        <div className="wf-tech-stat-card">
          <div className="wf-tech-stat-label">流程总数</div>
          <div className="wf-tech-stat-value">{stats.total}</div>
        </div>
        <div className="wf-tech-stat-card is-emerald">
          <div className="wf-tech-stat-label">本页已发布</div>
          <div className="wf-tech-stat-value">{stats.published}</div>
        </div>
        <div className="wf-tech-stat-card is-amber">
          <div className="wf-tech-stat-label">本页待审核</div>
          <div className="wf-tech-stat-value">{stats.reviewing}</div>
        </div>
        <div className="wf-tech-stat-card is-cyan">
          <div className="wf-tech-stat-label">本页草稿</div>
          <div className="wf-tech-stat-value">{stats.draft}</div>
        </div>
      </section>

      <section className="wf-tech-panel">
        <div className="wf-tech-toolbar">
          <div className="relative min-w-[240px] flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
            <Input
              className="pl-9"
              placeholder="搜索流程键 / 名称 / 描述"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={(e) => { if (e.key === "Enter") setQuery(keyword.trim()); }}
            />
          </div>
          <Button variant="outline" onClick={() => setQuery(keyword.trim())}>查询</Button>
        </div>

        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[260px]">名称</TableHead>
              <TableHead className="w-[180px]">流程键</TableHead>
              <TableHead className="w-[180px]">最新版本</TableHead>
              <TableHead className="w-[120px]">运行中版本</TableHead>
              <TableHead className="w-[140px]">最近操作人</TableHead>
              <TableHead className="w-[180px]">更新时间</TableHead>
              <TableHead className="w-[100px] text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {!loading && defs.length === 0 && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-slate-400">
                  暂无工作流，点击「新建工作流」创建
                </TableCell>
              </TableRow>
            )}
            {loading && (
              <TableRow>
                <TableCell colSpan={7} className="py-10 text-center text-slate-400">加载中…</TableCell>
              </TableRow>
            )}
            {defs.map((d) => (
              <TableRow key={d.id} className="cursor-pointer" onClick={() => navigate(`/admin/workflow/${d.defKey}`)}>
                <TableCell className="align-top">
                  <div className="flex items-center gap-2 font-medium text-slate-900">
                    <Activity className="h-3.5 w-3.5 text-cyan-500" />
                    {d.name || "-"}
                  </div>
                  {d.description ? (
                    <div className="mt-0.5 max-w-xs truncate text-xs text-slate-400">{d.description}</div>
                  ) : null}
                </TableCell>
                <TableCell className="align-top font-mono text-xs text-slate-600">{d.defKey}</TableCell>
                <TableCell className="align-top">
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
                <TableCell className="align-top text-slate-600">
                  {d.publishedVersionNumber != null
                    ? `v${d.publishedVersionNumber}`
                    : <span className="text-slate-400">未发布</span>}
                </TableCell>
                <TableCell className="align-top text-sm text-slate-700">
                  {d.latestCreatedByName || d.latestCreatedBy || "-"}
                </TableCell>
                <TableCell className="align-top text-sm text-slate-500">{formatTime(d.updateTime)}</TableCell>
                <TableCell className="align-top text-right" onClick={(e) => e.stopPropagation()}>
                  <Button variant="ghost" size="sm" onClick={() => navigate(`/admin/workflow/${d.defKey}`)}>
                    <GitBranch className="mr-1 h-4 w-4" /> 编辑
                  </Button>
                </TableCell>
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
            void load(next.current, next.size, query);
          }}
        />
      </section>
    </div>
  );
}
