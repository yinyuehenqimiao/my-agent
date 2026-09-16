-- myagent v0.9.0: 可视化工作流编辑源
-- process_json 保存画布节点与连线；bpmn_xml 仍是发布时的不可变运行时产物。
ALTER TABLE wf_version ADD COLUMN IF NOT EXISTS process_json TEXT;
COMMENT ON COLUMN wf_version.process_json IS '可视化编排 JSON 编辑源，发布后随版本冻结';
