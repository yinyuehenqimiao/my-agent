import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import BpmnModeler from "bpmn-js/lib/Modeler";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn.css";
import { toast } from "sonner";
import { ArrowLeft, Check, FileCode, GitBranch, RotateCcw, Send, ThumbsDown, ThumbsUp } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { getErrorMessage } from "@/utils/error";
import {
  WF_STATUS_LABEL,
  workflowService,
  type WfDefinitionDetail,
  type WfVersion,
} from "@/services/workflowService";

/** 允许的节点类型（与后端 BpmnValidator 白名单一致） */
const ALLOWED_PALETTE = [
  { type: "bpmn:StartEvent", label: "开始" },
  { type: "bpmn:EndEvent", label: "结束" },
  { type: "bpmn:ServiceTask", label: "服务任务（校验/冻结/结算/审计）" },
  { type: "bpmn:UserTask", label: "人工任务（审批/确认）" },
  { type: "bpmn:ExclusiveGateway", label: "排他网关" },
];

const EMPTY_DIAGRAM = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://myagent.com/workflow">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" />
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
    <bpmn:endEvent id="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="160" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BPMNShape_EndEvent_1" bpmnElement="EndEvent_1">
        <dc:Bounds x="360" y="160" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="BPMNEdge_Flow_1" bpmnElement="Flow_1">
        <di:waypoint x="196" y="178" />
        <di:waypoint x="360" y="178" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

