package com.myagent.workflow.service;

import com.myagent.framework.exception.ClientException;
import com.myagent.workflow.model.WorkflowProcess;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 将受限业务积木编译为 Flowable BPMN，不接受前端提交的 XML。 */
@Service
public class WorkflowProcessCompiler {

    private static final Set<String> TYPES = Set.of("start", "collect", "tool.query", "knowledge", "react",
            "condition", "confirm", "tool.execute", "human.wait", "reply", "end");
    private static final Set<String> OPERATORS = Set.of("eq", "ne", "gt", "ge", "lt", "le", "in", "exists");

    public CompiledProcess compile(String defKey, WorkflowProcess process) {
        validate(process);
        String processId = "wf_" + escapeId(defKey);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
                .append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:flowable=\"http://flowable.org/bpmn\">")
                .append("<process id=\"").append(processId).append("\" isExecutable=\"true\">");
        for (WorkflowProcess.Node node : process.nodes()) {
            String id = escapeId(node.id());
            switch (node.type()) {
                case "start" -> xml.append("<startEvent id=\"").append(id).append("\"/>");
                case "end" -> xml.append("<endEvent id=\"").append(id).append("\"/>");
                case "condition" -> xml.append("<exclusiveGateway id=\"").append(id).append("\"/>");
                case "collect", "confirm", "human.wait" -> appendUserTask(xml, id, node.type(), node.version());
                case "tool.query", "knowledge", "react", "tool.execute", "reply" -> appendServiceTask(xml, id, node.type(), node.version());
                default -> throw new ClientException("不支持的节点类型：" + node.type());
            }
        }
        for (WorkflowProcess.Edge edge : process.edges()) {
            xml.append("<sequenceFlow id=\"").append(escapeId(edge.id())).append("\" sourceRef=\"")
                    .append(escapeId(edge.source())).append("\" targetRef=\"").append(escapeId(edge.target())).append("\"");
            if (edge.condition() == null) xml.append("/>");
            else xml.append("><conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[")
                    .append(toExpression(edge.condition())).append("]]></conditionExpression></sequenceFlow>");
        }
        xml.append("</process></definitions>");
        String bpmn = xml.toString();
        return new CompiledProcess(bpmn, sha256(normalize(process)));
    }

    private static void appendServiceTask(StringBuilder xml, String id, String type, Integer version) {
        xml.append("<serviceTask id=\"").append(id)
                .append("\" flowable:delegateExpression=\"${workflowTaskDelegate}\" flowable:type=\"template\">")
                .append("<extensionElements>")
                .append("<flowable:field name=\"nodeType\"><flowable:string>").append(escapeXml(type)).append("</flowable:string></flowable:field>")
                .append("<flowable:field name=\"nodeVersion\"><flowable:string>").append(version == null ? 1 : version).append("</flowable:string></flowable:field>")
                .append("<flowable:field name=\"nodeInstanceId\"><flowable:string>").append(escapeXml(id)).append("</flowable:string></flowable:field>")
                .append("</extensionElements></serviceTask>");
    }

    private static void appendUserTask(StringBuilder xml, String id, String type, Integer version) {
        xml.append("<userTask id=\"").append(id).append("\">")
                .append("<extensionElements>")
                .append("<flowable:field name=\"nodeType\"><flowable:string>").append(escapeXml(type)).append("</flowable:string></flowable:field>")
                .append("<flowable:field name=\"nodeVersion\"><flowable:string>").append(version == null ? 1 : version).append("</flowable:string></flowable:field>")
                .append("<flowable:field name=\"nodeInstanceId\"><flowable:string>").append(escapeXml(id)).append("</flowable:string></flowable:field>")
                .append("</extensionElements></userTask>");
    }

    public void validate(WorkflowProcess process) {
        if (process == null || process.nodes() == null || process.edges() == null) throw new ClientException("流程图不能为空");
        Map<String, WorkflowProcess.Node> nodes = new HashMap<>();
        for (WorkflowProcess.Node node : process.nodes()) {
            if (node == null || blank(node.id()) || !TYPES.contains(node.type())) throw new ClientException("存在未知或无效节点");
            if (nodes.put(node.id(), node) != null) throw new ClientException("节点 ID 重复：" + node.id());
        }
        List<WorkflowProcess.Node> starts = nodes.values().stream().filter(n -> "start".equals(n.type())).toList();
        List<WorkflowProcess.Node> ends = nodes.values().stream().filter(n -> "end".equals(n.type())).toList();
        if (starts.size() != 1 || ends.isEmpty()) throw new ClientException("流程必须有一个开始节点和至少一个结束节点");
        Map<String, List<WorkflowProcess.Edge>> outgoing = process.edges().stream().collect(Collectors.groupingBy(WorkflowProcess.Edge::source));
        Set<String> edgeIds = new HashSet<>();
        for (WorkflowProcess.Edge edge : process.edges()) {
            if (edge == null || blank(edge.id()) || !edgeIds.add(edge.id()) || !nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target()))
                throw new ClientException("存在悬空或重复连线");
            if (edge.condition() != null) validateCondition(edge.condition());
        }
        for (WorkflowProcess.Node node : nodes.values()) {
            if ("condition".equals(node.type())) {
                List<WorkflowProcess.Edge> exits = outgoing.getOrDefault(node.id(), List.of());
                if (exits.stream().noneMatch(edge -> edge.condition() == null)) throw new ClientException("条件节点缺少默认出口：" + node.id());
            }
        }
        rejectCyclesAndUnreachable(starts.get(0).id(), nodes, outgoing);
        requireConfirmationOnEveryWritePath(starts.get(0).id(), nodes, outgoing, false);
    }

    private static void validateCondition(WorkflowProcess.Condition condition) {
        if (blank(condition.field()) || !condition.field().matches("[A-Za-z][A-Za-z0-9_]{0,63}") || !OPERATORS.contains(condition.operator()))
            throw new ClientException("条件配置不合法");
    }

    private static void rejectCyclesAndUnreachable(String start, Map<String, WorkflowProcess.Node> nodes,
                                                    Map<String, List<WorkflowProcess.Edge>> outgoing) {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        visit(start, outgoing, visited, visiting);
        if (visited.size() != nodes.size()) throw new ClientException("存在不可达节点");
    }

    private static void visit(String id, Map<String, List<WorkflowProcess.Edge>> outgoing, Set<String> visited, Set<String> visiting) {
        if (!visiting.add(id)) throw new ClientException("首版不支持流程循环");
        for (WorkflowProcess.Edge edge : outgoing.getOrDefault(id, List.of())) if (!visited.contains(edge.target())) visit(edge.target(), outgoing, visited, visiting);
        visiting.remove(id);
        visited.add(id);
    }

    private static void requireConfirmationOnEveryWritePath(String id, Map<String, WorkflowProcess.Node> nodes,
                                                            Map<String, List<WorkflowProcess.Edge>> outgoing, boolean confirmed) {
        WorkflowProcess.Node node = nodes.get(id);
        boolean nextConfirmed = confirmed || "confirm".equals(node.type());
        if ("tool.execute".equals(node.type()) && !nextConfirmed) throw new ClientException("写操作路径缺少用户确认：" + id);
        for (WorkflowProcess.Edge edge : outgoing.getOrDefault(id, List.of())) requireConfirmationOnEveryWritePath(edge.target(), nodes, outgoing, nextConfirmed);
    }

    private static String toExpression(WorkflowProcess.Condition condition) {
        String field = condition.field();
        String value = condition.value() == null ? "" : condition.value().replace("'", "");
        if ("exists".equals(condition.operator())) return "${" + field + " != null}";
        if ("in".equals(condition.operator())) return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).map(v -> field + " == '" + v + "'").collect(Collectors.joining(" || ", "${", "}"));
        return "${" + field + " " + mapOperator(condition.operator()) + " '" + value + "'}";
    }
    private static String mapOperator(String op) { return Map.of("eq", "==", "ne", "!=", "gt", ">", "ge", ">=", "lt", "<", "le", "<=").get(op); }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String escapeId(String value) { return value.replaceAll("[^A-Za-z0-9_]", "_"); }
    private static String escapeXml(String value) { return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static String normalize(WorkflowProcess process) { return "v=" + process.schemaVersion() + "#" + process.nodes().stream().map(n -> n.id() + ":" + n.type() + ":" + n.version() + ":" + canonical(n.config())).sorted().collect(Collectors.joining("|")) + "#" + process.edges().stream().map(e -> e.id() + ":" + e.source() + ":" + e.target() + ":" + e.outlet() + ":" + e.condition()).sorted().collect(Collectors.joining("|")) + "#vars=" + canonical(process.variables()) + "#deps=" + canonical(process.dependencies()); }
    private static String canonical(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) return map.entrySet().stream().sorted(java.util.Comparator.comparing(entry -> String.valueOf(entry.getKey()))).map(entry -> entry.getKey() + "=" + canonical(entry.getValue())).collect(Collectors.joining(",", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(WorkflowProcessCompiler::canonical).collect(Collectors.joining(",", "[", "]"));
        return String.valueOf(value);
    }
    private static String sha256(String text) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }

    public record CompiledProcess(String bpmnXml, String contentHash) { }
}
