-- 补内置智能体 KNOWLEDGE_TOOL_DESCRIPTION 槽位（可重复执行）

INSERT INTO t_agent_prompt (id, agent_id, slot_key, content, create_time, update_time, deleted)
SELECT
  '2001523723396309018',
  '2001523723396309001',
  'KNOWLEDGE_TOOL_DESCRIPTION',
  $prompt$检索本助手配置的知识库，返回一份已基于命中资料合成的完整答案。

适用：答案写在资料里的问题——规范、制度、流程、操作指南、政策、产品与业务说明等。判断依据是答案能否在资料中找到，而非问题属于哪个领域；用词通用但各家规定可能不同的问题同样适用。不确定是否收录时也调用一次，由检索结果说明。

不适用：某个具体对象的当前状态、某条记录、某个实时数值，改用能查到该对象的业务工具；只处理用户本轮给出的文本、或只靠通用语言能力就能完成的任务。

参数 query：完整、独立、可单独读懂的疑问句，使用用户原语言；指代先替换为明确对象，必要背景一并写入；复合问题整体传入，无需你预先拆成子问题。

返回值：面向用户的成品答案，可能含 Markdown 图片、链接与 HTML 表格；未检索到相关内容时会明确说明这一点。$prompt$,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
WHERE NOT EXISTS (
  SELECT 1 FROM t_agent_prompt
  WHERE agent_id = '2001523723396309001'
    AND slot_key = 'KNOWLEDGE_TOOL_DESCRIPTION'
    AND deleted = 0
);