export function WorkflowDesignerPage() {
  const { defKey } = useParams();
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<BpmnModeler | null>(null);
  const [detail, setDetail] = useState<WfDefinitionDetail | null>(null);
  const [name, setName] = useState("");
  const [defKeyInput, setDefKeyInput] = useState("");
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
  const [reviewComment, setReviewComment] = useState("");
  const [busyAction, setBusyAction] = useState<string | null>(null);

  useEffect(() => {
    if (!containerRef.current) return;
    const modeler = new BpmnModeler({ container: containerRef.current });
    modelerRef.current = modeler;
    modeler.importXML(EMPTY_DIAGRAM).then(() => modeler.get("canvas").zoom("fit-viewport"));
    return () => { modeler.destroy(); modelerRef.current = null; };
  }, []);

  const showVersion = useCallback(async (version?: WfVersion) => {
    const xmlToLoad = version?.bpmnXml || EMPTY_DIAGRAM;
    if (!modelerRef.current) return;
    await modelerRef.current.importXML(xmlToLoad);
    modelerRef.current.get("canvas").zoom("fit-viewport");
  }, []);

  const loadDefinition = useCallback(async () => {
    if (!defKey) return;
    try {
      const d = await workflowService.getDefinition(defKey);
      const latest = d.versions[d.versions.length - 1];
      setDetail(d);
      setName(d.definition.name || "");
      setDefKeyInput(d.definition.defKey || defKey);
      setSelectedVersionId(latest?.id ?? null);
      await showVersion(latest);
    } catch (e) {
      toast.error(getErrorMessage(e, "加载工作流失败"));
    }
  }, [defKey, showVersion]);

  useEffect(() => {
    void loadDefinition();
  }, [loadDefinition]);

  const selectedVersion = useMemo(
    () => detail?.versions.find((version) => version.id === selectedVersionId),
    [detail, selectedVersionId],
  );

  const handleVersionChange = async (value: string) => {
    const version = detail?.versions.find((item) => item.id === Number(value));
    if (!version) return;
    setSelectedVersionId(version.id);
    try {
      await showVersion(version);
    } catch (e) {
      toast.error(getErrorMessage(e, "版本内容加载失败"));
    }
  };

  const readCanvasXml = async () => {
    if (!modelerRef.current) throw new Error("工作流设计器尚未初始化，请稍后重试");
    const result = await modelerRef.current.saveXML({ format: true });
    if (!result.xml) throw new Error("未能读取当前流程图内容");
    return result.xml;
  };

  const runAction = async (action: string, successMessage: string, task: () => Promise<unknown>) => {
    if (busyAction) return;
    setBusyAction(action);
    try {
      await task();
      toast.success(successMessage);
      await loadDefinition();
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setBusyAction(null);
    }
  };

  const handleCreate = async () => {
    if (!defKeyInput.trim()) {
      toast.error("请填写流程键");
      return;
    }
    if (busyAction) return;
    setBusyAction("create");
    try {
      const currentXml = await readCanvasXml();
      await workflowService.createDefinition({
        defKey: defKeyInput.trim(),
        name: name.trim() || defKeyInput.trim(),
        bpmnXml: currentXml,
      });
      toast.success("已创建草稿");
      navigate("/admin/workflow");
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setBusyAction(null);
    }
  };

  const handleNewVersion = async () => {
    if (!detail) return;
    if (busyAction) return;
    setBusyAction("new-version");
    try {
      const currentXml = await readCanvasXml();
      const source = selectedVersion;
      if (!source) throw new Error("当前工作流没有可作为基线的版本");
      await workflowService.newVersion({ sourceVersionId: source.id, bpmnXml: currentXml });
      toast.success("已创建新版本");
      await loadDefinition();
    } catch (e) {
      toast.error(getErrorMessage(e));
    } finally {
      setBusyAction(null);
    }
  };

  const handleSubmitReview = async (versionId: number) => {
    await runAction("submit-review", "已提交审核", () => workflowService.submitReview(versionId));
  };

  const handlePublish = async (versionId: number) => {
    await runAction("publish", "已发布", () => workflowService.publish(versionId));
  };

  const handleReview = async (versionId: number, approve: boolean) => {
    await runAction(approve ? "approve" : "reject", approve ? "审核已通过" : "已驳回并退回修改", () =>
      workflowService.review(versionId, { approve, comment: reviewComment.trim() || undefined }),
    );
    setReviewComment("");
  };

  const handleRetire = async (versionId: number) => {
    await runAction("retire", "该版本已停用", () => workflowService.retire(versionId));
  };

  const handleRollback = async (versionId: number) => {
    if (!detail) return;
    await runAction("rollback", "已切换到所选历史版本", () =>
      workflowService.rollback(detail.definition.id, versionId),
    );
  };

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/workflow")}>
          <ArrowLeft className="mr-1 h-4 w-4" /> 返回
        </Button>
        <h2 className="text-lg font-semibold">{defKey ? `编辑：${detail?.definition.name || defKey}` : "新建工作流"}</h2>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[1fr_320px]">
        <Card>
          <CardContent className="h-[560px] p-0">
            <div ref={containerRef} className="h-full w-full" />
          </CardContent>
        </Card>

        <Card>
          <CardContent className="space-y-4">
            <div>
              <p className="mb-1 text-sm font-medium">流程信息</p>
              <Input placeholder="流程标识（如 leave-approval）" value={defKeyInput} onChange={(e) => setDefKeyInput(e.target.value)} disabled={!!defKey} />
              <p className="mt-1 text-xs text-slate-400">流程标识创建后不可修改，建议使用小写字母、数字和短横线。</p>
              <Input placeholder="流程名称" value={name} onChange={(e) => setName(e.target.value)} className="mt-2" disabled={!!defKey} />
            </div>

            {detail && detail.versions.length > 0 && (
              <div>
                <p className="mb-1 text-sm font-medium">当前查看版本</p>
                <Select value={selectedVersionId ? String(selectedVersionId) : undefined} onValueChange={handleVersionChange}>
                  <SelectTrigger aria-label="当前查看版本">
                    <SelectValue placeholder="请选择版本" />
                  </SelectTrigger>
                  <SelectContent>
                    {[...detail.versions].reverse().map((version) => (
                      <SelectItem key={version.id} value={String(version.id)}>
                        v{version.versionNumber} · {WF_STATUS_LABEL[version.status]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="mt-2 flex items-center gap-2 text-xs text-slate-500">
                  <Badge variant="outline">{selectedVersion ? WF_STATUS_LABEL[selectedVersion.status] : "未选择"}</Badge>
                  {selectedVersion && <span>创建人：{selectedVersion.createdBy}</span>}
                </div>
              </div>
            )}

            <div>
              <p className="mb-1 text-sm font-medium">允许节点</p>
              <div className="flex flex-wrap gap-2">
                {ALLOWED_PALETTE.map((p) => (
                  <span key={p.type} className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-600">
                    {p.label}
                  </span>
                ))}
              </div>
              <p className="mt-1 text-xs text-slate-400">禁止脚本、Java 类、HTTP 节点；写工具需确认与幂等。</p>
            </div>

            <div className="space-y-2">
              {defKey ? (
                <>
                  <Button variant="outline" className="w-full" onClick={handleNewVersion} disabled={!selectedVersion || !!busyAction}>
                    <FileCode className="mr-2 h-4 w-4" /> 基于当前画布创建新版本
                  </Button>
                  {selectedVersion?.status === "DRAFT" && (
                    <Button className="w-full" onClick={() => handleSubmitReview(selectedVersion.id)} disabled={!!busyAction}>
                      <Send className="mr-2 h-4 w-4" /> 提交当前版本审核
                    </Button>
                  )}
                  {selectedVersion?.status === "IN_REVIEW" && (
                    <div className="space-y-2 rounded-lg border border-slate-200 p-3">
                      <p className="text-sm font-medium">审核意见</p>
                      <Textarea value={reviewComment} onChange={(event) => setReviewComment(event.target.value)} placeholder="可填写审核意见；驳回时建议说明原因" />
                      <div className="grid grid-cols-2 gap-2">
                        <Button variant="outline" onClick={() => handleReview(selectedVersion.id, false)} disabled={!!busyAction}>
                          <ThumbsDown className="mr-1 h-4 w-4" /> 驳回
                        </Button>
                        <Button onClick={() => handleReview(selectedVersion.id, true)} disabled={!!busyAction}>
                          <ThumbsUp className="mr-1 h-4 w-4" /> 审核通过
                        </Button>
                      </div>
                    </div>
                  )}
                  {selectedVersion?.status === "APPROVED" && (
                    <Button className="w-full" onClick={() => handlePublish(selectedVersion.id)} disabled={!!busyAction}>
                      <Check className="mr-2 h-4 w-4" /> 发布当前版本
                    </Button>
                  )}
                  {selectedVersion?.status === "PUBLISHED" && selectedVersion.id === detail?.definition.currentPublishedVersionId && (
                    <Button variant="outline" className="w-full" onClick={() => handleRetire(selectedVersion.id)} disabled={!!busyAction}>
                      停用当前发布版本
                    </Button>
                  )}
                  {selectedVersion?.status === "PUBLISHED" && selectedVersion.id !== detail?.definition.currentPublishedVersionId && (
                    <Button variant="outline" className="w-full" onClick={() => handleRollback(selectedVersion.id)} disabled={!!busyAction}>
                      <RotateCcw className="mr-2 h-4 w-4" /> 切换到这个历史版本
                    </Button>
                  )}
                </>
              ) : (
                <Button className="w-full" onClick={handleCreate} disabled={!!busyAction}>
                  <GitBranch className="mr-2 h-4 w-4" /> 创建草稿
                </Button>
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
