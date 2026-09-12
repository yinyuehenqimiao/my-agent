import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import BpmnModeler from "bpmn-js/lib/Modeler";
import "bpmn-js/dist/assets/diagram-js.css";
import "bpmn-js/dist/assets/bpmn-font/css/bpmn.css";
import { toast } from "sonner";
import { ArrowLeft, Check, FileCode, GitBranch, Send, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { getErrorMessage } from "@/utils/error";
import {
  workflowService,
  type WfDefinitionDetail,
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
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="http://myagent.com/workflow">
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
  const [xml, setXml] = useState(EMPTY_DIAGRAM);
  const [name, setName] = useState("");
  const [defKeyInput, setDefKeyInput] = useState("");

  useEffect(() => {
    if (!containerRef.current) return;
    const modeler = new BpmnModeler({ container: containerRef.current });
    modelerRef.current = modeler;
    modeler.importXML(EMPTY_DIAGRAM).then(() => modeler.get("canvas").zoom("fit-viewport"));
    return () => { modeler.destroy(); modelerRef.current = null; };
  }, []);

  useEffect(() => {
    if (!defKey) return;
    workflowService.getDefinition(defKey)
      .then((d) => {
        setDetail(d);
        const published = d.versions.find((v) => v.id === d.definition.currentPublishedVersionId);
        const latest = d.versions[d.versions.length - 1];
        const target = published ?? latest;
        if (target) {
          // 加载已发布或最新版本的 BPMN（此处简化：用空画布，实际应加载版本 BPMN）
          modelerRef.current?.importXML(EMPTY_DIAGRAM).then(() =>
            modelerRef.current?.get("canvas").zoom("fit-viewport")
          );
        }
      })
      .catch((e) => toast.error(getErrorMessage(e)));
  }, [defKey]);

  const handleSaveXml = async () => {
    if (!modelerRef.current) return;
    const result = await modelerRef.current.saveXML({ format: true });
    setXml(result.xml);
  };

  const handleCreate = async () => {
    if (!defKeyInput.trim()) {
      toast.error("请填写流程键");
      return;
    }
    await handleSaveXml();
    try {
      await workflowService.createDefinition({ defKey: defKeyInput, name: name || defKeyInput, bpmnXml: xml });
      toast.success("已创建草稿");
      navigate("/admin/workflow");
    } catch (e) {
      toast.error(getErrorMessage(e));
    }
  };

  const handleNewVersion = async () => {
    if (!detail) return;
    await handleSaveXml();
    try {
      await workflowService.newVersion({ sourceVersionId: detail.versions[detail.versions.length - 1].id, bpmnXml: xml });
      toast.success("已创建新版本");
    } catch (e) {
      toast.error(getErrorMessage(e));
    }
  };

  const handleSubmitReview = async (versionId: number) => {
    try {
      await workflowService.submitReview(versionId);
      toast.success("已提交审核");
    } catch (e) {
      toast.error(getErrorMessage(e));
    }
  };

  const handlePublish = async (versionId: number) => {
    try {
      await workflowService.publish(versionId);
      toast.success("已发布");
    } catch (e) {
      toast.error(getErrorMessage(e));
    }
  };

  return (
    <div className="space-y-4 p-4">
      <div className="flex items-center gap-2">
        <Button variant="ghost" size="sm" onClick={() => navigate("/admin/workflow")}>
          <ArrowLeft className="mr-1 h-4 w-4" /> 返回
        </Button>
        <h2 className="text-lg font-semibold">{defKey ? `编辑 ${defKey}` : "新建工作流"}</h2>
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
              <Input placeholder="流程键（如 leave-approval）" value={defKeyInput} onChange={(e) => setDefKeyInput(e.target.value)} disabled={!!defKey} />
              <Input placeholder="流程名称" value={name} onChange={(e) => setName(e.target.value)} className="mt-2" />
            </div>

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
                  <Button variant="outline" className="w-full" onClick={handleNewVersion}>
                    <FileCode className="mr-2 h-4 w-4" /> 保存为新版本
                  </Button>
                  {detail?.versions.map((v) => (
                    <div key={v.id} className="flex items-center justify-between rounded-md border border-slate-200 px-3 py-2 text-sm">
                      <span>
                        v{v.versionNumber} · <span className="text-slate-500">{v.status}</span>
                      </span>
                      <span className="flex gap-1">
                        {v.status === "DRAFT" && (
                          <Button variant="ghost" size="sm" onClick={() => handleSubmitReview(v.id)}>
                            <Send className="h-4 w-4" /> 提交审核
                          </Button>
                        )}
                        {v.status === "APPROVED" && (
                          <Button variant="ghost" size="sm" onClick={() => handlePublish(v.id)}>
                            <Check className="h-4 w-4" /> 发布
                          </Button>
                        )}
                      </span>
                    </div>
                  ))}
                </>
              ) : (
                <Button className="w-full" onClick={handleCreate}>
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
