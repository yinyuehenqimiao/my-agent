import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ReactFlow, Background, Controls, MiniMap, addEdge, useEdgesState, useNodesState,
  type Connection, type Edge, type Node,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { toast } from "sonner";
import { ArrowLeft, CheckCircle2, Plus, Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { workflowService, type WorkflowProcess, type WfVersion } from "@/services/workflowService";
import { getErrorMessage } from "@/utils/error";

type BusinessNodeData = { label: string; workflowType: string; config: Record<string, unknown> };
const NODE_TYPES = [
  ["start", "开始"], ["collect", "收集信息"], ["tool.query", "查询工具"], ["knowledge", "知识问答"],
  ["react", "智能处理"], ["condition", "条件分支"], ["confirm", "用户确认"], ["tool.execute", "执行业务操作"],
  ["human.wait", "人工等待"], ["reply", "回复"], ["end", "结束"],
] as const;

const templates: Record<string, { nodes: Node<BusinessNodeData>[]; edges: Edge[] }> = {
  leave: linear(["start", "collect", "tool.query", "knowledge", "confirm", "tool.execute", "reply", "end"]),
  policy: linear(["start", "knowledge", "reply", "end"]),
  profile: linear(["start", "tool.query", "reply", "end"]),
};

function linear(types: string[]) {
  const nodes = types.map((type, index) => ({ id: `${type.replace(".", "_")}_${index}`, position: { x: index * 190, y: 120 }, data: { label: NODE_TYPES.find(([key]) => key === type)?.[1] ?? type, workflowType: type, config: {} } }));
  const edges = nodes.slice(1).map((node, index) => ({ id: `e_${index}`, source: nodes[index].id, target: node.id }));
  return { nodes, edges };
}

export function BusinessWorkflowDesignerPage() {
  const { defKey } = useParams(); const navigate = useNavigate();
  const [key, setKey] = useState(defKey ?? ""); const [name, setName] = useState("");
  const [nodes, setNodes, onNodesChange] = useNodesState<Node<BusinessNodeData>>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);
  const [selectedId, setSelectedId] = useState<string>(); const [version, setVersion] = useState<WfVersion>();
  const [busy, setBusy] = useState(false);
  const selected = nodes.find((node) => node.id === selectedId);

  useEffect(() => {
    if (!defKey) { const initial = templates.leave; setNodes(initial.nodes); setEdges(initial.edges); return; }
    workflowService.getDefinition(defKey).then((detail) => {
      setName(detail.definition.name); setKey(detail.definition.defKey);
      const latest = detail.versions.at(-1); setVersion(latest);
      if (latest?.sourceType === "PROCESS_JSON" && latest.processJson) loadProcess(JSON.parse(latest.processJson));
      else toast.info("该版本是旧 BPMN 流程，仅保留兼容运行；请复制为新的业务积木流程");
    }).catch((error) => toast.error(getErrorMessage(error)));
  }, [defKey]);

  const loadProcess = (process: WorkflowProcess) => {
    setNodes(process.nodes.map((item, index) => ({ id: item.id, position: item.position ?? { x: index * 180, y: 100 }, data: { label: NODE_TYPES.find(([type]) => type === item.type)?.[1] ?? item.type, workflowType: item.type, config: item.config ?? {} } })));
    setEdges(process.edges.map((item) => ({ id: item.id, source: item.source, target: item.target, label: item.outlet })));
  };
  const process = useMemo<WorkflowProcess>(() => ({ schemaVersion: 1,
    nodes: nodes.map((node) => ({ id: node.id, type: node.data.workflowType, version: 1, position: node.position, config: node.data.config })),
    edges: edges.map((edge) => ({ id: edge.id, source: edge.source, target: edge.target, outlet: typeof edge.label === "string" ? edge.label : undefined })),
    variables: [], dependencies: [],
  }), [nodes, edges]);
  const connect = useCallback((connection: Connection) => setEdges((items) => addEdge(connection, items)), []);
  const addNode = (type: string, label: string) => setNodes((items) => [...items, { id: `${type.replace(".", "_")}_${crypto.randomUUID()}`, position: { x: 160 + items.length * 30, y: 100 + items.length * 40 }, data: { label, workflowType: type, config: {} } }]);
  const useTemplate = (template: keyof typeof templates) => { setNodes(templates[template].nodes); setEdges(templates[template].edges); };

  const run = async (action: "save" | "validate") => {
    if (!key.trim()) return toast.error("请填写流程键"); setBusy(true);
    try {
      if (action === "validate") { const result = await workflowService.validateProcess(key, process); toast.success(`校验通过：${result.contentHash.slice(0, 12)}`); }
      else if (!defKey) { await workflowService.createProcessDefinition({ defKey: key, name: name || key, process }); toast.success("动态流程草稿已创建"); navigate(`/admin/workflow/${key}`); }
      else if (version) { const result = await workflowService.saveProcessVersion(version.id, version.lockVersion ?? 0, process); setVersion({ ...version, lockVersion: result.lockVersion, contentHash: result.contentHash, processJson: JSON.stringify(process) }); toast.success("草稿已保存"); }
    } catch (error) { toast.error(getErrorMessage(error)); } finally { setBusy(false); }
  };

  return <div className="flex h-[calc(100vh-64px)] flex-col gap-3 p-4">
    <div className="flex items-center gap-2"><Button variant="outline" onClick={() => navigate("/admin/workflow")}><ArrowLeft className="mr-1 h-4 w-4"/>返回</Button><Input className="w-52" placeholder="流程键" value={key} disabled={!!defKey} onChange={(e) => setKey(e.target.value)}/><Input className="w-64" placeholder="流程名称" value={name} onChange={(e) => setName(e.target.value)}/><Button disabled={busy} onClick={() => run("save")}><Save className="mr-1 h-4 w-4"/>保存草稿</Button><Button variant="outline" disabled={busy} onClick={() => run("validate")}><CheckCircle2 className="mr-1 h-4 w-4"/>校验</Button></div>
    <div className="grid min-h-0 flex-1 grid-cols-[220px_1fr_300px] gap-3">
      <aside className="overflow-auto rounded-lg border bg-card p-3"><h3 className="mb-2 font-semibold">业务积木</h3>{NODE_TYPES.map(([type,label]) => <Button key={type} variant="ghost" className="mb-1 w-full justify-start" onClick={() => addNode(type,label)}><Plus className="mr-2 h-4 w-4"/>{label}</Button>)}<h3 className="mb-2 mt-4 font-semibold">模板</h3><Button variant="outline" className="mb-2 w-full" onClick={() => useTemplate("leave")}>请假办理</Button><Button variant="outline" className="mb-2 w-full" onClick={() => useTemplate("policy")}>制度查询</Button><Button variant="outline" className="w-full" onClick={() => useTemplate("profile")}>个人信息查询</Button></aside>
      <main className="min-h-0 rounded-lg border bg-background"><ReactFlow nodes={nodes} edges={edges} onNodesChange={onNodesChange} onEdgesChange={onEdgesChange} onConnect={connect} onNodeClick={(_,node) => setSelectedId(node.id)} fitView><MiniMap/><Controls/><Background/></ReactFlow></main>
      <aside className="overflow-auto rounded-lg border bg-card p-3"><h3 className="mb-3 font-semibold">节点配置</h3>{selected ? <><div className="mb-2 text-sm">{selected.data.label}<br/><span className="text-muted-foreground">{selected.id}</span></div><Textarea rows={18} value={JSON.stringify(selected.data.config,null,2)} onChange={(event) => { try { const config = JSON.parse(event.target.value); setNodes((items) => items.map((item) => item.id === selected.id ? { ...item, data: { ...item.data, config } } : item)); } catch { /* 输入过程中允许暂时非完整 JSON */ } }}/></> : <p className="text-sm text-muted-foreground">选择节点后配置业务参数。</p>}</aside>
    </div>
  </div>;
}
